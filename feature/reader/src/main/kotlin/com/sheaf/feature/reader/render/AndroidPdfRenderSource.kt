package com.sheaf.feature.reader.render

import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.sheaf.core.domain.model.OutlineEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [PdfRenderSource] backed by android.graphics.pdf.PdfRenderer.
 *
 * PdfRenderer allows only ONE page open at a time, so all access is serialized behind [mutex].
 * Rendering runs on [io] (typically Dispatchers.IO). Bitmaps are white-filled before render so
 * transparent PDF regions don't come out black. Aspect ratios of the first [EAGER_MEASURE] pages
 * are read at open time; the rest are measured in the background between renders, so a 2,000-page
 * PDF opens without first touching every page. Until a page is measured it borrows the first
 * page's shape, which is exact for the usual uniform document.
 */
class AndroidPdfRenderSource private constructor(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val aspectRatios: FloatArray,
    measuredUpTo: Int,
    private val io: CoroutineDispatcher,
) : PdfRenderSource {

    private val mutex = Mutex()

    // close() can arrive while a render holds the (single) open page. Closing the renderer under it
    // throws and leaks the native renderer, so a busy close is deferred to the end of that render.
    @Volatile private var closed = false
    private val closeLock = Any()
    private var released = false
    private val measureScope = CoroutineScope(SupervisorJob() + io)
    private val fallbackRatio = aspectRatios.firstOrNull()?.takeIf { it > 0f } ?: 1.4142f
    private val _ratioVersion = MutableStateFlow(0)
    override val ratioVersion: StateFlow<Int> = _ratioVersion.asStateFlow()

    init {
        if (measuredUpTo < aspectRatios.size) {
            measureScope.launch {
                var next = measuredUpTo
                while (next < aspectRatios.size && isActive && !closed) {
                    // Small batches under the page lock, so on-screen renders never wait long.
                    mutex.withLock {
                        try {
                            if (closed) return@withLock
                            val end = minOf(next + MEASURE_BATCH, aspectRatios.size)
                            for (i in next until end) aspectRatios[i] = runCatching { measure(renderer, i) }.getOrDefault(fallbackRatio)
                            next = end
                            _ratioVersion.value++
                        } finally {
                            // close() arrived mid-batch and couldn't take the lock: release here.
                            if (closed) releaseNow()
                        }
                    }
                    yield()
                }
            }
        }
    }


    override val pageCount: Int get() = renderer.pageCount

    override fun pageAspectRatio(pageIndex: Int): Float =
        aspectRatios.getOrElse(pageIndex) { 0f }.takeIf { it > 0f } ?: fallbackRatio

    override suspend fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): Bitmap =
        withContext(io) {
            require(widthPx > 0 && heightPx > 0) { "render size must be positive" }
            mutex.withLock {
                check(!closed) { "render source closed" }
                try {
                    val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    renderer.openPage(pageIndex).use { page ->
                        if (aspectRatios.getOrElse(pageIndex) { 1f } <= 0f && page.width > 0) {
                            aspectRatios[pageIndex] = page.height.toFloat() / page.width.toFloat()
                        }
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                    bitmap
                } finally {
                    if (closed) releaseNow()
                }
            }
        }

    // PdfRenderer exposes no document outline. A text-capable engine (PdfBox/PDFium) fills this in
    // when full-text search + ToC land (tracked in BUILD_NOTES as the M1 engine-upgrade increment).
    override suspend fun outline(): List<OutlineEntry> = emptyList()

    override fun close() {
        closed = true
        measureScope.cancel()
        // Free now if no render is in flight; otherwise the in-flight render frees on exit.
        if (mutex.tryLock()) {
            try {
                releaseNow()
            } finally {
                mutex.unlock()
            }
        }
    }

    private fun releaseNow() = synchronized(closeLock) {
        if (released) return@synchronized
        released = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }

    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val io: CoroutineDispatcher,
    ) : PdfRenderSourceFactory {
        override suspend fun open(uri: String, password: String?): PdfRenderSource = withContext(io) {
            // Android 8 PdfRenderer bug: never hand it a protected or broken file (see the guard).
            PdfRendererGuard.check(context, uri)
            val pfd = context.contentResolver.openFileDescriptor(uri.toUri(), "r")
                ?: error("Could not open $uri")
            // PdfRenderer throws on corrupt or password-protected files; close the descriptor then,
            // or every failed import / password retry leaks one.
            val renderer = try {
                PdfRenderer(pfd)
            } catch (t: Throwable) {
                runCatching { pfd.close() }
                throw t
            }
            // 0 = not measured yet.
            val ratios = FloatArray(renderer.pageCount)
            val eager = minOf(renderer.pageCount, EAGER_MEASURE)
            try {
                for (i in 0 until eager) ratios[i] = measure(renderer, i)
            } catch (t: Throwable) {
                runCatching { renderer.close() }
                runCatching { pfd.close() }
                throw t
            }
            AndroidPdfRenderSource(pfd, renderer, ratios, eager, io)
        }
    }

    private companion object {
        const val EAGER_MEASURE = 150
        const val MEASURE_BATCH = 50

        fun measure(renderer: PdfRenderer, index: Int): Float =
            renderer.openPage(index).use { p ->
                if (p.width > 0) p.height.toFloat() / p.width.toFloat() else 1.4142f
            }
    }
}
