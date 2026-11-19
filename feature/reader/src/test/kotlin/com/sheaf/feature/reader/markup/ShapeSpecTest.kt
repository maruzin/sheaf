package com.sheaf.feature.reader.markup

import com.sheaf.core.domain.model.NormPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeSpecTest {
    private val a4 = 1.4142f

    @Test
    fun `a shape round-trips through an annotation`() {
        val s = ShapeSpec(ShapeKind.Ellipse, NormPoint(0.1f, 0.2f), NormPoint(0.4f, 0.3f), 0xFFC5221F.toInt(), 0.004f)
        assertEquals(s, ShapeSpec.of(s.toAnnotation(1, 0)))
    }

    @Test
    fun `nearly level lines snap level, for underline and strikethrough`() {
        val s = ShapeSpec.fromDrag(ShapeKind.Line, NormPoint(0.1f, 0.5f), NormPoint(0.6f, 0.505f), 0, a4)!!
        assertEquals(s.start.y, s.end.y, 1e-6f)
        val v = ShapeSpec.fromDrag(ShapeKind.Arrow, NormPoint(0.5f, 0.1f), NormPoint(0.503f, 0.5f), 0, a4)!!
        assertEquals(v.start.x, v.end.x, 1e-6f)
        val d = ShapeSpec.fromDrag(ShapeKind.Line, NormPoint(0.1f, 0.1f), NormPoint(0.4f, 0.3f), 0, a4)!!
        assertTrue(d.end.y != d.start.y)
    }

    @Test
    fun `a tap is not a shape`() {
        assertNull(ShapeSpec.fromDrag(ShapeKind.Rectangle, NormPoint(0.5f, 0.5f), NormPoint(0.505f, 0.503f), 0, a4))
    }

    @Test
    fun `white-out is white and redaction black, whatever ink is chosen`() {
        val red = 0xFFC5221F.toInt()
        assertEquals(0xFFFFFFFF.toInt(), ShapeSpec(ShapeKind.WhiteOut, NormPoint(0f, 0f), NormPoint(1f, 1f), red, 0f).drawColor)
        assertEquals(0xFF000000.toInt(), ShapeSpec(ShapeKind.Redact, NormPoint(0f, 0f), NormPoint(1f, 1f), red, 0f).drawColor)
        assertEquals(red, ShapeSpec(ShapeKind.Rectangle, NormPoint(0f, 0f), NormPoint(1f, 1f), red, 0f).drawColor)
    }

    @Test
    fun `arrowhead points back from the tip`() {
        val s = ShapeSpec(ShapeKind.Arrow, NormPoint(0.1f, 0.5f), NormPoint(0.9f, 0.5f), 0, 0.004f)
        val head = s.arrowHead(1000f, 1414f)
        assertEquals(2, head.size)
        assertTrue(head.all { it.first < 900f })
        assertTrue(head[0].second != head[1].second)
    }
}
