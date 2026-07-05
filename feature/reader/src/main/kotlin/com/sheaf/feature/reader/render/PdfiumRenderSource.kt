package com.sheaf.feature.reader.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.sheaf.core.domain.model.OutlineEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [PdfRenderSource] backed by PDFium (io.legere). Renders faster than the platform PdfRenderer and can
 * open password-protected PDFs (via [Factory.open]'s password argument). Rendering runs on [io] and is
 * serialized behind [mutex]. Aspect ratios are pre-read once at open time so layout is cheap.
 * Document outline still comes from the PdfBox extractor, so [outline] returns empty here.
 */
class PdfiumRenderSource private constructor(
    private val pfd: ParcelFileDescriptor,
    private val document: PdfDocument,
    private val aspectRatios: FloatArray,
    override val pageCount: Int,
    private val io: CoroutineDispatcher,
) : PdfRenderSource {

    private val mutex = Mutex()

    override fun pageAspectRatio(pageIndex: Int): Float =
        aspectRatios.getOrElse(pageIndex) { 1.4142f }

    override suspend fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): Bitmap =
        withContext(io) {
            require(widthPx > 0 && heightPx > 0) { "render size must be positive" }
            mutex.withLock {
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                document.openPage(pageIndex)?.use { page ->
                    page.renderPageBitmap(
                        bitmap, 0, 0, widthPx, heightPx,
                        false, false, Color.WHITE, Color.WHITE,
                    )
                }
                bitmap
            }
        }

    override suspend fun outline(): List<OutlineEntry> = emptyList()

    override fun close() {
        runCatching { document.close() }
        runCatching { pfd.close() }
    }

    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val io: CoroutineDispatcher,
    ) : PdfRenderSourceFactory {
        override suspend fun open(uri: String, password: String?): PdfRenderSource =
            withContext(io) {
                val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                    ?: error("Could not open $uri")
                val core = PdfiumCore(context)
                val document = core.newDocument(pfd, password)
                val count = document.getPageCount()
                val ratios = FloatArray(count)
                for (i in 0 until count) {
                    document.openPage(i)?.use { p ->
                        val w = p.getPageWidthPoint()
                        val h = p.getPageHeightPoint()
                        ratios[i] = if (w > 0) h.toFloat() / w.toFloat() else 1.4142f
                    }
                }
                PdfiumRenderSource(pfd, document, ratios, count, io)
            }
    }
}
