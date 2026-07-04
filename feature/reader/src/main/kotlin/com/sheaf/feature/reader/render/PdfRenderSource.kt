package com.sheaf.feature.reader.render

import android.graphics.Bitmap
import com.sheaf.core.domain.model.OutlineEntry

/**
 * Engine-agnostic contract for rendering + reading a PDF. Two candidate implementations
 * (PDFium fork, androidx.pdf) can sit behind this; the reader UI depends only on this interface.
 * Chosen to keep the M1 render-engine decision reversible without touching UI/VM code.
 */
interface PdfRenderSource {
    val pageCount: Int

    /** Renders [pageIndex] to a bitmap at the requested pixel size. Caller owns recycling. */
    suspend fun renderPage(pageIndex: Int, widthPx: Int, heightPx: Int): Bitmap

    /** Aspect ratio (height / width) of a page, for layout before the bitmap is ready. */
    fun pageAspectRatio(pageIndex: Int): Float

    /** Document outline / table of contents, empty if none. */
    suspend fun outline(): List<OutlineEntry>

    fun close()
}

/** Opens a [PdfRenderSource] for a document URI. Implemented over the chosen native engine. */
interface PdfRenderSourceFactory {
    suspend fun open(uri: String): PdfRenderSource
}
