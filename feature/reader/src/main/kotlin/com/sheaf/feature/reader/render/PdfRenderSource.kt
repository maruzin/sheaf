package com.sheaf.feature.reader.render

import android.graphics.Bitmap
import com.sheaf.core.domain.model.OutlineEntry

/**
 * Engine-agnostic contract for rendering a PDF. The M1 baseline is backed by the platform
 * PdfRenderer; PDFium / androidx.pdf can replace it without touching UI/VM code.
 */
interface PdfRenderSource {
    val pageCount: Int

    /** Renders [pageIndex] to an ARGB_8888 bitmap at the requested pixel size. */
    suspend fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): Bitmap

    /** height/width of a page, so the UI can lay out the slot before the bitmap is ready. */
    fun pageAspectRatio(pageIndex: Int): Float

    /** Document outline; empty when the engine exposes none (PdfRenderer has no outline API). */
    suspend fun outline(): List<OutlineEntry>

    fun close()
}

interface PdfRenderSourceFactory {
    /** Opens a render source for a content:// or file:// [uri]. */
    suspend fun open(uri: String): PdfRenderSource
}
