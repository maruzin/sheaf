package com.sheaf.feature.reader.compress

import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.sheaf.feature.reader.pdf.PdfBoxMemory
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

class PdfBoxCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) : PdfCompressor {

    override suspend fun compress(uri: String): String? = withContext(io) {
        ensureInit()
        runCatching {
            // Share-only output: cacheDir (served by the FileProvider), not permanent storage.
            val dir = File(context.cacheDir, "compressed").apply { mkdirs() }
            val out = File(dir, "compressed_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri.toUri())?.use { input ->
                PDDocument.load(input, PdfBoxMemory.setting()).use { doc ->
                    for (page in doc.pages) {
                        val res = page.resources ?: continue
                        val names = res.xObjectNames?.toList() ?: continue
                        for (name in names) {
                            runCatching {
                                val xobj = res.getXObject(name)
                                if (xobj is PDImageXObject) {
                                    val bmp = xobj.image ?: return@runCatching
                                    val longest = max(bmp.width, bmp.height)
                                    // Only touch large, opaque images: re-encoding small ones can inflate
                                    // size, and JPEG would flatten transparency (e.g. logos -> black boxes).
                                    if (longest <= MAX_DIM || bmp.hasAlpha()) return@runCatching
                                    val scale = MAX_DIM.toFloat() / longest
                                    val nw = (bmp.width * scale).roundToInt().coerceAtLeast(1)
                                    val nh = (bmp.height * scale).roundToInt().coerceAtLeast(1)
                                    val scaled = bmp.scale(nw, nh, true)
                                    val newImage = JPEGFactory.createFromImage(doc, scaled, JPEG_QUALITY)
                                    res.put(name, newImage)
                                }
                            }
                        }
                    }
                    doc.save(out)
                }
            }
            // Only report success if something was actually written (null stream = unreadable source).
            out.takeIf { it.length() > 0 }?.absolutePath
        }.getOrNull()
    }

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) PDFBoxResourceLoader.init(context.applicationContext)
    }

    private companion object {
        const val MAX_DIM = 1600
        const val JPEG_QUALITY = 0.6f
        val initialized = AtomicBoolean(false)
    }
}
