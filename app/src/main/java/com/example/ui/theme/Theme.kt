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
    primary = MedTealDark,
    secondary = MedTealSecondaryDark,
    tertiary = MedSkyDark,
    background = MedBackgroundDark,
    surface = MedSurfaceDark,
    onPrimary = MedBackgroundDark,
    onSecondary = MedTextLight,
    onTertiary = MedBackgroundDark,
    onBackground = MedTextLight,
    onSurface = MedTextLight,
    onSurfaceVariant = MedTextSecondaryLight,
    outlineVariant = MedOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = MedTealLight,
    secondary = MedTealSecondaryLight,
    tertiary = MedSkyLight,
    background = MedBackgroundLight,
    surface = MedSurfaceLight,
    onPrimary = MedSurfaceLight,
    onSecondary = MedSurfaceLight,
    onTertiary = MedSurfaceLight,
    onBackground = MedTextDark,
    onSurface = MedTextDark,
    onSurfaceVariant = MedTextSecondaryDark,
    outlineVariant = MedOutlineLight
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to preserve the brand theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
