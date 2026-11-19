package com.sheaf.feature.reader.markup

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.sheaf.core.domain.model.NormPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** The shapes the markup tools draw. White-out and Redact are filled boxes with their own meaning. */
enum class ShapeKind(val label: String) {
    Line("Line"),
    Arrow("Arrow"),
    Rectangle("Box"),
    Ellipse("Circle"),
    /** A white box: covers text so new text (a text box) can go on top. "Edit text", the honest way. */
    WhiteOut("White-out"),
    /** A black box; on export the page becomes an image, so the text underneath is really gone. */
    Redact("Redact"),
    /** A fillable text field in the exported copy (create a form). */
    FormText("Text field"),
    /** A fillable checkbox in the exported copy. */
    FormCheckbox("Checkbox"),
    ;

    val isFormField: Boolean get() = this == FormText || this == FormCheckbox
}

/**
 * A shape on a page, as the user sees the page: [start] and [end] normalized (0..1, top-left origin),
 * [widthFrac] the line width as a fraction of page width. Stored as an [Annotation] of type
 * [AnnotationType.Shape]: points = [start, end], note = kind name.
 */
data class ShapeSpec(
    val kind: ShapeKind,
    val start: NormPoint,
    val end: NormPoint,
    val colorArgb: Int,
    val widthFrac: Float,
) {
    val filled: Boolean get() = kind == ShapeKind.WhiteOut || kind == ShapeKind.Redact

    /** The colour actually drawn: white-out is always white, redaction always black. */
    val drawColor: Int
        get() = when (kind) {
            ShapeKind.WhiteOut -> 0xFFFFFFFF.toInt()
            ShapeKind.Redact -> 0xFF000000.toInt()
            else -> colorArgb
        }

    fun toAnnotation(documentId: Long, pageIndex: Int, id: Long = 0, createdAt: Long = System.currentTimeMillis()) =
        Annotation(
            id = id,
            documentId = documentId,
            pageIndex = pageIndex,
            type = AnnotationType.Shape,
            colorArgb = colorArgb,
            strokeWidth = widthFrac,
            points = listOf(start, end),
            note = kind.name,
            createdAt = createdAt,
        )

    /**
     * The two arrowhead strokes for an arrow ending at [end], in page pixels for a page [w] x [h]:
     * pairs of (x, y). Head length scales with the line width so thick arrows keep their shape.
     */
    fun arrowHead(w: Float, h: Float): List<Pair<Float, Float>> {
        val ex = end.x * w; val ey = end.y * h
        val angle = atan2((ey - start.y * h).toDouble(), (ex - start.x * w).toDouble())
        val len = (widthFrac * w * 5f).coerceAtLeast(w * 0.02f).toDouble()
        val spread = Math.toRadians(28.0)
        return listOf(angle + Math.PI - spread, angle + Math.PI + spread).map { a ->
            (ex + (len * cos(a)).toFloat()) to (ey + (len * sin(a)).toFloat())
        }
    }

    companion object {
        /** Default line width: about 2 pt on A4. */
        const val DEFAULT_WIDTH = 0.004f

        fun of(annotation: Annotation): ShapeSpec? {
            if (annotation.type != AnnotationType.Shape || annotation.points.size < 2) return null
            val kind = runCatching { ShapeKind.valueOf(annotation.note.orEmpty()) }.getOrNull() ?: return null
            return ShapeSpec(kind, annotation.points[0], annotation.points[1], annotation.colorArgb, annotation.strokeWidth)
        }

        /**
         * A drag from [start] to [end] (page-normalized) becomes a shape, or null when it is too small
         * to be intentional. Lines snap to horizontal or vertical within 4 degrees (underline, strikethrough).
         */
        fun fromDrag(kind: ShapeKind, start: NormPoint, end: NormPoint, colorArgb: Int, pageAspect: Float): ShapeSpec? {
            val dx = end.x - start.x
            val dy = (end.y - start.y) * pageAspect
            if (hypot(dx, dy) < 0.015f) return null
            var e = end
            if (kind == ShapeKind.Line || kind == ShapeKind.Arrow) {
                val deg = Math.toDegrees(atan2(abs(dy).toDouble(), abs(dx).toDouble()))
                if (deg < 4) e = NormPoint(end.x, start.y)
                else if (deg > 86) e = NormPoint(start.x, end.y)
            }
            return ShapeSpec(kind, start, e, colorArgb, DEFAULT_WIDTH)
        }
    }
}
