package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NeonDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = NeonVoid,
    primaryContainer = NeonCyanDark,
    onPrimaryContainer = NeonCyan,
    secondary = NeonMagenta,
    onSecondary = TextPrimary,
    secondaryContainer = NeonMagentaDark,
    onSecondaryContainer = NeonMagenta,
    tertiary = NeonLime,
    onTertiary = NeonVoid,
    tertiaryContainer = NeonLimeDark,
    onTertiaryContainer = NeonLime,
    background = NeonVoid,
    onBackground = TextPrimary,
    surface = NeonSurface,
    onSurface = TextPrimary,
    surfaceVariant = NeonSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NeonCardBorder,
    error = NeonCrimson,
    onError = TextPrimary
)

@Composable
fun NeonRunnerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonDarkColorScheme,
        typography = NeonTypography,
        content = content
    )
}
