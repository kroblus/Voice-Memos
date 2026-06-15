package com.voicememos.aicalendar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = InkTeal,
    onPrimary = Color.White,
    secondary = Clay,
    onSecondary = Color.White,
    background = Paper,
    onBackground = NightInk,
    surface = Porcelain,
    onSurface = NightInk,
    tertiary = Ochre,
)

private val DarkColors = darkColorScheme(
    primary = MistTeal,
    onPrimary = DeepTeal,
    secondary = Copper,
    onSecondary = NightInk,
    background = NightInk,
    onBackground = Paper,
    surface = DeepTeal,
    onSurface = Paper,
    tertiary = Ochre,
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

