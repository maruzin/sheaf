package com.sheaf.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// PLACEHOLDER PALETTE — deliberately neutral. The real, chosen visual direction (1 of 4 proposed)
// is applied at M2 per the Opus 4.8 "propose 4 directions" rule. Do not treat these hexes as final.
private val AccentDark = Color(0xFF6EA8FE)
private val AccentLight = Color(0xFF2F6FED)

private val DarkColors = darkColorScheme(primary = AccentDark)
private val LightColors = lightColorScheme(primary = AccentLight)

@Composable
fun SheafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Material You toggle wired up at M2
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
