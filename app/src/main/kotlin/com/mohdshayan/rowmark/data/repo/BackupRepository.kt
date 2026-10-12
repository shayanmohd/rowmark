package com.mohdshayan.rowmark.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.mohdshayan.rowmark.core.BackupCodec
import com.mohdshayan.rowmark.core.BackupFile
import com.mohdshayan.rowmark.core.BackupFormatException
import com.mohdshayan.rowmark.core.CounterDto
import com.mohdshayan.rowmark.core.CsvWriter
import com.mohdshayan.rowmark.core.EventDto
import com.mohdshayan.rowmark.core.MarkupDto
import com.mohdshayan.rowmark.core.PatternDto
import com.mohdshayan.rowmark.core.ProjectDto
import com.mohdshayan.rowmark.core.ProjectYarnDto
import com.mohdshayan.rowmark.core.RestoreMode
import com.mohdshayan.rowmark.core.ScheduleDto
import com.mohdshayan.rowmark.core.SessionDto
import com.mohdshayan.rowmark.core.ToolDto
import com.mohdshayan.rowmark.core.YarnDto
import com.mohdshayan.rowmark.data.db.AppDatabase
import com.mohdshayan.rowmark.data.db.CountEvent
import com.mohdshayan.rowmark.data.db.Counter
import com.mohdshayan.rowmark.data.db.Markup
import com.mohdshayan.rowmark.data.db.Pattern
import com.mohdshayan.rowmark.data.db.Project
import com.mohdshayan.rowmark.data.db.ProjectYarn
import com.mohdshayan.rowmark.data.db.Schedule
import com.mohdshayan.rowmark.data.db.Session
import com.mohdshayan.rowmark.data.db.Tool
import com.mohdshayan.rowmark.data.db.Yarn
import com.mohdshayan.rowmark.data.prefs.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class RestoreResult {
    data class Ready(val file: BackupFile, val projectCount: Int) : RestoreResult()
    object NotABackup : RestoreResult()
    object Unreadable : RestoreResult()
}

/**
 * The backup is a zip: backup.json plus patterns/ and photos/ by hash. Everything the user made goes
 * in, locked or not; the entitlement never does (it comes back from Google Play).
 */
class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: AppPrefs,
    private val patterns: PatternRepository,
) {
    fun suggestedName(nowMs: Long = System.currentTimeMillis()): String =
        "rowmark-backup-" + DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())) + ".zip"

    suspend fun snapshot(): BackupFile = withContext(Dispatchers.IO) {
        val projects = db.projectDao().all()
        val byId = projects.associateBy { it.id }
        val counters = db.counterDao().all()
        val patternsAll = db.patternDao().all()
        val patternById = patternsAll.associateBy { it.id }
        val yarns = db.yarnDao().all()
        val yarnById = yarns.associateBy { it.id }
        BackupFile(
            exportedAt = System.currentTimeMillis(),
            projects = projects.map { ProjectDto(it.uuid, it.name, it.craft, it.status, it.needleOrHook, it.yarnText, it.notes, it.targetRows, it.totalRows, it.coverFile, it.createdAt, it.updatedAt, it.lastCountAt) },
            counters = counters.mapNotNull { c -> byId[c.projectId]?.let { p -> CounterDto(c.id, p.uuid, c.name, c.kind, c.value, c.length, c.parentId, c.resetsWithRow, c.alertEvery, c.alertLabel, c.position) } },
            events = db.countEventDao().all().mapNotNull { e -> byId[e.projectId]?.let { p -> EventDto(p.uuid, e.counterId, e.groupId, e.delta, e.valueAfter, e.source, e.at, e.totalDelta) } },
            sessions = db.sessionDao().all().mapNotNull { s -> byId[s.projectId]?.let { p -> SessionDto(p.uuid, s.startedAt, s.endedAt, s.activeMs, s.rows) } },
            schedules = db.scheduleDao().all().mapNotNull { s -> byId[s.projectId]?.let { p -> ScheduleDto(p.uuid, s.counterId, s.action, s.label, s.startRow, s.every, s.times) } },
            patterns = patternsAll.mapNotNull { pt -> byId[pt.projectId]?.let { p -> PatternDto(pt.uuid, p.uuid, pt.kind, pt.file, pt.title, pt.pageCount, pt.readsUpward, pt.rulerPage, pt.rulerTop, pt.rulerHeight) } },
            markup = db.markupDao().all().mapNotNull { m -> patternById[m.patternId]?.let { pt -> MarkupDto(pt.uuid, m.page, m.tool, m.points) } },
            yarns = yarns.map { YarnDto(it.uuid, it.brand, it.name, it.colourway, it.weight, it.fibre, it.metresPerSkein, it.skeins, it.dyeLot, it.photoFile) },
            tools = db.toolDao().all().map { ToolDto(it.uuid, it.type, it.sizeMm, it.count) },
            projectYarns = db.projectYarnDao().all().mapNotNull { l -> byId[l.projectId]?.let { p -> yarnById[l.yarnId]?.let { y -> ProjectYarnDto(p.uuid, y.uuid, l.skeinsUsed) } } },
            settings = prefs.exportable(),
        )
    }

    /** Writes the zip to [out]. Throws on an I/O failure, which the caller turns into the error line. */
    suspend fun writeBackup(out: OutputStream) = withContext(Dispatchers.IO) {
        val file = snapshot()
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(BackupCodec.encode(file).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (name in file.patterns.map { it.file }.toSet()) addFile(zip, "patterns/$name", File(patterns.patternsDir, name))
            val photos = file.projects.mapNotNull { it.coverFile } + file.yarns.mapNotNull { it.photoFile }
            for (name in photos.toSet()) addFile(zip, "photos/$name", patterns.photoFile(name))
        }
    }

    private fun addFile(zip: ZipOutputStream, entry: String, f: File) {
        if (!f.exists()) return
        zip.putNextEntry(ZipEntry(entry))
        f.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /** Reads and validates a backup, extracting its files to a staging folder, before any table changes. */
    suspend fun read(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var json: String? = null
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext RestoreResult.Unreadable
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val name = e.name
                    when {
                        name == "backup.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                        (name.startsWith("patterns/") || name.startsWith("photos/")) && !name.contains("..") -> {
                            val target = File(staging, name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            return@withContext RestoreResult.Unreadable
        }
        val text = json ?: return@withContext RestoreResult.NotABackup
        val file = try { BackupCodec.decode(text) } catch (e: BackupFormatException) { return@withContext RestoreResult.NotABackup }
        RestoreResult.Ready(file, file.projects.size)
    }

    /** Applies a backup read earlier. Merge keeps other projects and replaces matching uuids; Replace wipes first. */
    suspend fun apply(file: BackupFile, mode: RestoreMode) = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "restore")
        File(staging, "patterns").listFiles()?.forEach { it.copyTo(File(patterns.patternsDir, it.name), overwrite = true) }
        File(staging, "photos").listFiles()?.forEach { it.copyTo(File(patterns.photosDir, it.name), overwrite = true) }
        db.withTransaction {
            if (mode == RestoreMode.REPLACE) {
                db.projectDao().deleteAll()
                db.yarnDao().deleteAll()
                db.toolDao().deleteAll()
            }
            val projectIds = HashMap<String, Long>()
            for (p in file.projects) {
                db.projectDao().byUuid(p.uuid)?.let { db.projectDao().delete(it.id) }
                projectIds[p.uuid] = db.projectDao().insert(Project(0, p.uuid, p.name, p.craft, p.status, p.needleOrHook, p.yarnText, p.notes, p.targetRows, p.totalRows, p.coverFile, p.createdAt, p.updatedAt, p.lastCountAt))
            }
            val counterIds = HashMap<Long, Long>()
            for (c in file.counters) {
                val pid = projectIds[c.projectUuid] ?: continue
                counterIds[c.key] = db.counterDao().insert(Counter(0, pid, c.name, c.kind, c.value, c.length, null, c.resetsWithRow, c.alertEvery, c.alertLabel, c.position))
            }
            for (c in file.counters) {
                val id = counterIds[c.key] ?: continue
                val parent = c.parentKey?.let { counterIds[it] } ?: continue
                db.counterDao().get(id)?.let { db.counterDao().update(it.copy(parentId = parent)) }
            }
            val groupBase = db.countEventDao().maxGroup() + 1
            val events = file.events.mapNotNull { e ->
                val pid = projectIds[e.projectUuid] ?: return@mapNotNull null
                val cid = counterIds[e.counterKey] ?: return@mapNotNull null
                CountEvent(0, pid, cid, groupBase + e.groupId, e.delta, e.valueAfter, e.source, e.at, e.totalDelta)
            }
            if (events.isNotEmpty()) db.countEventDao().insertAll(events)
            for (s in file.sessions) {
                val pid = projectIds[s.projectUuid] ?: continue
                db.sessionDao().insert(Session(0, pid, s.startedAt, s.endedAt ?: s.startedAt + s.activeMs, s.activeMs, s.rows))
            }
            for (s in file.schedules) {
                val pid = projectIds[s.projectUuid] ?: continue
                db.scheduleDao().insert(Schedule(0, pid, s.counterKey?.let { counterIds[it] }, s.action, s.label, s.startRow, s.every, s.times))
            }
            val patternIds = HashMap<String, Long>()
            for (p in file.patterns) {
                val pid = projectIds[p.projectUuid] ?: continue
                patternIds[p.uuid] = db.patternDao().insert(Pattern(0, p.uuid, pid, p.kind, p.file, p.title, p.pageCount, p.readsUpward, p.rulerPage, p.rulerTop, p.rulerHeight))
            }
            for (m in file.markup) {
                val pid = patternIds[m.patternUuid] ?: continue
                db.markupDao().insert(Markup(0, pid, m.page, m.tool, m.points))
            }
            val yarnIds = HashMap<String, Long>()
            for (y in file.yarns) {
                val existing = db.yarnDao().byUuid(y.uuid)
                yarnIds[y.uuid] = db.yarnDao().upsert(Yarn(existing?.id ?: 0, y.uuid, y.brand, y.name, y.colourway, y.weight, y.fibre, y.metresPerSkein, y.skeins, y.dyeLot, y.photoFile))
            }
            for (t in file.tools) {
                val existing = db.toolDao().byUuid(t.uuid)
                db.toolDao().upsert(Tool(existing?.id ?: 0, t.uuid, t.type, t.sizeMm, t.count))
            }
            for (l in file.projectYarns) {
                val pid = projectIds[l.projectUuid] ?: continue
                val yid = yarnIds[l.yarnUuid] ?: db.yarnDao().byUuid(l.yarnUuid)?.id ?: continue
                db.projectYarnDao().upsert(ProjectYarn(pid, yid, l.skeinsUsed))
            }
        }
        prefs.importSettings(file.settings)
        // After a Replace the remembered project may be gone; the widget and the tile then start afresh.
        if (prefs.current().lastProjectId.let { it > 0 && db.projectDao().get(it) == null }) prefs.setLastProjectId(0L)
        staging.deleteRecursively()
    }

    /** The journal: one line per session, RFC 4180. */
    suspend fun journalCsv(): String = withContext(Dispatchers.IO) {
        val projects = db.projectDao().all().associateBy { it.id }
        val zone = ZoneId.systemDefault()
        val day = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val time = DateTimeFormatter.ofPattern("HH:mm")
        val rows = db.sessionDao().all().map { s ->
            val start = Instant.ofEpochMilli(s.startedAt).atZone(zone)
            val end = s.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
            listOf(
                projects[s.projectId]?.name ?: "", day.format(start), time.format(start), end?.let { time.format(it) } ?: "",
                (s.activeMs / 60_000L).toString(), s.rows.toString(),
            )
        }
        CsvWriter.document(listOf("project", "date", "start", "end", "active_minutes", "rows"), rows)
    }
}
