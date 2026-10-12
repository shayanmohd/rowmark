package com.mohdshayan.rowmark.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class ProjectDto(
    val uuid: String, val name: String, val craft: String, val status: String, val needleOrHook: String,
    val yarnText: String, val notes: String, val targetRows: Int?, val totalRows: Int, val coverFile: String?,
    val createdAt: Long, val updatedAt: Long, val lastCountAt: Long?,
)
@Serializable data class CounterDto(
    val key: Long, val projectUuid: String, val name: String, val kind: String, val value: Int, val length: Int?,
    val parentKey: Long?, val resetsWithRow: Boolean, val alertEvery: Int?, val alertLabel: String, val position: Int,
)
@Serializable data class EventDto(
    val projectUuid: String, val counterKey: Long, val groupId: Long, val delta: Int, val valueAfter: Int, val source: String, val at: Long, val totalDelta: Int = 0,
)
@Serializable data class SessionDto(val projectUuid: String, val startedAt: Long, val endedAt: Long?, val activeMs: Long, val rows: Int)
@Serializable data class ScheduleDto(
    val projectUuid: String, val counterKey: Long?, val action: String, val label: String, val startRow: Int, val every: Int, val times: Int,
)
@Serializable data class PatternDto(
    val uuid: String, val projectUuid: String, val kind: String, val file: String, val title: String, val pageCount: Int,
    val readsUpward: Boolean, val rulerPage: Int, val rulerTop: Float, val rulerHeight: Float,
)
@Serializable data class MarkupDto(val patternUuid: String, val page: Int, val tool: String, val points: String)
@Serializable data class YarnDto(
    val uuid: String, val brand: String, val name: String, val colourway: String, val weight: String, val fibre: String,
    val metresPerSkein: Int?, val skeins: Float, val dyeLot: String, val photoFile: String?,
)
@Serializable data class ToolDto(val uuid: String, val type: String, val sizeMm: Float, val count: Int)
@Serializable data class ProjectYarnDto(val projectUuid: String, val yarnUuid: String, val skeinsUsed: Float)

/** The whole backup, format 1. Counters and schedules refer to each other by [CounterDto.key], local to the file. */
@Serializable
data class BackupFile(
    val format: Int = BackupCodec.FORMAT,
    val app: String = BackupCodec.APP,
    val exportedAt: Long,
    val projects: List<ProjectDto> = emptyList(),
    val counters: List<CounterDto> = emptyList(),
    val events: List<EventDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val schedules: List<ScheduleDto> = emptyList(),
    val patterns: List<PatternDto> = emptyList(),
    val markup: List<MarkupDto> = emptyList(),
    val yarns: List<YarnDto> = emptyList(),
    val tools: List<ToolDto> = emptyList(),
    val projectYarns: List<ProjectYarnDto> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

class BackupFormatException(message: String) : Exception(message)

enum class RestoreMode { MERGE, REPLACE }

/** What a merge keeps: incoming projects whose uuid is new, or every incoming project when replacing. */
data class RestorePlan(val newProjectUuids: Set<String>, val replacedProjectUuids: Set<String>)

object BackupCodec {
    const val FORMAT = 1
    const val APP = "rowmark"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun encode(file: BackupFile): String = json.encodeToString(file)

    /** Rejects anything without a format line or from another app, before any table is touched. */
    fun decode(text: String): BackupFile {
        val head = runCatching { json.parseToJsonElement(text) }.getOrNull()
            ?: throw BackupFormatException("not JSON")
        val obj = head as? kotlinx.serialization.json.JsonObject ?: throw BackupFormatException("not an object")
        val format = (obj["format"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            ?: throw BackupFormatException("no format")
        if (format > FORMAT) throw BackupFormatException("format $format is newer than $FORMAT")
        val app = (obj["app"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        if (app != APP) throw BackupFormatException("not a Rowmark backup")
        return runCatching { json.decodeFromString(BackupFile.serializer(), text) }
            .getOrElse { throw BackupFormatException("unreadable: ${it.message}") }
    }

    /** Merge by project uuid: an existing project is replaced by the file's copy, a new one is added. */
    fun plan(existingProjectUuids: Set<String>, incoming: BackupFile, mode: RestoreMode): RestorePlan {
        val incomingUuids = incoming.projects.map { it.uuid }.toSet()
        return when (mode) {
            RestoreMode.REPLACE -> RestorePlan(incomingUuids, existingProjectUuids)
            RestoreMode.MERGE -> RestorePlan(incomingUuids - existingProjectUuids, incomingUuids intersect existingProjectUuids)
        }
    }
}
