package com.mohdshayan.rowmark.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.mohdshayan.rowmark.core.RulerMath
import com.mohdshayan.rowmark.data.db.AppDatabase
import com.mohdshayan.rowmark.data.db.Markup
import com.mohdshayan.rowmark.data.db.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

enum class PatternKind { PDF, IMAGE }

sealed class ImportResult {
    data class Added(val pattern: Pattern) : ImportResult()
    object PasswordProtected : ImportResult()
    object Unreadable : ImportResult()
}

/**
 * Pattern files live under filesDir/patterns by content hash, so the backup carries them by name and a
 * file added twice is stored once. PdfRenderer opens one page at a time on the IO dispatcher.
 */
class PatternRepository(private val context: Context, private val db: AppDatabase) {

    val patternsDir: File get() = File(context.filesDir, "patterns").apply { mkdirs() }
    val photosDir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    fun observePatterns(projectId: Long): Flow<List<Pattern>> = db.patternDao().observeForProject(projectId)
    fun observePatternCount(projectId: Long): Flow<Int> = db.patternDao().observeCount(projectId)
    fun observeMarkup(patternId: Long): Flow<List<Markup>> = db.markupDao().observeForPattern(patternId)

    suspend fun import(projectId: Long, uri: Uri, kind: PatternKind, title: String): ImportResult = withContext(Dispatchers.IO) {
        val ext = if (kind == PatternKind.PDF) "pdf" else "img"
        val file = runCatching { copyByHash(context.contentResolver.openInputStream(uri) ?: return@withContext ImportResult.Unreadable, patternsDir, ext) }
            .getOrElse { return@withContext ImportResult.Unreadable }
        val pages = if (kind == PatternKind.PDF) {
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd -> PdfRenderer(pfd).use { it.pageCount } }
            } catch (e: SecurityException) {
                file.delete(); return@withContext ImportResult.PasswordProtected
            } catch (e: Exception) {
                file.delete(); return@withContext ImportResult.Unreadable
            }
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, opts)
            if (opts.outWidth <= 0) { file.delete(); return@withContext ImportResult.Unreadable }
            1
        }
        val pattern = Pattern(
            uuid = UUID.randomUUID().toString(), projectId = projectId, kind = kind.name, file = file.name,
            title = title.ifBlank { if (kind == PatternKind.PDF) "Pattern" else "Photo" }, pageCount = pages,
            rulerTop = 0.9f, rulerHeight = 0.05f,
        )
        val id = db.patternDao().insert(pattern)
        ImportResult.Added(pattern.copy(id = id))
    }

    /** Copies a stream into [dir] named by its SHA-256, returning the file; a repeat import reuses it. */
    fun copyByHash(input: InputStream, dir: File, ext: String): File {
        val tmp = File.createTempFile("import", ".tmp", context.cacheDir)
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { ins ->
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    digest.update(buf, 0, n)
                    out.write(buf, 0, n)
                }
            }
        }
        val name = digest.digest().joinToString("") { "%02x".format(it) } + "." + ext
        val target = File(dir, name)
        if (target.exists()) tmp.delete() else if (!tmp.renameTo(target)) { tmp.copyTo(target, overwrite = true); tmp.delete() }
        return target
    }

    suspend fun deletePattern(pattern: Pattern) = withContext(Dispatchers.IO) {
        db.patternDao().delete(pattern.id)
        val stillUsed = db.patternDao().all().any { it.file == pattern.file }
        if (!stillUsed) File(patternsDir, pattern.file).delete()
        cache.remove(pattern.id to 0)
    }

    suspend fun setRuler(patternId: Long, page: Int, top: Float, height: Float) {
        val h = RulerMath.clampHeight(height)
        db.patternDao().setRuler(patternId, page, RulerMath.clampTop(top, h), h)
    }

    suspend fun setDirection(patternId: Long, upward: Boolean) = db.patternDao().setDirection(patternId, upward)

    suspend fun addStroke(patternId: Long, page: Int, points: List<Pair<Float, Float>>) {
        val json = points.joinToString(",", "[", "]") { "[%.4f,%.4f]".format(it.first, it.second) }
        db.markupDao().insert(Markup(patternId = patternId, page = page, points = json))
    }

    suspend fun undoStroke(patternId: Long): Boolean {
        val last = db.markupDao().last(patternId) ?: return false
        db.markupDao().delete(last)
        return true
    }

    private val cache = object : LinkedHashMap<Pair<Long, Int>, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Long, Int>, Bitmap>?): Boolean = size > 3
    }
    private val renderLock = Mutex()

    /** One page at screen width, cached three deep. Null when the file is gone. */
    suspend fun renderPage(pattern: Pattern, page: Int, widthPx: Int): Bitmap? = renderLock.withLock {
        val key = pattern.id to page
        cache[key]?.let { if (it.width >= widthPx / 2) return@withLock it }
        val bmp = withContext(Dispatchers.IO) {
            val file = File(patternsDir, pattern.file)
            if (!file.exists()) return@withContext null
            runCatching {
                if (pattern.kind == PatternKind.PDF.name) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { r ->
                            r.openPage(page.coerceIn(0, r.pageCount - 1)).use { p ->
                                val w = widthPx.coerceIn(320, 2400)
                                val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                b.eraseColor(0xFFFFFFFF.toInt())
                                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                b
                            }
                        }
                    }
                } else {
                    decodeScaled(file, widthPx.coerceIn(320, 2400))
                }
            }.getOrNull()
        }
        if (bmp != null) cache[key] = bmp
        bmp
    }

    fun decodeScaled(file: File, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** The bundled sample chart, for the locked teaser. */
    fun sampleChart(): Bitmap? = runCatching { context.assets.open("sample_chart.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()

    suspend fun importPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
            copyByHash(input, photosDir, "img").name
        }.getOrNull()
    }

    fun photoFile(name: String): File = File(photosDir, name)
}
