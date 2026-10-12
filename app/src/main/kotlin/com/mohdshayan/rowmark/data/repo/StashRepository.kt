package com.mohdshayan.rowmark.data.repo

import com.mohdshayan.rowmark.data.db.AppDatabase
import com.mohdshayan.rowmark.data.db.ProjectYarn
import com.mohdshayan.rowmark.data.db.Tool
import com.mohdshayan.rowmark.data.db.Yarn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

class StashRepository(private val db: AppDatabase) {
    fun observeYarns(): Flow<List<Yarn>> = db.yarnDao().observeAll()
    fun observeTools(): Flow<List<Tool>> = db.toolDao().observeAll()
    fun observeLinks(): Flow<List<ProjectYarn>> = db.projectYarnDao().observeAll()
    fun observeItemCount(): Flow<Int> = combine(db.yarnDao().observeCount(), db.toolDao().observeCount()) { a, b -> a + b }

    suspend fun saveYarn(yarn: Yarn): Long = db.yarnDao().upsert(yarn.copy(uuid = yarn.uuid.ifBlank { UUID.randomUUID().toString() }))
    suspend fun deleteYarn(id: Long) = db.yarnDao().delete(id)
    suspend fun saveTool(tool: Tool): Long = db.toolDao().upsert(tool.copy(uuid = tool.uuid.ifBlank { UUID.randomUUID().toString() }))
    suspend fun deleteTool(id: Long) = db.toolDao().delete(id)

    suspend fun setLinks(yarnId: Long, projectIds: Set<Long>) {
        db.projectYarnDao().deleteForYarn(yarnId)
        for (p in projectIds) db.projectYarnDao().upsert(ProjectYarn(p, yarnId))
    }
}
