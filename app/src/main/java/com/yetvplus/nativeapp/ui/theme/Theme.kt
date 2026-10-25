package com.yetvplus.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF7C6BFF)
val BrandDark = Color(0xFF1B1440)
val Bg = Color(0xFF0B0B12)
val Surface = Color(0xFF171722)
val SurfaceLight = Color(0xFF242433)
val TextPrimary = Color(0xFFEFEFF7)
val TextSecondary = Color(0xFFA0A0B8)

/** 当前主色，可运行时切换（个性化 → 默认色） */
val LocalBrand = staticCompositionLocalOf { Brand }

/**
 * 预设可选主色。
 * 注意：0xFF7C6BFF 这类字面量超过 Int 上限，Kotlin 会推断成 Long，
 * 必须显式 toInt() 并声明为 Int，否则跟 Prefs.themeColor(Int) 比较会报类型不匹配。
 * Color(Int) 接受的正是这个「负数」形式的 ARGB。
 */
val THEME_PRESETS: List<Pair<String, Int>> = listOf(
    "紫" to 0xFF7C6BFF.toInt(),
    "蓝" to 0xFF2E7CF6.toInt(),
    "青" to 0xFF12B8A6.toInt(),
    "绿" to 0xFF22A06B.toInt(),
    "橙" to 0xFFFF8A3D.toInt(),
    "红" to 0xFFE5484D.toInt(),
    "粉" to 0xFFE0559B.toInt(),
    "金" to 0xFFD9A441.toInt()
)

@Composable
fun YETVTheme(brandColor: Color = Brand, content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = brandColor,
        onPrimary = Color.White,
        secondary = brandColor,
        background = Bg,
        surface = Surface,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceLight,
        onSurfaceVariant = TextSecondary
    )
    CompositionLocalProvider(LocalBrand provides brandColor) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography(),
            content = content
        )
    }
}
