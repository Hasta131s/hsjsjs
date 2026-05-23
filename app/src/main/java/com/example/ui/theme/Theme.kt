package com.example.ui.theme

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

private val HighDensityColorScheme = lightColorScheme(
    primary = HighDensityPrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = HighDensityWarnContainer,
    onPrimaryContainer = HighDensityWarnText,
    secondary = HighDensityGreySurface,
    onSecondary = HighDensityTextDark,
    background = HighDensityBackground,
    onBackground = HighDensityTextDark,
    surface = Color.White,
    onSurface = HighDensityTextDark,
    surfaceVariant = HighDensityGreySurface,
    onSurfaceVariant = HighDensityTextMuted,
    outline = HighDensityBorder
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // High Density is primarily a clean corporate light design
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = HighDensityColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
