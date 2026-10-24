package com.sheaf.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomMathTest {

    @Test
    fun `zoom is clamped to 1x - 5x`() {
        assertEquals(1f, ZoomMath.clamp(0.2f))
        assertEquals(5f, ZoomMath.clamp(9f))
        assertEquals(2.5f, ZoomMath.clamp(2.5f))
    }

    @Test
    fun `focal scroll keeps the point under the fingers fixed`() {
        // Point at viewport x=100 with scroll 0 sits at content x=100. After 2x it is at 200, so
        // scroll must be 100 to keep it at viewport x=100.
        assertEquals(100f, ZoomMath.focalScroll(scroll = 0f, focus = 100f, factor = 2f))
        assertEquals(450f, ZoomMath.focalScroll(scroll = 200f, focus = 50f, factor = 2f))
        // Zooming out never produces a negative scroll.
        assertEquals(0f, ZoomMath.focalScroll(scroll = 10f, focus = 50f, factor = 0.2f))
    }

    @Test
    fun `render width snaps to quarter steps and is capped`() {
        assertEquals(1080, ZoomMath.renderWidth(1080, 1f))
        assertEquals(ZoomMath.renderWidth(800, 1.30f), ZoomMath.renderWidth(800, 1.20f)) // both snap to 1.25
        assertEquals(ZoomMath.MAX_RENDER_WIDTH_PX, ZoomMath.renderWidth(1080, 5f))
    }

    @Test
    fun `double tap zooms in from fit width and back out otherwise`() {
        assertEquals(ZoomMath.DOUBLE_TAP_ZOOM, ZoomMath.doubleTapTarget(1f))
        assertEquals(1f, ZoomMath.doubleTapTarget(2.5f))
        assertEquals(1f, ZoomMath.doubleTapTarget(4f))
    }
}
