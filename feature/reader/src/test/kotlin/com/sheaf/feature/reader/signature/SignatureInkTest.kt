package com.sheaf.feature.reader.signature

import com.sheaf.core.domain.model.NormPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureInkTest {

    /** Paper with a lighting gradient (bright left, shadowed right) and a dark ink bar at rows 40..47, cols 50..149. */
    private fun photo(ink: Int = 0x202028, w: Int = 200, h: Int = 100): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val paper = (245 - x * 0.4f).toInt() // 245 → ~165: a shadow darker than ink contrast would suggest
        val inBar = y in 40..47 && x in 50..149
        if (inBar) (0xFF shl 24) or ink else (0xFF shl 24) or (paper shl 16) or (paper shl 8) or paper
    }

    @Test
    fun `cuts the ink out of shaded paper and crops to it`() {
        val r = SignatureInk.extract(photo(), 200, 100)
        assertTrue(r is SignatureInk.Result.Ok)
        val img = (r as SignatureInk.Result.Ok).image
        // Cropped to the bar plus a small margin, not the whole frame (the shadow isn't ink).
        assertTrue("width ${img.width}", img.width in 100..120)
        assertTrue("height ${img.height}", img.height in 8..20)
        val centre = img.pixels[(img.height / 2) * img.width + img.width / 2]
        assertEquals(255, centre ushr 24)
        val corner = img.pixels[0]
        assertEquals(0, corner ushr 24)
    }

    @Test
    fun `blue pen stays blue`() {
        val r = SignatureInk.extract(photo(ink = 0x1830B0), 200, 100) as SignatureInk.Result.Ok
        val p = r.image.pixels[(r.image.height / 2) * r.image.width + r.image.width / 2]
        val red = (p shr 16) and 0xFF
        val blue = p and 0xFF
        assertTrue("blue $blue > red $red", blue > red * 2)
    }

    @Test
    fun `blank paper or a black frame has no signature`() {
        val blank = IntArray(200 * 100) { 0xFFF2F2F2.toInt() }
        assertEquals(SignatureInk.Result.NoInk, SignatureInk.extract(blank, 200, 100))
        val dark = IntArray(200 * 100) { i -> if (i % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertEquals(SignatureInk.Result.NoInk, SignatureInk.extract(dark, 200, 100))
    }

    @Test
    fun `drawn strokes keep every stroke in the saved layout`() {
        val strokes = listOf(
            listOf(NormPoint(0.1f, 0.5f), NormPoint(0.4f, 0.6f)),
            listOf(NormPoint(0.6f, 0.3f), NormPoint(0.9f, 0.4f)),
            listOf(NormPoint(0.5f, 0.2f)), // the dot on an "i"
        )
        val layout = SignatureStrokes.layout(strokes, 1000f, 400f, 8f)!!
        assertEquals(3, layout.strokes.size)
        assertEquals(SignatureStrokes.OUTPUT_HEIGHT, layout.height)
        // Wider than tall: spans 0.1..0.9 of a 1000 px pad vs 0.2..0.6 of 400 px.
        assertTrue(layout.width > layout.height)
        layout.strokes.flatten().forEach { (x, y) ->
            assertTrue(x in 0f..layout.width.toFloat())
            assertTrue(y in 0f..layout.height.toFloat())
        }
    }

    @Test
    fun `legacy single-stroke signatures still decode`() {
        assertEquals(listOf(NormPoint(0.1f, 0.2f), NormPoint(0.3f, 0.4f)), SignatureStrokes.decodeLegacy("0.1,0.2;0.3,0.4"))
        assertTrue(SignatureStrokes.decodeLegacy("png:sig_1.png").isEmpty())
        assertEquals(listOf("sig_1_2.png", "sig_3.png"), SignatureStore.decodeList("png:sig_1_2.png|../evil.png|sig_3.png"))
    }
}
