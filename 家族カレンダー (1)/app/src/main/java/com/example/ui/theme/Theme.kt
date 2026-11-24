package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
  primary = TerracottaPrimary,
  onPrimary = Color.White,
  primaryContainer = TerracottaContainer,
  onPrimaryContainer = OnTerracottaContainer,
  secondary = SoftSage,
  onSecondary = Color.White,
  secondaryContainer = SoftSageContainer,
  onSecondaryContainer = OnSoftSageContainer,
  tertiary = WarmHoney,
  onTertiary = Color.White,
  tertiaryContainer = WarmHoneyContainer,
  onTertiaryContainer = OnWarmHoneyContainer,
  background = WarmCream,
  onBackground = SoftCharcoal,
  surface = SoftLinenWhite,
  onSurface = SoftCharcoal,
  surfaceVariant = WarmSand,
  onSurfaceVariant = WarmTaupe,
  surfaceContainerLowest = WarmCream,
  surfaceContainerLow = WarmCream,
  surfaceContainer = WarmSand,
  surfaceContainerHigh = WarmCream,
  surfaceContainerHighest = WarmSandDeep,
  outline = WarmOutline,
  error = SoftRoseError,
)

@Composable
fun FamilyCalendarTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = LightColorScheme,
    typography = Typography,
    content = content,
  )
}
