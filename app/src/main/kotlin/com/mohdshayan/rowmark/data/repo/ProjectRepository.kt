package com.mohdshayan.rowmark.data.repo

import androidx.room.withTransaction
import com.mohdshayan.rowmark.core.Change
import com.mohdshayan.rowmark.core.CountResult
import com.mohdshayan.rowmark.core.CounterEngine
import com.mohdshayan.rowmark.core.CounterKind
import com.mohdshayan.rowmark.core.CounterSnapshot
import com.mohdshayan.rowmark.core.ScheduleEngine
import com.mohdshayan.rowmark.core.ScheduleSpec
import com.mohdshayan.rowmark.core.SessionClock
import com.mohdshayan.rowmark.core.SessionState
import com.mohdshayan.rowmark.data.db.AppDatabase
import com.mohdshayan.rowmark.data.db.CountEvent
import com.mohdshayan.rowmark.data.db.Counter
import com.mohdshayan.rowmark.data.db.Project
import com.mohdshayan.rowmark.data.db.Schedule
import com.mohdshayan.rowmark.data.db.Session
import com.mohdshayan.rowmark.data.prefs.AppPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import java.util.UUID

/** Where a count came from, for the journal. */
enum class CountSource { TAP, KEY, REMOTE, WIDGET, TILE }

/** A shaping step that came due on this count. */
data class ScheduleDue(val schedule: Schedule, val step: Int)

/** One action's outcome for the feedback layer and the screen. */
data class CountOutcome(
    val projectId: Long,
    val result: CountResult,
    val dueSchedules: List<ScheduleDue> = emptyList(),
    val undone: Boolean = false,
)

fun Counter.kindEnum(): CounterKind = runCatching { CounterKind.valueOf(kind) }.getOrDefault(CounterKind.CUSTOM)

fun Counter.snapshot() = CounterSnapshot(id, kindEnum(), value, length, parentId, resetsWithRow, alertEvery, alertLabel)

fun Schedule.spec() = ScheduleSpec(id, action, label, startRow, every, times)

/**
 * Every count, minus and undo goes through here, whatever started it (pad, key, remote, widget, tile),
 * so the journal, the session clock, Total rows and the widget all move together.
 */
class ProjectRepository(
    private val db: AppDatabase,
    private val prefs: AppPrefs,
    private val onChanged: (projectId: Long) -> Unit,
) {
    private val _outcomes = MutableSharedFlow<CountOutcome>(extraBufferCapacity = 8)
    val outcomes: SharedFlow<CountOutcome> = _outcomes.asSharedFlow()

    fun observeProjects(): Flow<List<Project>> = db.projectDao().observeAll()
    fun observeProject(id: Long): Flow<Project?> = db.projectDao().observe(id)
    fun observeCounters(projectId: Long): Flow<List<Counter>> = db.counterDao().observeForProject(projectId)
    fun observeAllCounters(): Flow<List<Counter>> = db.counterDao().observeAll()
    fun observeSchedules(projectId: Long): Flow<List<Schedule>> = db.scheduleDao().observeForProject(projectId)
    fun observeSessions(projectId: Long): Flow<List<Session>> = db.sessionDao().observeForProject(projectId)
    fun observeEventCount(projectId: Long): Flow<Int> = db.countEventDao().observeCount(projectId)

    suspend fun project(id: Long): Project? = db.projectDao().get(id)
    suspend fun counters(projectId: Long): List<Counter> = db.counterDao().forProject(projectId)

    /** Creates or updates a project and its counters in one transaction. Returns the project id. */
    suspend fun saveProject(project: Project, counters: List<Counter>): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val id = if (project.id == 0L) {
            db.projectDao().insert(project.copy(uuid = project.uuid.ifBlank { UUID.randomUUID().toString() }, createdAt = now, updatedAt = now))
        } else {
            db.projectDao().update(project.copy(updatedAt = now))
            project.id
        }
        // Temporary negative ids in the editor map to real ids once inserted, parents included.
        val idMap = HashMap<Long, Long>()
        val kept = mutableListOf<Long>()
        for ((index, c) in counters.withIndex()) {
            val base = c.copy(projectId = id, position = index)
            if (c.id > 0) {
                db.counterDao().update(base.copy(parentId = null))
                idMap[c.id] = c.id
                kept += c.id
            } else {
                val newId = db.counterDao().insert(base.copy(id = 0, parentId = null))
                idMap[c.id] = newId
                kept += newId
            }
        }
        for (c in counters) {
            val realId = idMap.getValue(c.id)
            val parent = c.parentId?.let { idMap[it] }
            val stored = db.counterDao().get(realId) ?: continue
            db.counterDao().update(stored.copy(parentId = parent))
        }
        db.counterDao().deleteOthers(id, kept.ifEmpty { listOf(-1L) })
        if (prefs.current().lastProjectId == 0L) prefs.setLastProjectId(id)
        id
    }.also { onChanged(it) }

    suspend fun deleteProject(id: Long) {
        db.projectDao().delete(id)
        if (prefs.current().lastProjectId == id) prefs.setLastProjectId(0L)
        onChanged(id)
    }

    suspend fun count(projectId: Long, counterId: Long, source: CountSource): CountOutcome? =
        act(projectId, source) { snaps -> CounterEngine.count(snaps, counterId) }

    suspend fun minus(projectId: Long, counterId: Long, source: CountSource): CountOutcome? =
        act(projectId, source) { snaps -> CounterEngine.minus(snaps, counterId) }

    /** Counts the project's Row counter, or its first counter: the widget, tile and keys use this. */
    suspend fun countRow(projectId: Long, source: CountSource): CountOutcome? {
        val row = rowCounter(projectId) ?: return null
        return count(projectId, row.id, source)
    }

    suspend fun minusRow(projectId: Long, source: CountSource): CountOutcome? {
        val row = rowCounter(projectId) ?: return null
        return minus(projectId, row.id, source)
    }

    suspend fun rowCounter(projectId: Long): Counter? {
        val counters = db.counterDao().forProject(projectId)
        return counters.firstOrNull { it.kind == CounterKind.ROW.name } ?: counters.firstOrNull()
    }

    private suspend fun act(projectId: Long, source: CountSource, action: (List<CounterSnapshot>) -> CountResult): CountOutcome? {
        val outcome = db.withTransaction {
            val project = db.projectDao().get(projectId) ?: return@withTransaction null
            val counters = db.counterDao().forProject(projectId)
            val result = action(counters.map { it.snapshot() })
            if (result.isEmpty) return@withTransaction CountOutcome(projectId, result)
            val now = System.currentTimeMillis()
            val group = db.countEventDao().maxGroup() + 1
            persist(project, counters, result.changes, result.totalDelta, group, source, now)
            if (result.totalDelta > 0) touchSession(projectId, now, countsAsRow = true)
            else if (result.changes.isNotEmpty()) touchSession(projectId, now, countsAsRow = false)
            val after = CounterEngine.apply(counters.map { it.snapshot() }, result.changes)
            val total = project.totalRows + result.totalDelta
            CountOutcome(projectId, result, dueSchedules(projectId, after, total, result))
        } ?: return null
        prefs.setLastProjectId(projectId)
        onChanged(projectId)
        _outcomes.tryEmit(outcome)
        return outcome
    }

    /** Reverses the last action as one step, whatever it carried into parents or reset. */
    suspend fun undo(projectId: Long): CountOutcome? {
        val outcome = db.withTransaction {
            val project = db.projectDao().get(projectId) ?: return@withTransaction null
            val events = db.countEventDao().lastGroup(projectId)
            if (events.isEmpty()) return@withTransaction null
            val counters = db.counterDao().forProject(projectId)
            val group = events.map { Change(it.counterId, it.delta, it.valueAfter) }
            val inverse = CounterEngine.undo(group)
            val totalDelta = -events.sumOf { it.totalDelta }
            db.countEventDao().deleteGroup(projectId, events.first().groupId)
            for (c in inverse) db.counterDao().setValue(c.counterId, c.valueAfter)
            val total = (project.totalRows + totalDelta).coerceAtLeast(0)
            db.projectDao().setTotal(projectId, total, System.currentTimeMillis())
            CountOutcome(projectId, CountResult(inverse, totalDelta = totalDelta), undone = true)
        } ?: return null
        onChanged(projectId)
        _outcomes.tryEmit(outcome)
        return outcome
    }

    private suspend fun persist(project: Project, counters: List<Counter>, changes: List<Change>, totalDelta: Int, group: Long, source: CountSource, now: Long) {
        for (c in changes) db.counterDao().setValue(c.counterId, c.valueAfter)
        db.countEventDao().insertAll(changes.mapIndexed { i, c ->
            CountEvent(0, project.id, c.counterId, group, c.delta, c.valueAfter, source.name, now, if (i == 0) totalDelta else 0)
        })
        db.projectDao().setTotal(project.id, (project.totalRows + totalDelta).coerceAtLeast(0), now)
    }

    private suspend fun touchSession(projectId: Long, now: Long, countsAsRow: Boolean) {
        val open = db.sessionDao().open(projectId)
        val state = open?.let { SessionState(it.startedAt, it.startedAt + it.activeMs, it.activeMs, it.rows, it.endedAt) }
        // lastAt is recovered as startedAt + activeMs: the clock only stores active time, never gaps.
        val lastAt = open?.let { lastCountAt[projectId] ?: (it.startedAt + it.activeMs) }
        val adjusted = state?.copy(lastAt = lastAt ?: state.lastAt)
        val t = SessionClock.onCount(adjusted, now, countsAsRow)
        if (t.closed != null && open != null) {
            db.sessionDao().update(open.copy(endedAt = t.closed.endedAt, activeMs = t.closed.activeMs, rows = t.closed.rows))
            prefs.bumpSessionsCompleted()
            db.sessionDao().insert(Session(0, projectId, t.current.startedAt, null, t.current.activeMs, t.current.rows))
        } else if (open == null) {
            db.sessionDao().insert(Session(0, projectId, t.current.startedAt, null, t.current.activeMs, t.current.rows))
        } else {
            db.sessionDao().update(open.copy(activeMs = t.current.activeMs, rows = t.current.rows))
        }
        lastCountAt[projectId] = now
    }

    private val lastCountAt = HashMap<Long, Long>()

    /** Closes a session that sat idle, so Stats never shows a session as still running the next day. */
    suspend fun closeIdleSessions() {
        val now = System.currentTimeMillis()
        for (s in db.sessionDao().allOpen()) {
            val last = lastCountAt[s.projectId] ?: (s.startedAt + s.activeMs)
            if (now - last > SessionClock.IDLE_MS) {
                db.sessionDao().update(s.copy(endedAt = last))
                prefs.bumpSessionsCompleted()
            }
        }
    }

    suspend fun endSessionNow(projectId: Long): Boolean {
        val open = db.sessionDao().open(projectId) ?: return false
        val last = lastCountAt[projectId] ?: (open.startedAt + open.activeMs)
        db.sessionDao().update(open.copy(endedAt = last))
        prefs.bumpSessionsCompleted()
        lastCountAt.remove(projectId)
        return true
    }

    private suspend fun dueSchedules(projectId: Long, after: List<CounterSnapshot>, totalRows: Int, result: CountResult): List<ScheduleDue> {
        if (result.totalDelta <= 0) return emptyList()
        val schedules = db.scheduleDao().forProject(projectId)
        if (schedules.isEmpty()) return emptyList()
        return schedules.mapNotNull { s ->
            val row = s.counterId?.let { id -> after.firstOrNull { it.id == id }?.value } ?: totalRows
            ScheduleEngine.stepAt(s.spec(), row)?.let { ScheduleDue(s, it) }
        }
    }

    suspend fun saveSchedule(schedule: Schedule): Long {
        val id = if (schedule.id == 0L) db.scheduleDao().insert(schedule) else { db.scheduleDao().update(schedule); schedule.id }
        onChanged(schedule.projectId)
        return id
    }

    suspend fun deleteSchedule(schedule: Schedule) {
        db.scheduleDao().delete(schedule.id)
        onChanged(schedule.projectId)
    }

    suspend fun lastProject(): Project? {
        val id = prefs.current().lastProjectId
        return if (id > 0) db.projectDao().get(id) else db.projectDao().observeAll().first().firstOrNull()
    }
}
