package com.yetvplus.native.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF7C6BFF)
val BrandDark = Color(0xFF1B1440)
val Bg = Color(0xFF0B0B12)
val Surface = Color(0xFF171722)
val SurfaceLight = Color(0xFF242433)
val TextPrimary = Color(0xFFEFEFF7)
val TextSecondary = Color(0xFFA0A0B8)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    secondary = Brand,
    background = Bg,
    surface = Surface,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary
)

@Composable
fun YETVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content
    )
}
