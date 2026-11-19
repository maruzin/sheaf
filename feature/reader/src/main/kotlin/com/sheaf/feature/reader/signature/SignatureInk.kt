package com.sheaf.feature.reader.signature

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Turns a photo of a signature on paper into clean ink on a transparent background.
 *
 * 1. Evens out lighting: each pixel's brightness is divided by the local paper brightness (a blurred
 *    grid of block maxima), so shadows and phone-camera vignetting don't read as ink.
 * 2. Otsu's threshold on the evened-out image separates ink from paper.
 * 3. Ink gets one colour (the average of the confident ink pixels, so blue pen stays blue), with a
 *    soft alpha ramp at the edges so strokes stay smooth.
 * 4. The result is cropped to the ink with a small margin.
 *
 * Works on raw ARGB ints so it runs (and is tested) without Android.
 */
object SignatureInk {

    data class Image(val pixels: IntArray, val width: Int, val height: Int) {
        override fun equals(other: Any?) = other is Image && width == other.width && height == other.height &&
            pixels.contentEquals(other.pixels)
        override fun hashCode() = 31 * (31 * width + height) + pixels.contentHashCode()
    }

    sealed interface Result {
        data class Ok(val image: Image) : Result
        /** Nothing that looks like pen strokes (blank paper, or too dark/busy a photo). */
        data object NoInk : Result
    }

    fun extract(argb: IntArray, width: Int, height: Int): Result {
        require(argb.size == width * height) { "pixel count mismatch" }
        if (width < 8 || height < 8) return Result.NoInk
        // One float buffer, evened out in place, keeps peak memory low on large photos.
        val norm = FloatArray(argb.size) { i -> luminance(argb[i]) }
        run {
            val paper = paperBrightness(norm, width, height)
            for (i in norm.indices) norm[i] = (norm[i] / paper[i].coerceAtLeast(1f) * 255f).coerceIn(0f, 255f)
        }

        val t = otsu(norm)
        // Ink and paper must differ clearly, or this is not a photo of pen on paper.
        var inkSum = 0.0; var inkN = 0; var paperSum = 0.0; var paperN = 0
        for (v in norm) if (v <= t) { inkSum += v; inkN++ } else { paperSum += v; paperN++ }
        if (inkN == 0 || paperN == 0 || paperSum / paperN - inkSum / inkN < 50.0) return Result.NoInk
        // Ink must be clearly darker than paper; the ramp gives anti-aliased edges.
        val solid = t * 0.72f
        val alpha = IntArray(norm.size)
        var inkCount = 0
        var r = 0L; var g = 0L; var b = 0L; var colourSamples = 0
        for (i in norm.indices) {
            val v = norm[i]
            val a = when {
                v <= solid -> 255
                v >= t -> 0
                else -> ((t - v) / (t - solid) * 255f).roundToInt()
            }
            alpha[i] = a
            if (a >= 128) inkCount++
            if (a == 255) {
                val p = argb[i]
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                colourSamples++
            }
        }
        val fraction = inkCount.toFloat() / norm.size
        // A signature covers a little of the frame. Nearly nothing = blank; a lot = not a clean shot.
        if (inkCount < 30 || fraction > 0.35f) return Result.NoInk

        val inkColour = if (colourSamples > 0) {
            darken((r / colourSamples).toInt(), (g / colourSamples).toInt(), (b / colourSamples).toInt())
        } else {
            0x15171C
        }

        // Crop to the ink (alpha >= 64) with a 4% margin.
        var minX = width; var minY = height; var maxX = -1; var maxY = -1
        for (y in 0 until height) for (x in 0 until width) {
            if (alpha[y * width + x] >= 64) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return Result.NoInk
        val pad = (max(maxX - minX, maxY - minY) * 0.04f).roundToInt() + 2
        minX = max(0, minX - pad); minY = max(0, minY - pad)
        maxX = min(width - 1, maxX + pad); maxY = min(height - 1, maxY + pad)
        val cw = maxX - minX + 1
        val ch = maxY - minY + 1
        val out = IntArray(cw * ch)
        for (y in 0 until ch) for (x in 0 until cw) {
            val a = alpha[(y + minY) * width + (x + minX)]
            out[y * cw + x] = (a shl 24) or inkColour
        }
        return Result.Ok(Image(out, cw, ch))
    }

    internal fun luminance(p: Int): Float {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Local paper brightness: max per block, smoothed over neighbouring blocks, bilinear per pixel. */
    private fun paperBrightness(lum: FloatArray, w: Int, h: Int): FloatArray {
        val block = max(8, min(w, h) / 12)
        val bw = (w + block - 1) / block
        val bh = (h + block - 1) / block
        val maxes = FloatArray(bw * bh)
        for (y in 0 until h) for (x in 0 until w) {
            val k = (y / block) * bw + x / block
            if (lum[y * w + x] > maxes[k]) maxes[k] = lum[y * w + x]
        }
        // Max over the 3x3 neighbourhood bridges blocks fully covered by a thick stroke.
        val smooth = FloatArray(maxes.size)
        for (by in 0 until bh) for (bx in 0 until bw) {
            var m = 0f
            for (dy in -1..1) for (dx in -1..1) {
                val nx = bx + dx; val ny = by + dy
                if (nx in 0 until bw && ny in 0 until bh) m = max(m, maxes[ny * bw + nx])
            }
            smooth[by * bw + bx] = m
        }
        val out = FloatArray(lum.size)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / block - 0.5f).coerceIn(0f, (bh - 1).toFloat())
            val y0 = fy.toInt(); val y1 = min(y0 + 1, bh - 1); val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / block - 0.5f).coerceIn(0f, (bw - 1).toFloat())
                val x0 = fx.toInt(); val x1 = min(x0 + 1, bw - 1); val tx = fx - x0
                val top = smooth[y0 * bw + x0] * (1 - tx) + smooth[y0 * bw + x1] * tx
                val bottom = smooth[y1 * bw + x0] * (1 - tx) + smooth[y1 * bw + x1] * tx
                out[y * w + x] = top * (1 - ty) + bottom * ty
            }
        }
        return out
    }

    /** Otsu's method over 256 bins. */
    internal fun otsu(values: FloatArray): Float {
        val hist = IntArray(256)
        values.forEach { hist[it.roundToInt().coerceIn(0, 255)]++ }
        val total = values.size.toDouble()
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0.0; var best = 0.0
        var low = 127; var high = 127
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF == 0.0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            when {
                between > best * (1 + 1e-9) -> { best = between; low = i; high = i }
                between >= best * (1 - 1e-9) && best > 0 -> high = i
            }
        }
        // With empty bins between the classes every split there ties: take the middle of the gap,
        // not its dark edge (which would class the ink itself as paper).
        return (low + high) / 2f
    }

    /** Pen colour, pushed darker so a faint photo still prints as a strong line. */
    private fun darken(r: Int, g: Int, b: Int): Int {
        val f = 0.75f
        return ((r * f).roundToInt() shl 16) or ((g * f).roundToInt() shl 8) or (b * f).roundToInt()
    }
}
