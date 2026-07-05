package com.sheaf.feature.reader.pages

/** One page in the desired output arrangement: which source page, and how much to rotate it. */
data class PageOp(val sourceIndex: Int, val rotation: Int)

/** Applies a page arrangement (reorder + delete + rotate) and writes a new PDF. */
interface PdfPageEditor {
    /** Returns the output file path, or null on failure. */
    suspend fun save(uri: String, plan: List<PageOp>): String?
}
