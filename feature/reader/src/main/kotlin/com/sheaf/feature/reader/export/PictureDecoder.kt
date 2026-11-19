package com.sheaf.feature.reader.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import com.sheaf.core.data.log.AppLog
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes a photo or picture as a software ARGB bitmap no larger than [maxSide] on its long side,
 * upright. ImageDecoder (Android 9+) applies EXIF rotation itself; the BitmapFactory fallback
 * (Android 8, and sources ImageDecoder can't open) reads the EXIF orientation and applies it, so a
 * portrait phone photo never comes out sideways. Null when the picture can't be read or doesn't fit
 * in memory.
 */
object PictureDecoder {

    fun decode(context: Context, uri: Uri, maxSide: Int): Bitmap? =
        attempt { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) withImageDecoder(context, uri, maxSide) else null }
            ?: attempt { withBitmapFactory(context, uri, maxSide) }

    private inline fun attempt(block: () -> Bitmap?): Bitmap? = try {
        block()
    } catch (oom: OutOfMemoryError) {
        AppLog.e("Picture", "Picture too large to open", oom)
        null
    } catch (e: Exception) {
        AppLog.e("Picture", "Reading a picture failed", e)
        null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun withImageDecoder(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val src = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxSide) {
                val s = maxSide.toFloat() / longest
                decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
            }
        }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false).also { _ -> it.recycle() } }
    }

    private fun withBitmapFactory(context: Context, uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val upright = orient(raw, orientation)
        // BitmapFactory only halves; finish the scale so the long side respects maxSide exactly.
        val longest = max(upright.width, upright.height)
        if (longest <= maxSide) return upright
        val s = maxSide.toFloat() / longest
        return upright.scale((upright.width * s).roundToInt(), (upright.height * s).roundToInt())
            .also { if (it !== upright) upright.recycle() }
    }

    /** Applies an EXIF orientation (rotation and/or mirror) to [bmp]. */
    fun orient(bmp: Bitmap, orientation: Int): Bitmap {
        val m = orientationMatrix(orientation) ?: return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
    }

    fun orientationMatrix(orientation: Int): Matrix? = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { postRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { postRotate(270f); postScale(-1f, 1f) }
        else -> null
    }
}
