package com.mohdshayan.rowmark.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY COALESCE(lastCountAt, updatedAt) DESC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: Long): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: Long): Project?

    @Query("SELECT * FROM projects")
    suspend fun all(): List<Project>

    @Query("SELECT * FROM projects WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): Project?

    @Insert suspend fun insert(project: Project): Long
    @Update suspend fun update(project: Project)
    @Query("DELETE FROM projects WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM projects") suspend fun deleteAll()
    @Query("UPDATE projects SET totalRows = :total, lastCountAt = :at, updatedAt = :at WHERE id = :id")
    suspend fun setTotal(id: Long, total: Int, at: Long)
}

@Dao
interface CounterDao {
    @Query("SELECT * FROM counters WHERE projectId = :projectId ORDER BY position, id")
    fun observeForProject(projectId: Long): Flow<List<Counter>>

    @Query("SELECT * FROM counters WHERE projectId = :projectId ORDER BY position, id")
    suspend fun forProject(projectId: Long): List<Counter>

    @Query("SELECT * FROM counters ORDER BY projectId, position, id")
    fun observeAll(): Flow<List<Counter>>

    @Query("SELECT * FROM counters")
    suspend fun all(): List<Counter>

    @Query("SELECT * FROM counters WHERE id = :id")
    suspend fun get(id: Long): Counter?

    @Insert suspend fun insert(counter: Counter): Long
    @Update suspend fun update(counter: Counter)
    @Query("UPDATE counters SET value = :value WHERE id = :id") suspend fun setValue(id: Long, value: Int)
    @Query("DELETE FROM counters WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM counters WHERE projectId = :projectId AND id NOT IN (:keep)") suspend fun deleteOthers(projectId: Long, keep: List<Long>)
}

@Dao
interface CountEventDao {
    @Insert suspend fun insertAll(events: List<CountEvent>)

    @Query("SELECT * FROM count_events WHERE projectId = :projectId AND groupId = (SELECT MAX(groupId) FROM count_events WHERE projectId = :projectId) ORDER BY id")
    suspend fun lastGroup(projectId: Long): List<CountEvent>

    @Query("SELECT COALESCE(MAX(groupId), 0) FROM count_events")
    suspend fun maxGroup(): Long

    @Query("DELETE FROM count_events WHERE projectId = :projectId AND groupId = :groupId")
    suspend fun deleteGroup(projectId: Long, groupId: Long)

    @Query("SELECT COUNT(*) FROM count_events WHERE projectId = :projectId")
    fun observeCount(projectId: Long): Flow<Int>

    @Query("SELECT * FROM count_events ORDER BY at")
    suspend fun all(): List<CountEvent>
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY startedAt DESC")
    fun observeForProject(projectId: Long): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE projectId = :projectId AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun open(projectId: Long): Session?

    @Query("SELECT * FROM sessions WHERE endedAt IS NULL")
    suspend fun allOpen(): List<Session>

    @Query("SELECT * FROM sessions ORDER BY startedAt")
    suspend fun all(): List<Session>

    @Query("SELECT COALESCE(MAX(rows), 0) FROM sessions")
    fun observeMaxRows(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sessions WHERE rows >= :minRows AND endedAt IS NOT NULL")
    fun observeClosedWithAtLeast(minRows: Int): Flow<Int>

    @Insert suspend fun insert(session: Session): Long
    @Update suspend fun update(session: Session)
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE projectId = :projectId ORDER BY startRow, id")
    fun observeForProject(projectId: Long): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE projectId = :projectId ORDER BY startRow, id")
    suspend fun forProject(projectId: Long): List<Schedule>

    @Query("SELECT * FROM schedules")
    suspend fun all(): List<Schedule>

    @Insert suspend fun insert(schedule: Schedule): Long
    @Update suspend fun update(schedule: Schedule)
    @Query("DELETE FROM schedules WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface PatternDao {
    @Query("SELECT * FROM patterns WHERE projectId = :projectId ORDER BY id")
    fun observeForProject(projectId: Long): Flow<List<Pattern>>

    @Query("SELECT COUNT(*) FROM patterns WHERE projectId = :projectId")
    fun observeCount(projectId: Long): Flow<Int>

    @Query("SELECT * FROM patterns WHERE id = :id")
    suspend fun get(id: Long): Pattern?

    @Query("SELECT * FROM patterns")
    suspend fun all(): List<Pattern>

    @Insert suspend fun insert(pattern: Pattern): Long
    @Update suspend fun update(pattern: Pattern)
    @Query("DELETE FROM patterns WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE patterns SET rulerPage = :page, rulerTop = :top, rulerHeight = :height WHERE id = :id")
    suspend fun setRuler(id: Long, page: Int, top: Float, height: Float)
    @Query("UPDATE patterns SET readsUpward = :upward WHERE id = :id")
    suspend fun setDirection(id: Long, upward: Boolean)
}

@Dao
interface MarkupDao {
    @Query("SELECT * FROM markup WHERE patternId = :patternId ORDER BY id")
    fun observeForPattern(patternId: Long): Flow<List<Markup>>

    @Query("SELECT * FROM markup")
    suspend fun all(): List<Markup>

    @Query("SELECT * FROM markup WHERE patternId = :patternId ORDER BY id DESC LIMIT 1")
    suspend fun last(patternId: Long): Markup?

    @Insert suspend fun insert(markup: Markup): Long
    @Delete suspend fun delete(markup: Markup)
}

@Dao
interface YarnDao {
    @Query("SELECT * FROM yarns ORDER BY brand, name") fun observeAll(): Flow<List<Yarn>>
    @Query("SELECT * FROM yarns") suspend fun all(): List<Yarn>
    @Query("SELECT COUNT(*) FROM yarns") fun observeCount(): Flow<Int>
    @Query("SELECT * FROM yarns WHERE uuid = :uuid") suspend fun byUuid(uuid: String): Yarn?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(yarn: Yarn): Long
    @Query("DELETE FROM yarns WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM yarns") suspend fun deleteAll()
}

@Dao
interface ToolDao {
    @Query("SELECT * FROM tools ORDER BY type, sizeMm") fun observeAll(): Flow<List<Tool>>
    @Query("SELECT * FROM tools") suspend fun all(): List<Tool>
    @Query("SELECT COUNT(*) FROM tools") fun observeCount(): Flow<Int>
    @Query("SELECT * FROM tools WHERE uuid = :uuid") suspend fun byUuid(uuid: String): Tool?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(tool: Tool): Long
    @Query("DELETE FROM tools WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM tools") suspend fun deleteAll()
}

@Dao
interface ProjectYarnDao {
    @Query("SELECT * FROM project_yarns") fun observeAll(): Flow<List<ProjectYarn>>
    @Query("SELECT * FROM project_yarns") suspend fun all(): List<ProjectYarn>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(link: ProjectYarn)
    @Query("DELETE FROM project_yarns WHERE projectId = :projectId AND yarnId = :yarnId") suspend fun delete(projectId: Long, yarnId: Long)
    @Query("DELETE FROM project_yarns WHERE yarnId = :yarnId") suspend fun deleteForYarn(yarnId: Long)
}
