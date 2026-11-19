package com.sheaf.feature.reader.signature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.createBitmap
import com.sheaf.core.data.log.AppLog
import com.sheaf.core.domain.model.NormPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
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
        val bitmap = runCatching { decode(uri, MAX_IMPORT_SIDE) }
            .onFailure { AppLog.e("Signature", "Reading the signature picture failed", it) }
            .getOrNull() ?: return@withContext ImportResult.Unreadable
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val w = bitmap.width
        val h = bitmap.height
        bitmap.recycle()
        when (val r = SignatureInk.extract(pixels, w, h)) {
            SignatureInk.Result.NoInk -> ImportResult.NoInk
            is SignatureInk.Result.Ok -> {
                val out = Bitmap.createBitmap(r.image.pixels, r.image.width, r.image.height, Bitmap.Config.ARGB_8888)
                val name = runCatching { writePng(out) }.getOrNull()
                if (name != null) ImportResult.Ok(name) else ImportResult.Unreadable
            }
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

    private fun decode(uri: Uri, maxSide: Int): Bitmap? =
        runCatching { decodeWith(uri, maxSide, preferImageDecoder = true) }.getOrNull()
            // Some sources ImageDecoder can't open (and test environments) still decode the classic way.
            ?: decodeWith(uri, maxSide, preferImageDecoder = false)

    private fun decodeWith(uri: Uri, maxSide: Int, preferImageDecoder: Boolean): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && preferImageDecoder) {
            // ImageDecoder applies the photo's EXIF rotation; a software bitmap so pixels can be read.
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > maxSide) {
                    val s = maxSide.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                }
            }.copy(Bitmap.Config.ARGB_8888, false)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    companion object {
        const val PREFIX = "png:"
        const val MAX_SAVED = 6
        private const val MAX_IMPORT_SIDE = 1600
        private val NAME = Regex("sig_[0-9_]+\\.png")

        fun decodeList(pref: String): List<String> =
            if (!pref.startsWith(PREFIX)) emptyList()
            else pref.removePrefix(PREFIX).split('|').filter { NAME.matches(it) }

        fun encodeList(names: List<String>): String = PREFIX + names.distinct().take(MAX_SAVED).joinToString("|")
    }
}
