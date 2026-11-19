package com.sheaf.feature.reader.signature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.sheaf.core.data.log.AppLog
import com.sheaf.core.domain.model.NormPoint
import com.sheaf.feature.reader.export.PictureDecoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Saved signature images (transparent PNGs) in noBackupFilesDir: personal data that never goes to
 * cloud backup or device transfer. Placed signatures reference an image by file name, so changing
 * your signature later never alters documents you already signed.
 *
 * The list of saved signatures (newest first) lives in the signature preference as
 * "png:a.png|b.png" (older versions stored one polyline there; [SignatureStrokes.decodeLegacy]).
 */
@Singleton
class SignatureStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) {
    sealed interface ImportResult {
        data class Ok(val name: String) : ImportResult
        data object NoInk : ImportResult
        data object Unreadable : ImportResult
    }

    private val dir: File get() = File(context.noBackupFilesDir, "signatures").apply { mkdirs() }

    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Only plain names Sheaf generated: a placed signature's note can never point outside [dir]. */
    fun file(name: String): File? =
        name.takeIf { NAME.matches(it) }?.let { File(dir, it) }?.takeIf { it.isFile }

    suspend fun load(name: String): Bitmap? = withContext(io) {
        cache.get(name) ?: runCatching {
            file(name)?.let { BitmapFactory.decodeFile(it.absolutePath) }
        }.getOrNull()?.also { cache.put(name, it) }
    }

    /** Width / height of a saved signature, or a typical 2.5 if it can't be read. */
    suspend fun aspect(name: String): Float = load(name)?.let { it.width.toFloat() / it.height.coerceAtLeast(1) } ?: 2.5f

    /** Renders hand-drawn [strokes] (normalized over a pad [padW] x [padH] px) into a new signature image. */
    suspend fun saveDrawn(
        strokes: List<List<NormPoint>>,
        padW: Float,
        padH: Float,
        lineWidthPx: Float,
        colorArgb: Int,
    ): String? = withContext(io) {
        val layout = SignatureStrokes.layout(strokes, padW, padH, lineWidthPx) ?: return@withContext null
        runCatching {
            val bmp = createBitmap(layout.width, layout.height)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorArgb
                style = Paint.Style.STROKE
                strokeWidth = layout.lineWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val canvas = Canvas(bmp)
            layout.strokes.forEach { s ->
                if (s.size == 1) {
                    // A tap is a dot (the dot on an "i").
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(s[0].first, s[0].second, layout.lineWidth / 2f, paint)
                    paint.style = Paint.Style.STROKE
                } else {
                    val path = Path().apply {
                        moveTo(s[0].first, s[0].second)
                        for (i in 1 until s.size) lineTo(s[i].first, s[i].second)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            writePng(bmp)
        }.onFailure { AppLog.e("Signature", "Saving a drawn signature failed", it) }.getOrNull()
    }

    /** Cuts the signature out of a photo or picture ([uri]) and saves it as a new signature image. */
    suspend fun import(uri: Uri): ImportResult = withContext(io) {
        try {
            val bitmap = decode(uri, MAX_IMPORT_SIDE) ?: return@withContext ImportResult.Unreadable
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            bitmap.recycle()
            when (val r = SignatureInk.extract(pixels, w, h)) {
                SignatureInk.Result.NoInk -> ImportResult.NoInk
                is SignatureInk.Result.Ok -> {
                    val out = Bitmap.createBitmap(r.image.pixels, r.image.width, r.image.height, Bitmap.Config.ARGB_8888)
                    ImportResult.Ok(writePng(out))
                }
            }
        } catch (oom: OutOfMemoryError) {
            // Low-memory phones: report it instead of crashing the app.
            AppLog.e("Signature", "Signature picture too large to process", oom)
            ImportResult.Unreadable
        } catch (e: Exception) {
            AppLog.e("Signature", "Importing the signature picture failed", e)
            ImportResult.Unreadable
        }
    }

    /**
     * Deletes signature images nothing uses any more: not in the saved list and not placed on any
     * document ([keep]). Removed signatures are personal data and shouldn't linger on the phone.
     * Files younger than a few minutes are left alone, so a signature being saved right now is safe.
     */
    suspend fun sweep(keep: Set<String>, now: Long = System.currentTimeMillis()): Int = withContext(io) {
        var deleted = 0
        dir.listFiles()?.forEach { f ->
            if (NAME.matches(f.name) && f.name !in keep && now - f.lastModified() > SWEEP_MIN_AGE_MS) {
                cache.remove(f.name)
                File(f.path + ".txt").delete()
                if (f.delete()) deleted++
            }
        }
        if (deleted > 0) AppLog.i("Signature", "Deleted $deleted unused signature image(s)")
        deleted
    }

    /** Fill-and-sign stamps: placed and exported exactly like a signature image. */
    enum class Stamp { Date, Tick, Cross, Text, Picture }

    /**
     * Renders a stamp as a transparent PNG in signature ink: [text] for [Stamp.Text] and [Stamp.Date]
     * (the caller formats the date), drawn paths for a tick or cross (fonts may lack the glyphs).
     * Null on failure or blank text.
     */
    suspend fun saveStamp(kind: Stamp, text: String = ""): String? = withContext(io) {
        runCatching {
            val ink = 0xFF15171C.toInt()
            val bmp = when (kind) {
                Stamp.Tick, Stamp.Cross -> {
                    val size = 200
                    createBitmap(size, size).also { b ->
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = ink
                            style = Paint.Style.STROKE
                            strokeWidth = 24f
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                        val c = Canvas(b)
                        if (kind == Stamp.Tick) {
                            c.drawPath(Path().apply { moveTo(30f, 108f); lineTo(80f, 160f); lineTo(172f, 42f) }, paint)
                        } else {
                            c.drawLine(42f, 42f, 158f, 158f, paint)
                            c.drawLine(158f, 42f, 42f, 158f, paint)
                        }
                    }
                }
                Stamp.Picture -> return@withContext null // pictures come from importPicture
                Stamp.Text, Stamp.Date -> {
                    val clean = text.replace(Regex("\\s+"), " ").trim().take(MAX_STAMP_CHARS)
                    if (clean.isEmpty()) return@withContext null
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = ink
                        textSize = 110f
                        typeface = android.graphics.Typeface.SANS_SERIF
                    }
                    val fm = paint.fontMetrics
                    val pad = 16f
                    val w = (paint.measureText(clean) + pad * 2).roundToInt().coerceIn(16, 4000)
                    val h = (fm.descent - fm.ascent + pad * 2).roundToInt()
                    createBitmap(w, h).also { b -> Canvas(b).drawText(clean, pad, pad - fm.ascent, paint) }
                }
            }
            writePng(bmp)
        }.onFailure { AppLog.e("Signature", "Making a stamp failed", it) }.getOrNull()
    }

    /** A text box's words and style, kept beside its image so it can be edited after placing. */
    data class TextBox(val text: String, val size: Int, val colorArgb: Int) {
        companion object {
            /** Rendered text heights (px) for Small, Medium and Large. */
            val SIZES = listOf(70f, 110f, 160f)
            val COLOURS = listOf(0xFF15171C.toInt() to "Black", 0xFF1F3FA8.toInt() to "Blue", 0xFFC5221F.toInt() to "Red")
        }
    }

    /**
     * Renders a multi-line text box (up to [MAX_TEXTBOX_CHARS]) as a transparent PNG and remembers its
     * words and style, so "Edit text" can re-render it later. Null for blank text or on failure.
     */
    suspend fun saveTextBox(box: TextBox): String? = withContext(io) {
        val lines = box.text.replace("\r", "").take(MAX_TEXTBOX_CHARS).split('\n').map { it.trimEnd() }
            .dropLastWhile { it.isBlank() }
        if (lines.isEmpty() || lines.all { it.isBlank() }) return@withContext null
        runCatching {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = box.colorArgb
                textSize = TextBox.SIZES[box.size.coerceIn(0, TextBox.SIZES.lastIndex)]
                typeface = android.graphics.Typeface.SANS_SERIF
            }
            val fm = paint.fontMetrics
            val lineH = fm.descent - fm.ascent
            val pad = paint.textSize * 0.15f
            val w = (lines.maxOf { paint.measureText(it) } + pad * 2).roundToInt().coerceIn(16, 4000)
            val h = (lineH * lines.size + pad * 2).roundToInt().coerceIn(16, 4000)
            val bmp = createBitmap(w, h)
            val c = Canvas(bmp)
            lines.forEachIndexed { i, line -> c.drawText(line, pad, pad - fm.ascent + i * lineH, paint) }
            val name = writePng(bmp)
            File(dir, "$name.txt").writeText("${box.size}|${box.colorArgb}|${box.text}")
            name
        }.onFailure { AppLog.e("Signature", "Making a text box failed", it) }.getOrNull()
    }

    /** The words and style of a text box image, or null for any other image. */
    suspend fun textBox(name: String): TextBox? = withContext(io) {
        val f = file(name)?.let { File(it.path + ".txt") }?.takeIf { it.isFile } ?: return@withContext null
        runCatching {
            val raw = f.readText()
            val (size, colour) = raw.split('|', limit = 3).let { it[0].toInt() to it[1].toInt() }
            TextBox(raw.split('|', limit = 3)[2], size, colour)
        }.getOrNull()
    }

    /**
     * A photo, logo or picture to place on a page, kept in its own colours (no ink extraction). Opaque
     * pictures are saved without alpha so exports embed them as compact JPEG.
     */
    suspend fun importPicture(uri: Uri): String? = withContext(io) {
        try {
            val bmp = decode(uri, MAX_PICTURE_SIDE) ?: return@withContext null
            val opaque = !bmp.hasAlpha() || run {
                val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                px.all { (it ushr 24) == 0xFF }
            }
            val out = if (bmp.isMutable) bmp else bmp.copy(Bitmap.Config.ARGB_8888, true).also { bmp.recycle() }
            if (opaque) out.setHasAlpha(false)
            writePng(out)
        } catch (oom: OutOfMemoryError) {
            AppLog.e("Signature", "Picture too large to place", oom); null
        } catch (e: Exception) {
            AppLog.e("Signature", "Placing a picture failed", e); null
        }
    }

    /** Converts a legacy single-stroke signature (drawn on a 2.5:1 pad) into an image. */
    suspend fun fromLegacy(points: List<NormPoint>): String? =
        if (points.size < 2) null else saveDrawn(listOf(points), 1000f, 400f, 10f, 0xFF15171C.toInt())

    private fun writePng(bmp: Bitmap): String {
        val name = "sig_${System.currentTimeMillis()}_${(Math.random() * 1e6).roundToInt()}.png"
        val f = File(dir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        cache.put(name, bmp)
        return name
    }

    private fun decode(uri: Uri, maxSide: Int): Bitmap? = PictureDecoder.decode(context, uri, maxSide)

    companion object {
        const val PREFIX = "png:"
        const val MAX_SAVED = 6
        const val MAX_STAMP_CHARS = 60
        const val MAX_TEXTBOX_CHARS = 500
        private const val MAX_PICTURE_SIDE = 2000
        private const val MAX_IMPORT_SIDE = 1200
        private const val SWEEP_MIN_AGE_MS = 5 * 60_000L
        private val NAME = Regex("sig_[0-9_]+\\.png")

        fun decodeList(pref: String): List<String> =
            if (!pref.startsWith(PREFIX)) emptyList()
            else pref.removePrefix(PREFIX).split('|').filter { NAME.matches(it) }

        fun encodeList(names: List<String>): String = PREFIX + names.distinct().take(MAX_SAVED).joinToString("|")
    }
}
