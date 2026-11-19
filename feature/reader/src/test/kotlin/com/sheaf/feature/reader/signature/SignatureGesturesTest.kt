package com.sheaf.feature.reader.signature

import com.sheaf.feature.reader.signature.SignatureGestures.Part
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignatureGesturesTest {
    // Page 1000 x 1400 px. Box centred at (500, 700), 400 x 100 px.
    private val w = 1000f
    private val h = 1400f
    private val p = SignaturePlacement("s", 0.5f, 0.5f, 0.4f, 0.1f)
    private val r = 20f

    private fun hit(x: Float, y: Float, selected: Long? = 1L) =
        SignatureGestures.hitTest(listOf(1L to p), selected, x, y, w, h, r)

    @Test
    fun `body, handles and empty page`() {
        assertEquals(Part.Body, hit(500f, 700f)?.part)
        assertEquals(Part.Resize, hit(700f, 750f)?.part)
        assertEquals(Part.Delete, hit(300f, 650f)?.part)
        assertEquals(Part.Body, hit(700f, 650f)?.part) // top-right is plain body now
        assertEquals(Part.Rotate, hit(500f, 650f - SignatureGestures.rotateKnobOffset(r))?.part)
        assertNull(hit(100f, 100f))
    }

    @Test
    fun `handles only exist on the selected signature`() {
        assertNull(hit(500f, 650f - SignatureGestures.rotateKnobOffset(r), selected = null))
        assertEquals(Part.Body, hit(700f, 750f, selected = null)?.part) // corner is still inside the body slack
    }

    @Test
    fun `hit testing follows the box when it is turned`() {
        val turned = p.copy(rotation = 90f)
        // Turned 90°, the box is 100 wide and 400 tall around the centre.
        assertEquals(Part.Body, SignatureGestures.hitTest(listOf(1L to turned), null, 500f, 880f, w, h, r)?.part)
        assertNull(SignatureGestures.hitTest(listOf(1L to turned), null, 680f, 700f, w, h, r))
    }

    @Test
    fun `moving clamps the centre to the page`() {
        val moved = SignatureGestures.move(p, 100f, -140f, w, h)
        assertEquals(0.6f, moved.cx, 0.0001f)
        assertEquals(0.4f, moved.cy, 0.0001f)
        assertEquals(0.8f, SignatureGestures.move(p, 5000f, 0f, w, h).cx, 0.0001f) // box edge stops at the page edge
    }

    @Test
    fun `resizing keeps proportions and limits`() {
        // Drag the corner from (700, 750) to (900, 800): twice as far from the centre along the diagonal.
        val bigger = SignatureGestures.resize(p, 900f, 800f, w, h)
        assertEquals(0.8f, bigger.w, 0.0001f)
        assertEquals(bigger.w / 4f, bigger.h, 0.0001f)
        val tiny = SignatureGestures.resize(p, 500f, 700f, w, h)
        assertEquals(SignaturePlacement.MIN_WIDTH, tiny.w, 0.0001f)
    }

    @Test
    fun `rotating follows the finger and snaps to quarter turns`() {
        // Finger straight right of the centre = 90° clockwise.
        assertEquals(90f, SignatureGestures.rotate(p, 900f, 700f, w, h).rotation, 0.001f)
        // 3° off straight up snaps to 0.
        val nearlyUp = SignatureGestures.rotate(p, 500f + 10.5f, 500f, w, h)
        assertEquals(0f, nearlyUp.rotation, 0.001f)
        // 45° stays 45°.
        assertEquals(45f, SignatureGestures.rotate(p, 600f, 600f, w, h).rotation, 0.01f)
    }
}
