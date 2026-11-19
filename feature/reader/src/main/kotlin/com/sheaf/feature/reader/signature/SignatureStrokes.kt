package com.sheaf.feature.reader.signature

import com.sheaf.core.domain.model.NormPoint
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The layout for rendering hand-drawn strokes into a signature image: every stroke kept, the image
 * cropped to the ink with a margin, at a fixed output height so all signatures print crisply.
 */
object SignatureStrokes {

    /** Output layout: strokes in output pixels, image size, line width in output pixels. */
    data class Layout(val strokes: List<List<Pair<Float, Float>>>, val width: Int, val height: Int, val lineWidth: Float)

    const val OUTPUT_HEIGHT = 360

    /**
     * [strokes] are normalized over a pad [padW] x [padH] pixels wide, drawn with a line [lineWidthPx]
     * wide. Returns null when there is nothing to save.
     */
    fun layout(strokes: List<List<NormPoint>>, padW: Float, padH: Float, lineWidthPx: Float): Layout? {
        val drawn = strokes.filter { it.isNotEmpty() }
        if (drawn.isEmpty() || padW <= 0f || padH <= 0f) return null
        val px = drawn.map { s -> s.map { it.x * padW to it.y * padH } }
        val all = px.flatten()
        var minX = all.minOf { it.first }; var maxX = all.maxOf { it.first }
        var minY = all.minOf { it.second }; var maxY = all.maxOf { it.second }
        // A dot or a straight line still gets a sensible box.
        val margin = lineWidthPx * 2f
        minX -= margin; maxX += margin; minY -= margin; maxY += margin
        val boxH = max(maxY - minY, lineWidthPx * 8f)
        val boxW = max(maxX - minX, lineWidthPx * 8f)
        val scale = OUTPUT_HEIGHT / boxH
        val outW = (boxW * scale).roundToInt().coerceIn(16, 4000)
        val out = px.map { s -> s.map { (x, y) -> (x - minX) * scale to (y - minY) * scale } }
        return Layout(out, outW, OUTPUT_HEIGHT, (lineWidthPx * scale).coerceAtLeast(2f))
    }

    /** Legacy signatures (before 1.2) were one polyline saved as "x,y;x,y" over a 2.5:1 box. */
    fun decodeLegacy(s: String): List<NormPoint> =
        if (s.isBlank() || s.startsWith(SignatureStore.PREFIX)) emptyList() else s.split(";").mapNotNull { p ->
            val c = p.split(",")
            val x = c.getOrNull(0)?.toFloatOrNull()
            val y = c.getOrNull(1)?.toFloatOrNull()
            if (c.size == 2 && x != null && y != null) NormPoint(x, y) else null
        }
}
