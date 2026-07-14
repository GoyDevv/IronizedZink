/*
 * Ironized Zink — Material 3 / Material You theme.
 * Dark-only, with dynamic color (Material You) on Android 12+ and a hand-tuned
 * steel fallback on older devices.
 */
package com.goydevv.ironizedzink.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Hand-tuned "ironized" dark scheme used when dynamic color is unavailable (API < 31). */
private val IronDark = darkColorScheme(
    primary = Color(0xFF9FC6F0),
    onPrimary = Color(0xFF07314F),
    primaryContainer = Color(0xFF1E496B),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFB6C8DC),
    onSecondary = Color(0xFF203141),
    secondaryContainer = Color(0xFF364858),
    onSecondaryContainer = Color(0xFFD2E4F8),
    tertiary = Color(0xFFC7C2EA),
    background = Color(0xFF0E1216),
    onBackground = Color(0xFFDEE3E9),
    surface = Color(0xFF0E1216),
    onSurface = Color(0xFFDEE3E9),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainer = Color(0xFF1A1F24),
    surfaceContainerHigh = Color(0xFF242A30),
    outline = Color(0xFF8B9198),
)

/**
 * Dark-only Material You theme. On Android 12+ the colours are derived from the
 * user's wallpaper (dynamic color); otherwise the [IronDark] fallback is used.
 */
@Composable
fun IronizedZinkTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        IronDark
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = LexendTypography,
        content = content,
    )
}
