package com.sheaf.feature.reader.pages

/** A page card in the editor: its original index in the source doc and applied rotation (deg). */
data class PageItem(val sourceIndex: Int, val rotation: Int)

data class PagesUiState(
    val isLoading: Boolean = true,
    val documentId: Long? = null,
    val uri: String = "",
    val displayName: String = "",
    val items: List<PageItem> = emptyList(),
    val saving: Boolean = false,
    val savedDocumentId: Long? = null,
    /** Select mode (for Extract): which pages, by source index, are ticked. */
    val selecting: Boolean = false,
    val selected: Set<Int> = emptySet(),
    /** The document couldn't be opened: shown instead of the grid. */
    val error: String? = null,
    /** A save or extract failed: shown as a message with Retry, keeping the arrangement on screen. */
    val actionError: String? = null,
    /** Pages were rotated, deleted or moved and not yet saved. */
    val edited: Boolean = false,
)

/** "pages 2-4, 7": the 1-based positions of extracted pages, for the new file's name. */
object PageRanges {
    fun label(positions: List<Int>): String {
        val sorted = positions.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val parts = mutableListOf<String>()
        var start = sorted[0]
        var prev = start
        for (n in sorted.drop(1) + listOf(Int.MIN_VALUE)) {
            if (n == prev + 1) { prev = n; continue }
            parts += if (start == prev) "$start" else "$start-$prev"
            start = n; prev = n
        }
        return (if (sorted.size == 1) "page " else "pages ") + parts.joinToString(", ")
    }
}
