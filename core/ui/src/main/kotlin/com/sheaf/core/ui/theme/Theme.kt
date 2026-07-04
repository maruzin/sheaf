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

// ---- Ember visual direction (chosen at M2) ----
// Neutral slate surfaces + one warm ember accent. Document stays the hero.
private val Ember = Color(0xFFE4713B)
private val EmberDeep = Color(0xFFC85A28)
private val OnEmberDark = Color(0xFF3A1608)

private val EmberDark = darkColorScheme(
    primary = Ember,
    onPrimary = OnEmberDark,
    primaryContainer = Color(0xFF5A2E17),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFC9C3B9),
    onSecondary = Color(0xFF302A22),
    background = Color(0xFF15171C),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF15171C),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF262933),
    onSurfaceVariant = Color(0xFFC3C6CC),
    outline = Color(0xFF3A3E46),
    outlineVariant = Color(0xFF2B2E36),
)

private val EmberLight = lightColorScheme(
    primary = EmberDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF3A1608),
    secondary = Color(0xFF6C6459),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF15171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15171C),
    surfaceVariant = Color(0xFFE7E8EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFC3C6CC),
    outlineVariant = Color(0xFFDEE0E4),
)

@Composable
fun SheafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Material You opt-in; Ember is the default identity
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> EmberDark
        else -> EmberLight
    }
    MaterialTheme(
        colorScheme = colors,
        typography = SheafTypography,
        content = content,
    )
}
