package com.sheaf.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.roundToInt

/** Pure zoom rules for the page list (unit-tested). */
internal object ZoomMath {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 5f
    const val DOUBLE_TAP_ZOOM = 2.5f

    /** Longest bitmap edge we render. Larger zoom levels upscale instead of risking OOM. */
    const val MAX_RENDER_WIDTH_PX = 2048

    private const val ZOOM_STEP = 0.25f

    fun clamp(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * Scroll position that keeps the content point under [focus] (viewport px) fixed when the
     * content grows by [factor]: (scroll + focus) * factor - focus.
     */
    fun focalScroll(scroll: Float, focus: Float, factor: Float): Float =
        ((scroll + focus) * factor - focus).coerceAtLeast(0f)

    /**
     * Bitmap width for a page laid out [layoutWidthPx] wide at [zoom]. Snapped to 25% zoom steps so
     * a pinch doesn't queue a re-render every frame, and capped at [MAX_RENDER_WIDTH_PX].
     */
    fun renderWidth(viewportWidthPx: Int, zoom: Float): Int {
        val snapped = (clamp(zoom) / ZOOM_STEP).roundToInt() * ZOOM_STEP
        return (viewportWidthPx * snapped).roundToInt().coerceIn(1, MAX_RENDER_WIDTH_PX)
    }

    /** Next zoom level for a double-tap: zoom in from fit-width, otherwise return to fit-width. */
    fun doubleTapTarget(current: Float): Float = if (current > MIN_ZOOM + 0.05f) MIN_ZOOM else DOUBLE_TAP_ZOOM
}

/**
 * Two-finger pinch detector that runs on the Initial pass, so it wins over the LazyColumn's
 * one-finger scroll (the old detectTransformGestures lost that race and pinch rarely worked).
 * One-finger drags are left untouched for scrolling and drawing.
 */
internal fun Modifier.pinchToZoom(
    enabled: Boolean,
    onPinch: (zoomChange: Float, centroid: Offset) -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val callback by rememberUpdatedState(onPinch)
        pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoomChange = event.calculateZoom()
                        if (zoomChange != 1f) callback(zoomChange, event.calculateCentroid(useCurrent = true))
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}
