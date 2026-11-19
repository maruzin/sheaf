package com.sheaf.feature.reader.signature

import com.sheaf.core.domain.model.Annotation
import com.sheaf.core.domain.model.AnnotationType
import com.sheaf.core.domain.model.NormPoint
import kotlin.math.cos
import kotlin.math.sin

/**
 * Where a signature image sits on a page, as the user sees the page.
 *
 * - [cx], [cy]: the box centre, normalized over the displayed page (0..1, top-left origin).
 * - [w], [h]: the box size as fractions of the page **width** (both), so a box keeps its real shape
 *   on any page proportion and can be turned freely without stretching.
 * - [rotation]: clockwise degrees, any angle.
 *
 * Stored in an [Annotation] of type [AnnotationType.Signature]: points = [(cx, cy), (w, h)],
 * note = "<image file>@<rotation>". The image file lives in [SignatureStore].
 */
data class SignaturePlacement(
    val image: String,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val rotation: Float = 0f,
) {
    /** Height of the box as a fraction of the page height, for a page of [aspect] = height / width. */
    fun heightOnPage(aspect: Float): Float = h / aspect.coerceAtLeast(0.01f)

    /**
     * The image's bottom-left, bottom-right and top-left corners, normalized over the displayed page.
     * These three points fix the affine transform that draws the image (in Compose or into the PDF).
     */
    fun imageCorners(aspect: Float): Triple<NormPoint, NormPoint, NormPoint> {
        val a = aspect.coerceAtLeast(0.01f)
        // Work in units of page width, y down; page height is then [a].
        val centreX = cx
        val centreY = cy * a
        val rad = Math.toRadians(rotation.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        fun corner(dx: Float, dy: Float): NormPoint {
            // Clockwise on screen (y down) is the standard rotation formula.
            val x = centreX + dx * c - dy * s
            val y = centreY + dx * s + dy * c
            return NormPoint(x, y / a)
        }
        val hw = w / 2f
        val hh = h / 2f
        return Triple(corner(-hw, hh), corner(hw, hh), corner(-hw, -hh))
    }

    fun toAnnotation(documentId: Long, pageIndex: Int, id: Long = 0, createdAt: Long = System.currentTimeMillis()) =
        Annotation(
            id = id,
            documentId = documentId,
            pageIndex = pageIndex,
            type = AnnotationType.Signature,
            colorArgb = 0xFF15171C.toInt(),
            strokeWidth = 0f,
            points = listOf(NormPoint(cx, cy), NormPoint(w, h)),
            note = "$image@$rotation",
            createdAt = createdAt,
        )

    /**
     * Same placement, moved so the whole box, as turned, stays on a page of [pageAspect] = height /
     * width, as far as it fits; a box bigger than the page is centred on it.
     */
    fun clampedToPage(pageAspect: Float = 0f): SignaturePlacement {
        if (pageAspect <= 0f) return copy(cx = cx.coerceIn(0f, 1f), cy = cy.coerceIn(0f, 1f))
        // Half-extents of the turned box's bounding box, in page-width units.
        val rad = Math.toRadians(rotation.toDouble())
        val c = kotlin.math.abs(cos(rad).toFloat())
        val s = kotlin.math.abs(sin(rad).toFloat())
        val hw = (w * c + h * s) / 2f
        val hh = (w * s + h * c) / 2f / pageAspect
        fun fit(v: Float, half: Float) = if (half >= 0.5f) 0.5f else v.coerceIn(half, 1f - half)
        return copy(cx = fit(cx, hw), cy = fit(cy, hh))
    }

    companion object {
        const val DEFAULT_WIDTH = 0.36f
        const val MIN_WIDTH = 0.06f
        const val MAX_WIDTH = 1.2f

        fun of(annotation: Annotation): SignaturePlacement? {
            if (annotation.type != AnnotationType.Signature || annotation.points.size < 2) return null
            val note = annotation.note ?: return null
            val at = note.lastIndexOf('@')
            val image = if (at >= 0) note.substring(0, at) else note
            val rotation = if (at >= 0) note.substring(at + 1).toFloatOrNull() ?: 0f else 0f
            if (image.isBlank()) return null
            val (c, size) = annotation.points
            return SignaturePlacement(image, c.x, c.y, size.x, size.y, rotation)
        }

        /**
         * A new placement centred on [at], [DEFAULT_WIDTH] wide (narrower on landscape pages), with the
         * image's own proportions ([imageAspect] = width / height).
         */
        fun new(image: String, at: NormPoint, imageAspect: Float, pageAspect: Float): SignaturePlacement {
            val ia = imageAspect.coerceIn(0.2f, 12f)
            var w = DEFAULT_WIDTH
            // Keep it shorter than a third of the page on wide or landscape pages.
            val maxH = pageAspect.coerceAtLeast(0.1f) / 3f
            if (w / ia > maxH) w = maxH * ia
            return SignaturePlacement(image, at.x, at.y, w, w / ia).clampedToPage(pageAspect)
        }

        /** Turns a placement with its page: [degrees] clockwise (multiple of 90), [oldAspect] = height / width before. */
        fun rotateWithPage(p: SignaturePlacement, degrees: Int, oldAspect: Float): SignaturePlacement {
            val d = ((degrees % 360) + 360) % 360
            if (d == 0) return p
            val quarter = d == 90 || d == 270
            // Width fractions are relative to page width, which becomes the old height on a quarter turn.
            val scale = if (quarter) 1f / oldAspect.coerceAtLeast(0.01f) else 1f
            val (x, y) = when (d) {
                90 -> (1f - p.cy) to p.cx
                180 -> (1f - p.cx) to (1f - p.cy)
                else -> p.cy to (1f - p.cx)
            }
            return p.copy(cx = x, cy = y, w = p.w * scale, h = p.h * scale, rotation = (p.rotation + d) % 360f)
        }
    }
}
