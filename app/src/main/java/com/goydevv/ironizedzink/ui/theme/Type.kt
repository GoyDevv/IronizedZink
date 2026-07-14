/*
 * Ironized Zink — typography (Lexend variable font).
 * Lexend is licensed under the SIL Open Font License 1.1 (see assets/licenses).
 */
@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.goydevv.ironizedzink.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.goydevv.ironizedzink.R

/** Builds a Lexend face at a specific weight from the bundled variable font. */
private fun lexend(weight: Int, fontWeight: FontWeight) = Font(
    resId = R.font.lexend,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** The Lexend family, exposed across the standard weight range. */
val Lexend = FontFamily(
    lexend(300, FontWeight.Light),
    lexend(400, FontWeight.Normal),
    lexend(500, FontWeight.Medium),
    lexend(600, FontWeight.SemiBold),
    lexend(700, FontWeight.Bold),
)

/** Material 3 type scale, re-skinned with Lexend on every style. */
val LexendTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Lexend),
        displayMedium = displayMedium.copy(fontFamily = Lexend),
        displaySmall = displaySmall.copy(fontFamily = Lexend),
        headlineLarge = headlineLarge.copy(fontFamily = Lexend),
        headlineMedium = headlineMedium.copy(fontFamily = Lexend),
        headlineSmall = headlineSmall.copy(fontFamily = Lexend),
        titleLarge = titleLarge.copy(fontFamily = Lexend),
        titleMedium = titleMedium.copy(fontFamily = Lexend),
        titleSmall = titleSmall.copy(fontFamily = Lexend),
        bodyLarge = bodyLarge.copy(fontFamily = Lexend),
        bodyMedium = bodyMedium.copy(fontFamily = Lexend),
        bodySmall = bodySmall.copy(fontFamily = Lexend),
        labelLarge = labelLarge.copy(fontFamily = Lexend),
        labelMedium = labelMedium.copy(fontFamily = Lexend),
        labelSmall = labelSmall.copy(fontFamily = Lexend),
    )
}
