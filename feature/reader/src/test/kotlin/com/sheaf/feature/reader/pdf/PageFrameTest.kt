package com.sheaf.feature.reader.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PageFrameTest {

    // A cropped A4-ish page: crop box starts at (10, 20), 600 x 800 points.
    private fun frame(rotation: Int) = PageFrame(10f, 20f, 600f, 800f, rotation)

    private fun assertPoint(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
        assertEquals(expected.first, actual.first, 0.001f)
        assertEquals(expected.second, actual.second, 0.001f)
    }

    @Test
    fun `unrotated page maps through the crop box with a flipped y axis`() {
        val f = frame(0)
        assertPoint(10f to 820f, f.toPdf(0f, 0f)) // display top-left = crop top-left
        assertPoint(610f to 20f, f.toPdf(1f, 1f))
        assertPoint(310f to 420f, f.toPdf(0.5f, 0.5f))
    }

    @Test
    fun `rotated pages put the display top-left where the viewer sees it`() {
        // /Rotate 90 (clockwise): the PDF's left edge is shown on top, so display top-left is PDF bottom-left.
        assertPoint(10f to 20f, frame(90).toPdf(0f, 0f))
        // /Rotate 180: display top-left is PDF bottom-right.
        assertPoint(610f to 20f, frame(180).toPdf(0f, 0f))
        // /Rotate 270: display top-left is PDF top-right.
        assertPoint(610f to 820f, frame(270).toPdf(0f, 0f))
    }

    @Test
    fun `toDisplay inverts toPdf for every rotation`() {
        for (r in listOf(0, 90, 180, 270)) {
            val f = frame(r)
            for ((u, v) in listOf(0.1f to 0.2f, 0.9f to 0.05f, 0.5f to 0.5f, 0f to 1f)) {
                val (x, y) = f.toPdf(u, v)
                assertPoint(u to v, f.toDisplay(x, y))
            }
        }
    }

    @Test
    fun `display size swaps on quarter turns`() {
        assertEquals(600f, frame(0).displayWidth)
        assertEquals(800f, frame(90).displayWidth)
        assertEquals(600f, frame(270).displayHeight)
    }

    @Test
    fun `a form box on a rotated page becomes the right display rectangle`() {
        // Field across the bottom-left of the unrotated crop box: x 10..310, y 20..120.
        val r0 = frame(0).rectToDisplay(10f, 20f, 310f, 120f)
        assertArrayEquals(floatArrayOf(0f, 0.875f, 0.5f, 0.125f), r0, 0.001f)
        // Rotated 90 clockwise, the PDF's bottom-left corner is shown at the top-left.
        val r90 = frame(90).rectToDisplay(10f, 20f, 310f, 120f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0.125f, 0.5f), r90, 0.001f)
    }
}
