package com.nammameter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Auto-rickshaw yellow and black: maximum contrast for reading in sunlight.
val AutoYellow = Color(0xFFFFC400)
val AutoBlack = Color(0xFF0D0D0D)
val WaitAmber = Color(0xFFFF8F00)
val GoGreen = Color(0xFF1B8A3A)
val StopRed = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = AutoBlack,
    onPrimary = AutoYellow,
    primaryContainer = AutoYellow,
    onPrimaryContainer = AutoBlack,
    secondary = Color(0xFF5C4A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE9A6),
    onSecondaryContainer = AutoBlack,
    background = Color.White,
    onBackground = AutoBlack,
    surface = Color.White,
    onSurface = AutoBlack,
    surfaceVariant = Color(0xFFF2F0E8),
    onSurfaceVariant = Color(0xFF2B2B2B),
    error = StopRed,
)

private val DarkColors = darkColorScheme(
    primary = AutoYellow,
    onPrimary = AutoBlack,
    primaryContainer = AutoYellow,
    onPrimaryContainer = AutoBlack,
    secondary = Color(0xFFFFE08A),
    onSecondary = AutoBlack,
    secondaryContainer = Color(0xFF3A3000),
    onSecondaryContainer = Color(0xFFFFF4CC),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFEDEDED),
    error = Color(0xFFFF6E6E),
)

private val AppTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontSize = 18.sp),
        bodyMedium = bodyMedium.copy(fontSize = 16.sp),
        labelLarge = labelLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
    )
}

val FareTextStyle = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.Black, lineHeight = 76.sp)

@Composable
fun NammaMeterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
