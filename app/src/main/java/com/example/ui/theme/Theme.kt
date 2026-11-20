package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WaveLinkDarkColorScheme = darkColorScheme(
    primary = SonarCyan,
    onPrimary = OnSonarCyan,
    primaryContainer = SonarCyanContainer,
    onPrimaryContainer = SonarCyan,
    secondary = SignalBlue,
    onSecondary = Color(0xFF001E2E),
    secondaryContainer = SignalBlueContainer,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = SignalAmber,
    onTertiary = Color(0xFF2B1900),
    background = DeepNavy,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = SlateOutline,
    error = CoralRed,
    onError = Color.White,
    errorContainer = CoralRedContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val WaveLinkLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF003830),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = Color(0xFF003355),
    tertiary = SignalAmber,
    onTertiary = Color(0xFF2B1900),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = CoralRed,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val WaveLinkShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun WaveLinkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) WaveLinkDarkColorScheme else WaveLinkLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WaveLinkTypography,
        shapes = WaveLinkShapes,
        content = content
    )
}
