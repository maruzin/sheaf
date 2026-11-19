package com.sheaf.feature.reader.review

import com.sheaf.core.domain.model.NormPoint
import com.sheaf.feature.reader.export.OfficeWriters
import com.sheaf.feature.reader.signature.SignatureGestures
import com.sheaf.feature.reader.signature.SignatureGestures.Nudge
import com.sheaf.feature.reader.signature.SignaturePlacement
import com.sheaf.feature.reader.signature.SignatureStrokes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Regression tests for the 1.2.0 review findings (rotated clamping, stroke cap, cell limit, nudges, hit-testing). */
class ReviewFixesTest {

    private val a4 = 1.4142f

    /** Bounding box of the turned box, normalized over the page (minX, maxX, minY, maxY). */
    private fun bounds(p: SignaturePlacement, aspect: Float): FloatArray {
        val rad = Math.toRadians(p.rotation.toDouble())
        val ex = (p.w * abs(cos(rad)) + p.h * abs(sin(rad))).toFloat() / 2f
        val ey = (p.w * abs(sin(rad)) + p.h * abs(cos(rad))).toFloat() / 2f / aspect
        return floatArrayOf(p.cx - ex, p.cx + ex, p.cy - ey, p.cy + ey)
    }

    @Test
    fun `a turned signature dragged to a corner stays fully on the page`() {
        val start = SignaturePlacement("sig_1.png", 0.5f, 0.5f, 0.36f, 0.12f, rotation = 90f)
        for ((dx, dy) in listOf(-5000f to -5000f, 5000f to 5000f, -5000f to 5000f, 5000f to -5000f)) {
            val moved = SignatureGestures.move(start, dx, dy, 1000f, 1414.2f)
            val b = bounds(moved, a4)
            assertTrue("left ${b[0]}", b[0] >= -1e-4f)
            assertTrue("right ${b[1]}", b[1] <= 1f + 1e-4f)
            assertTrue("top ${b[2]}", b[2] >= -1e-4f)
            assertTrue("bottom ${b[3]}", b[3] <= 1f + 1e-4f)
        }
    }

    @Test
    fun `a turned signature can reach the side edge (clamp uses its turned width)`() {
        val p = SignaturePlacement("sig_1.png", 0f, 0.5f, 0.36f, 0.12f, rotation = 90f).clampedToPage(a4)
        // Turned 90 degrees, its horizontal half-extent is h/2 = 0.06, not w/2 = 0.18.
        assertEquals(0.06f, p.cx, 1e-4f)
    }

    @Test
    fun `a very wide drawn signature is scaled down whole, never cropped`() {
        val stroke = listOf(NormPoint(0.0f, 0.5f), NormPoint(1.0f, 0.52f))
        val layout = SignatureStrokes.layout(listOf(stroke), padW = 3000f, padH = 200f, lineWidthPx = 4f)!!
        assertTrue(layout.width <= 4000)
        val maxX = layout.strokes.flatten().maxOf { it.first }
        assertTrue("ink at $maxX fits in ${layout.width}", maxX <= layout.width)
        assertTrue(layout.height < SignatureStrokes.OUTPUT_HEIGHT)
    }

    @Test
    fun `excel cells are capped at the 32767 character limit`() {
        val out = ByteArrayOutputStream()
        OfficeWriters.writeXlsx(listOf("x".repeat(40_000)), "Long", out)
        val sheet = ZipInputStream(out.toByteArray().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "xl/worksheets/sheet1.xml" }
            zip.readBytes().toString(Charsets.UTF_8)
        }
        val longest = Regex("<t xml:space=\"preserve\">([^<]*)</t>").findAll(sheet).maxOf { it.groupValues[1].length }
        assertTrue("longest cell $longest", longest <= OfficeWriters.MAX_CELL_CHARS)
        // Page numbers are numeric cells.
        assertTrue(sheet.contains("<c r=\"A2\"><v>1</v></c>"))
    }

    @Test
    fun `nudges move, resize and turn one step and stay on the page`() {
        val p = SignaturePlacement("sig_1.png", 0.5f, 0.5f, 0.3f, 0.1f)
        assertEquals(0.48f, SignatureGestures.nudge(p, Nudge.Left, a4).cx, 1e-4f)
        assertEquals(0.52f, SignatureGestures.nudge(p, Nudge.Down, a4).cy, 1e-4f)
        val bigger = SignatureGestures.nudge(p, Nudge.Bigger, a4)
        assertEquals(0.33f, bigger.w, 1e-4f)
        assertEquals(p.w / p.h, bigger.w / bigger.h, 1e-3f)
        assertEquals(345f, SignatureGestures.nudge(p, Nudge.TurnLeft, a4).rotation, 1e-3f)
        assertEquals(15f, SignatureGestures.nudge(p, Nudge.TurnRight, a4).rotation, 1e-3f)
        var edge = p
        repeat(100) { edge = SignatureGestures.nudge(edge, Nudge.Right, a4) }
        assertTrue(edge.cx + edge.w / 2 <= 1f + 1e-4f)
    }

    @Test
    fun `on a tiny signature the nearest handle wins, and delete needs a close tap`() {
        val p = SignaturePlacement("sig_1.png", 0.5f, 0.5f, 0.06f, 0.02f) // 60 x 20 px on a 1000 px page
        val list = listOf(1L to p)
        // Near the bottom-right corner (resize), inside the delete zone's wider touch radius.
        val r = SignatureGestures.hitTest(list, 1L, 528f, 718f, 1000f, 1414f, handleRadius = 20f, minTouchRadius = 48f)
        assertEquals(SignatureGestures.Part.Resize, r?.part)
        // 40 px from the delete corner: beyond delete's own reach (28 px), so not a delete.
        val far = SignatureGestures.hitTest(list, 1L, 470f - 28f, 697f - 28f, 1000f, 1414f, handleRadius = 20f, minTouchRadius = 48f)
        assertTrue(far?.part != SignatureGestures.Part.Delete)
    }

    @Test
    fun `stamps are sized for form boxes, not signature lines`() {
        val tick = SignaturePlacement.new("sig_1.png", NormPoint(0.5f, 0.5f), 1f, a4, SignaturePlacement.STAMP_MARK_HEIGHT)
        assertEquals(SignaturePlacement.STAMP_MARK_HEIGHT, tick.h, 1e-4f)
        assertEquals(tick.w, tick.h, 1e-4f)
        val date = SignaturePlacement.new("sig_2.png", NormPoint(0.5f, 0.5f), 5.5f, a4, SignaturePlacement.STAMP_TEXT_HEIGHT)
        assertEquals(SignaturePlacement.STAMP_TEXT_HEIGHT, date.h, 1e-4f)
        assertTrue(date.w < SignaturePlacement.DEFAULT_WIDTH)
        // A tick resized smaller stays usable (no jump to a bigger minimum).
        val smaller = SignatureGestures.nudge(tick, Nudge.Smaller, a4)
        assertTrue(smaller.w < tick.w)
    }
}
