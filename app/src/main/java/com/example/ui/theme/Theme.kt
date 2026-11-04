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

private val AtelierLightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = SageSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = SlateWeatherTertiary,
    onTertiary = Color.White,
    tertiaryContainer = SlateWeatherContainer,
    onTertiaryContainer = OnSlateWeatherContainer,
    background = AlabasterBg,
    onBackground = EspressoInk,
    surface = IvorySurface,
    onSurface = EspressoInk,
    surfaceVariant = ParchmentVariant,
    onSurfaceVariant = WarmTaupe,
    outline = Color(0xFFC5B9A8)
)

private val AtelierDarkColorScheme = darkColorScheme(
    primary = BurnishedTerracotta,
    onPrimary = ObsidianBg,
    primaryContainer = DarkTerracottaContainer,
    onPrimaryContainer = WarmLinenText,
    secondary = EucalyptusSecondary,
    onSecondary = ObsidianBg,
    secondaryContainer = DarkSageContainer,
    onSecondaryContainer = WarmLinenText,
    tertiary = SkySlateDark,
    onTertiary = ObsidianBg,
    tertiaryContainer = DarkSlateContainer,
    onTertiaryContainer = WarmLinenText,
    background = ObsidianBg,
    onBackground = WarmLinenText,
    surface = CharcoalSurface,
    onSurface = WarmLinenText,
    surfaceVariant = VelvetSurfaceVariant,
    onSurfaceVariant = MutedSandText,
    outline = Color(0xFF4A423B)
)

val AtelierShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AtelierDarkColorScheme else AtelierLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AtelierShapes,
        content = content
    )
}
