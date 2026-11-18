package com.sheaf.feature.reader.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage

/**
 * Maps between the page as the user sees it and PDF user space.
 *
 * "Display" coordinates are normalized 0..1 with a top-left origin over the page *as rendered*:
 * the crop box, turned by the page's /Rotate (clockwise). That is what both PdfRenderer and PdfBox's
 * renderer draw, and what annotations, form overlays and OCR bitmaps are measured against.
 * PDF user space is the unrotated page, in points, with a bottom-left origin.
 *
 * Mapping only through the media box (as before) put markup in the wrong place on any rotated or
 * cropped page, e.g. a signature on a landscape scan landed sideways and off-target.
 */
data class PageFrame(
    val x0: Float,
    val y0: Float,
    val width: Float,
    val height: Float,
    /** Clockwise rotation: 0, 90, 180 or 270. */
    val rotation: Int,
) {
    /** Width of the page as displayed, in points (crop box width or height, depending on rotation). */
    val displayWidth: Float get() = if (rotation == 90 || rotation == 270) height else width
    val displayHeight: Float get() = if (rotation == 90 || rotation == 270) width else height

    /** Display (u, v) → PDF user-space (x, y). */
    fun toPdf(u: Float, v: Float): Pair<Float, Float> {
        // (a, b): normalized over the unrotated crop box, top-left origin.
        val (a, b) = when (rotation) {
            90 -> v to 1f - u
            180 -> 1f - u to 1f - v
            270 -> 1f - v to u
            else -> u to v
        }
        return (x0 + a * width) to (y0 + (1f - b) * height)
    }

    /** PDF user-space (x, y) → display (u, v). Inverse of [toPdf]. */
    fun toDisplay(x: Float, y: Float): Pair<Float, Float> {
        val a = (x - x0) / width
        val b = 1f - (y - y0) / height
        return when (rotation) {
            90 -> 1f - b to a
            180 -> 1f - a to 1f - b
            270 -> b to 1f - a
            else -> a to b
        }
    }

    /**
     * A PDF rectangle (lower-left x/y, upper-right x/y) → display rectangle (left, top, width, height).
     */
    fun rectToDisplay(llx: Float, lly: Float, urx: Float, ury: Float): FloatArray {
        val (u1, v1) = toDisplay(llx, lly)
        val (u2, v2) = toDisplay(urx, ury)
        val left = minOf(u1, u2)
        val top = minOf(v1, v2)
        return floatArrayOf(left, top, maxOf(u1, u2) - left, maxOf(v1, v2) - top)
    }

    companion object {
        fun of(page: PDPage): PageFrame {
            val box = page.cropBox
            val r = ((page.rotation % 360) + 360) % 360
            return PageFrame(
                x0 = box.lowerLeftX,
                y0 = box.lowerLeftY,
                width = box.width,
                height = box.height,
                rotation = if (r % 90 == 0) r else 0,
            )
        }
    }
}
