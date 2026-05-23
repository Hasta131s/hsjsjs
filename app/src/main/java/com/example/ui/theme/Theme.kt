package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = SlateBackground,
    primaryContainer = DarkGreen,
    onPrimaryContainer = TextLight,
    secondary = LightGreen,
    onSecondary = SlateBackground,
    background = SlateBackground,
    onBackground = TextLight,
    surface = SlateSurface,
    onSurface = TextLight,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = BorderColor
)

private val LightColorScheme = DarkColorScheme

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme by default as standard gaming utility
  dynamicColor: Boolean = false, // Preserve our corporate slate design
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
