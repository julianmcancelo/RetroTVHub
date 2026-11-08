package com.retro.tvbox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
private val RetroTvColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = BackgroundDark,
    primaryContainer = SurfaceDarkVariant,
    onPrimaryContainer = TextWhite,
    secondary = NeonPink,
    onSecondary = BackgroundDark,
    background = BackgroundDark,
    onBackground = TextWhite,
    surface = SurfaceDark,
    onSurface = TextWhite,
    surfaceVariant = SurfaceDarkVariant,
    onSurfaceVariant = TextGray,
    border = SurfaceDarkVariant
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun RetroTvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RetroTvColorScheme,
        content = content
    )
}
