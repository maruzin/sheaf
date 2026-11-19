package com.sheaf.feature.reader.signature

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hit-testing and drag maths for placed signatures, in page pixels (y down). Pure, so it is
 * unit-tested; the reader's gesture loop only feeds it pointer positions.
 *
 * A selected signature shows three handles: delete (top-left), resize (bottom-right, keeps the
 * proportions) and rotate (a knob above the top edge). Delete and resize sit on opposite corners so a
 * small signature's handles never crowd each other.
 */
object SignatureGestures {

    enum class Part { Delete, Resize, Rotate, Body }

    data class Hit(val id: Long, val part: Part)

    /** Pixel geometry of one placement on a page [pageW] x [pageH] pixels. */
    data class Frame(val cx: Float, val cy: Float, val hw: Float, val hh: Float, val rotation: Float) {
        /** [x], [y] in the box's own unrotated coordinates, origin at its centre. */
        fun toLocal(x: Float, y: Float): Pair<Float, Float> {
            val rad = Math.toRadians(-rotation.toDouble())
            val dx = x - cx
            val dy = y - cy
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            return (dx * c - dy * s) to (dx * s + dy * c)
        }

        fun toPage(lx: Float, ly: Float): Pair<Float, Float> {
            val rad = Math.toRadians(rotation.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            return (cx + lx * c - ly * s) to (cy + lx * s + ly * c)
        }
    }

    fun frame(p: SignaturePlacement, pageW: Float, pageH: Float) =
        Frame(p.cx * pageW, p.cy * pageH, p.w * pageW / 2f, p.h * pageW / 2f, p.rotation)

    /** Distance of the rotate knob above the box's top edge, in pixels. */
    fun rotateKnobOffset(handleRadius: Float) = handleRadius * 2.2f

    /**
     * What the finger at ([x], [y]) touches. Handles of the [selectedId] placement win; otherwise the
     * top-most box (last in [placements]) under the finger; null for empty page.
     */
    fun hitTest(
        placements: List<Pair<Long, SignaturePlacement>>,
        selectedId: Long?,
        x: Float,
        y: Float,
        pageW: Float,
        pageH: Float,
        handleRadius: Float,
    ): Hit? {
        placements.firstOrNull { it.first == selectedId }?.let { (id, p) ->
            val f = frame(p, pageW, pageH)
            val (lx, ly) = f.toLocal(x, y)
            fun near(hx: Float, hy: Float) = hypot(lx - hx, ly - hy) <= handleRadius * 1.4f
            if (near(-f.hw, -f.hh)) return Hit(id, Part.Delete)
            if (near(f.hw, f.hh)) return Hit(id, Part.Resize)
            if (near(0f, -f.hh - rotateKnobOffset(handleRadius))) return Hit(id, Part.Rotate)
        }
        for ((id, p) in placements.asReversed()) {
            val f = frame(p, pageW, pageH)
            val (lx, ly) = f.toLocal(x, y)
            // A little slack so thin signatures are still easy to grab.
            val slack = handleRadius * 0.5f
            if (lx in -f.hw - slack..f.hw + slack && ly in -f.hh - slack..f.hh + slack) return Hit(id, Part.Body)
        }
        return null
    }

    /** [start] moved by the finger travelling ([dx], [dy]) pixels. */
    fun move(start: SignaturePlacement, dx: Float, dy: Float, pageW: Float, pageH: Float): SignaturePlacement =
        start.copy(cx = start.cx + dx / pageW, cy = start.cy + dy / pageH).clampedToPage(pageH / pageW)

    /** [start] scaled so its bottom-right corner follows the finger at ([x], [y]); proportions kept. */
    fun resize(start: SignaturePlacement, x: Float, y: Float, pageW: Float, pageH: Float): SignaturePlacement {
        val f = frame(start, pageW, pageH)
        val (lx, ly) = f.toLocal(x, y)
        val original = hypot(f.hw, f.hh).coerceAtLeast(1f)
        // Project onto the corner direction so the box only grows/shrinks, never skews.
        val along = (lx * f.hw + ly * f.hh) / original
        val scale = (along / original).coerceAtLeast(0.01f)
        val w = (start.w * scale).coerceIn(SignaturePlacement.MIN_WIDTH, SignaturePlacement.MAX_WIDTH)
        return start.copy(w = w, h = start.h * (w / start.w)).clampedToPage(pageH / pageW)
    }

    /** [start] turned so its rotate knob points at the finger; snaps to the nearest quarter turn within 4°. */
    fun rotate(start: SignaturePlacement, x: Float, y: Float, pageW: Float, pageH: Float): SignaturePlacement {
        val f = frame(start, pageW, pageH)
        // The knob sits straight "up" from the centre at rotation 0.
        var deg = Math.toDegrees(atan2((y - f.cy).toDouble(), (x - f.cx).toDouble())).toFloat() + 90f
        deg = ((deg % 360f) + 360f) % 360f
        val nearest = (Math.round(deg / 90f) * 90f) % 360f
        val diff = kotlin.math.abs(deg - nearest).let { if (it > 180f) 360f - it else it }
        if (diff <= 4f) deg = nearest
        return start.copy(rotation = deg)
    }
}
