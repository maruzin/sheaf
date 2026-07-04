package com.sheaf.core.domain.model

/** A PDF document known to the library index. */
data class Document(
    val id: Long,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val lastOpenedAt: Long?,
    val addedAt: Long,
    val isBookmarked: Boolean = false,
)

/** A saved reading position within a document. */
data class ReadingPosition(
    val documentId: Long,
    val pageIndex: Int,
    val zoom: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/** A single entry in a document's table of contents (outline). */
data class OutlineEntry(
    val title: String,
    val pageIndex: Int,
    val depth: Int,
)

/** A full-text search hit within a document. */
data class SearchHit(
    val pageIndex: Int,
    val snippet: String,
    val boundsOnPage: List<RectF>,
)

data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)
