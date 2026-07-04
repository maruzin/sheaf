package com.sheaf.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sheaf.core.ui.R

/** Sheaf brand type — Hanken Grotesk (bundled). Weights map to the same variable file. */
val HankenGrotesk = FontFamily(
    Font(R.font.hanken_grotesk_regular, FontWeight.Normal),
    Font(R.font.hanken_grotesk_medium, FontWeight.Medium),
    Font(R.font.hanken_grotesk_semibold, FontWeight.SemiBold),
)

private val base = Typography()

/** Apply Hanken Grotesk across the type scale; keep Material3 metrics. */
val SheafTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = HankenGrotesk),
    displayMedium = base.displayMedium.copy(fontFamily = HankenGrotesk),
    displaySmall = base.displaySmall.copy(fontFamily = HankenGrotesk),
    headlineLarge = base.headlineLarge.copy(fontFamily = HankenGrotesk),
    headlineMedium = base.headlineMedium.copy(fontFamily = HankenGrotesk),
    headlineSmall = base.headlineSmall.copy(fontFamily = HankenGrotesk),
    titleLarge = base.titleLarge.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontFamily = HankenGrotesk),
    bodyMedium = base.bodyMedium.copy(fontFamily = HankenGrotesk),
    bodySmall = base.bodySmall.copy(fontFamily = HankenGrotesk),
    labelLarge = base.labelLarge.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = HankenGrotesk, fontWeight = FontWeight.Medium),
)
