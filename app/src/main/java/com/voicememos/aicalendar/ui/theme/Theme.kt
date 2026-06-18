package com.voicememos.aicalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Eucalyptus,
    onPrimary = Color.White,
    primaryContainer = MintShadow,
    onPrimaryContainer = GraphiteLeaf,
    secondary = Peach,
    onSecondary = GraphiteLeaf,
    secondaryContainer = RoseMilk,
    onSecondaryContainer = GraphiteLeaf,
    tertiary = Butter,
    onTertiary = GraphiteLeaf,
    background = MorningMist,
    onBackground = GraphiteLeaf,
    surface = MilkGlass,
    onSurface = GraphiteLeaf,
    surfaceVariant = MintShadow,
    onSurfaceVariant = LeafGrey,
    outline = Sage,
    outlineVariant = Color(0xFFD3E4DA),
    error = Color(0xFFB94332),
    onError = Color.White,
    errorContainer = Color(0xFFFFDED8),
    onErrorContainer = Color(0xFF5A150D),
)

private val DarkColors = darkColorScheme(
    primary = Sage,
    onPrimary = PineNight,
    primaryContainer = PineSurface,
    onPrimaryContainer = MorningMist,
    secondary = Peach,
    onSecondary = PineNight,
    secondaryContainer = Color(0xFF5F3832),
    onSecondaryContainer = MorningMist,
    tertiary = Butter,
    onTertiary = PineNight,
    background = PineNight,
    onBackground = MorningMist,
    surface = PineSurface,
    onSurface = MorningMist,
    surfaceVariant = Color(0xFF253C34),
    onSurfaceVariant = Color(0xFFC8D8D0),
    outline = Sage,
    outlineVariant = Color(0xFF35564B),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF5A150D),
    errorContainer = Color(0xFF7D251B),
    onErrorContainer = Color(0xFFFFDED8),
)

@Composable
fun ShengliTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ShengliTypography,
        content = content,
    )
}
