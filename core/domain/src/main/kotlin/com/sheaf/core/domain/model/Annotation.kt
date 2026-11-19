package com.sheaf.core.domain.model

/** A page annotation. Geometry is stored in page-normalized coordinates (0f..1f) so it is
 *  independent of render size / zoom. Ink uses [points]; highlight/underline use [points] as a
 *  polyline over the text; notes use [note] anchored at the first point. */
data class Annotation(
    val id: Long = 0,
    val documentId: Long,
    val pageIndex: Int,
    val type: AnnotationType,
    val colorArgb: Int,
    val strokeWidth: Float,
    val points: List<NormPoint>,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Stored by name, so new values are appended (older app versions read unknown names as Ink). */
enum class AnnotationType {
    Ink, Highlight, Underline, Strikethrough, Note,

    /** A placed signature image: see feature/reader SignaturePlacement for the point/note encoding. */
    Signature,
}

/** A point in page-normalized space; (0,0) = top-left, (1,1) = bottom-right of the page. */
data class NormPoint(val x: Float, val y: Float)
