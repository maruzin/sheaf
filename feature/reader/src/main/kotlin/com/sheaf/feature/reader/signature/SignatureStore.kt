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
                if (f.delete()) deleted++
            }
        }
        if (deleted > 0) AppLog.i("Signature", "Deleted $deleted unused signature image(s)")
        deleted
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
        private const val MAX_IMPORT_SIDE = 1200
        private const val SWEEP_MIN_AGE_MS = 5 * 60_000L
        private val NAME = Regex("sig_[0-9_]+\\.png")

        fun decodeList(pref: String): List<String> =
            if (!pref.startsWith(PREFIX)) emptyList()
            else pref.removePrefix(PREFIX).split('|').filter { NAME.matches(it) }

        fun encodeList(names: List<String>): String = PREFIX + names.distinct().take(MAX_SAVED).joinToString("|")
    }
}
