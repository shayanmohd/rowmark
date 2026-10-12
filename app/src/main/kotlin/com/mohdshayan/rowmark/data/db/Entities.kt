package com.mohdshayan.rowmark.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects", indices = [Index(value = ["uuid"], unique = true)])
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val craft: String = "knitting",
    val status: String = "active",
    val needleOrHook: String = "",
    val yarnText: String = "",
    val notes: String = "",
    val targetRows: Int? = null,
    val totalRows: Int = 0,
    val coverFile: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastCountAt: Long? = null,
)

@Entity(
    tableName = "counters",
    foreignKeys = [ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("parentId")],
)
data class Counter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val kind: String,
    val value: Int = 0,
    val length: Int? = null,
    val parentId: Long? = null,
    val resetsWithRow: Boolean = false,
    val alertEvery: Int? = null,
    val alertLabel: String = "",
    val position: Int = 0,
)

@Entity(
    tableName = "count_events",
    foreignKeys = [ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("counterId"), Index("at"), Index("groupId")],
)
data class CountEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val counterId: Long,
    val groupId: Long,
    val delta: Int,
    val valueAfter: Int,
    val source: String,
    val at: Long,
    /** How far Total rows moved for the whole action; carried on the group's first event only. */
    val totalDelta: Int = 0,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index("startedAt")],
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val activeMs: Long = 0,
    val rows: Int = 0,
)

@Entity(
    tableName = "schedules",
    foreignKeys = [ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    /** Null means Total rows; otherwise the counter whose value the schedule follows. */
    val counterId: Long? = null,
    val action: String = "increase",
    val label: String = "",
    val startRow: Int = 1,
    val every: Int = 1,
    val times: Int = 1,
)

@Entity(
    tableName = "patterns",
    foreignKeys = [ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId"), Index(value = ["uuid"], unique = true)],
)
data class Pattern(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val projectId: Long,
    val kind: String,
    val file: String,
    val title: String,
    val pageCount: Int = 1,
    val readsUpward: Boolean = true,
    val rulerPage: Int = 0,
    val rulerTop: Float = 0.9f,
    val rulerHeight: Float = 0.05f,
)

@Entity(
    tableName = "markup",
    foreignKeys = [ForeignKey(entity = Pattern::class, parentColumns = ["id"], childColumns = ["patternId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("patternId")],
)
data class Markup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patternId: Long,
    val page: Int,
    val tool: String = "highlighter",
    /** JSON array of page-fraction pairs: [[x,y],[x,y],...]. */
    val points: String,
)

@Entity(tableName = "yarns", indices = [Index(value = ["uuid"], unique = true)])
data class Yarn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val brand: String = "",
    val name: String,
    val colourway: String = "",
    val weight: String = "",
    val fibre: String = "",
    val metresPerSkein: Int? = null,
    val skeins: Float = 1f,
    val dyeLot: String = "",
    val photoFile: String? = null,
)

@Entity(tableName = "tools", indices = [Index(value = ["uuid"], unique = true)])
data class Tool(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val type: String = "needle",
    val sizeMm: Float,
    val count: Int = 1,
)

@Entity(
    tableName = "project_yarns",
    primaryKeys = ["projectId", "yarnId"],
    foreignKeys = [
        ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Yarn::class, parentColumns = ["id"], childColumns = ["yarnId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("yarnId")],
)
data class ProjectYarn(val projectId: Long, val yarnId: Long, val skeinsUsed: Float = 0f)
