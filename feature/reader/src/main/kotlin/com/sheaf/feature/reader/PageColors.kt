package com.sheaf.feature.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/** How each reading theme paints the page bitmaps and the area around them. */
internal object PageColors {
    // Dark: invert, mapping white paper to #121212 and black text to a soft #E0E0E0 (less glare).
    private const val DARK_HIGH = 224f
    private const val DARK_LOW = 18f
    private const val DARK_SCALE = -(DARK_HIGH - DARK_LOW) / 255f

    private val darkMatrix = ColorMatrix(
        floatArrayOf(
            DARK_SCALE, 0f, 0f, 0f, DARK_HIGH,
            0f, DARK_SCALE, 0f, 0f, DARK_HIGH,
            0f, 0f, DARK_SCALE, 0f, DARK_HIGH,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    // Sepia: multiply white paper to #F4ECD8 (warm), leaving black text black.
    private val sepiaMatrix = ColorMatrix().apply { setToScale(0xF4 / 255f, 0xEC / 255f, 0xD8 / 255f, 1f) }

    fun pageFilter(theme: ReaderTheme): ColorFilter? = when (theme) {
        ReaderTheme.Dark -> ColorFilter.colorMatrix(darkMatrix)
        ReaderTheme.Sepia -> ColorFilter.colorMatrix(sepiaMatrix)
        ReaderTheme.Light, ReaderTheme.System -> null
    }
}

@Composable
internal fun readerBackground(theme: ReaderTheme): Color = when (theme) {
    ReaderTheme.Sepia -> Color(0xFFE9DFC8)
    ReaderTheme.Light -> Color(0xFFE9EAEE)
    ReaderTheme.Dark -> Color(0xFF000000)
    ReaderTheme.System -> if (isSystemInDarkTheme()) Color(0xFF15171C) else Color(0xFFE9EAEE)
}

/** Text + background colors for reading mode (reflow). */
@Composable
internal fun reflowColors(theme: ReaderTheme): Pair<Color, Color> = when (theme) {
    ReaderTheme.Light -> Color(0xFF1B1B1F) to Color(0xFFFFFFFF)
    ReaderTheme.Dark -> Color(0xFFE0E0E0) to Color(0xFF121212)
    ReaderTheme.Sepia -> Color(0xFF5B4636) to Color(0xFFF4ECD8)
    ReaderTheme.System ->
        if (isSystemInDarkTheme()) Color(0xFFE3E2E6) to Color(0xFF15171C) else Color(0xFF1B1B1F) to Color(0xFFFFFFFF)
}
