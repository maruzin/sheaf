package com.sheaf.feature.reader.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.sheaf.core.domain.model.OutlineEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [PdfRenderSource] backed by android.graphics.pdf.PdfRenderer.
 *
 * PdfRenderer allows only ONE page open at a time, so all access is serialized behind [mutex].
 * Rendering runs on [io] (typically Dispatchers.IO). Bitmaps are white-filled before render so
 * transparent PDF regions don't come out black. Aspect ratios are pre-read once at open time to
 * keep layout cheap (avoids opening pages just to measure).
 */
class AndroidPdfRenderSource private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val aspectRatios: FloatArray,
    private val io: CoroutineDispatcher,
) : PdfRenderSource {

    private val mutex = Mutex()

    override val pageCount: Int get() = renderer.pageCount

    override fun pageAspectRatio(pageIndex: Int): Float =
        aspectRatios.getOrElse(pageIndex) { 1.4142f } // A4-ish fallback

    override suspend fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): Bitmap =
        withContext(io) {
            require(widthPx > 0 && heightPx > 0) { "render size must be positive" }
            mutex.withLock {
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                renderer.openPage(pageIndex).use { page ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                bitmap
            }
        }

    // PdfRenderer exposes no document outline. A text-capable engine (PdfBox/PDFium) fills this in
    // when full-text search + ToC land (tracked in BUILD_NOTES as the M1 engine-upgrade increment).
    override suspend fun outline(): List<OutlineEntry> = emptyList()

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val io: CoroutineDispatcher,
    ) : PdfRenderSourceFactory {
        override suspend fun open(uri: String): PdfRenderSource = withContext(io) {
            val pfd = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
                ?: error("Could not open $uri")
            val renderer = PdfRenderer(pfd)
            val ratios = FloatArray(renderer.pageCount)
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { p ->
                    ratios[i] = if (p.width > 0) p.height.toFloat() / p.width.toFloat() else 1.4142f
                }
            }
            AndroidPdfRenderSource(pfd, renderer, ratios, io)
        }
    }
}
