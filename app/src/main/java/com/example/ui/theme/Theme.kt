package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WhiteBeautifulColorScheme = lightColorScheme(
  primary = BrandAccent,
  onPrimary = PureWhite,
  primaryContainer = BrandAccentLight,
  onPrimaryContainer = BrandPrimary,
  secondary = TextSecondary,
  onSecondary = PureWhite,
  secondaryContainer = SoftSurface,
  onSecondaryContainer = TextPrimary,
  tertiary = BrandPrimary,
  onTertiary = PureWhite,
  background = PureWhite,
  onBackground = TextPrimary,
  surface = PureWhite,
  onSurface = TextPrimary,
  surfaceVariant = OffWhite,
  onSurfaceVariant = TextSecondary,
  outline = CardBorder,
  outlineVariant = CardBorderSubtle,
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF60A5FA),
  onPrimary = Color(0xFF0F172A),
  primaryContainer = Color(0xFF1E3A8A),
  onPrimaryContainer = Color(0xFFDBEAFE),
  secondary = Color(0xFF94A3B8),
  onSecondary = Color(0xFF0F172A),
  background = Color(0xFF0B0F17),
  onBackground = Color(0xFFF1F5F9),
  surface = Color(0xFF111827),
  onSurface = Color(0xFFF1F5F9),
  surfaceVariant = Color(0xFF1F2937),
  onSurfaceVariant = Color(0xFFCBD5E1),
  outline = Color(0xFF374151),
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Emphasize White Beautiful Theme by default
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else WhiteBeautifulColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

