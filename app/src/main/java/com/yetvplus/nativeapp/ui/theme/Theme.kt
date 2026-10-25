package com.yetvplus.nativeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF7C6BFF)
val BrandDark = Color(0xFF1B1440)

// ---------------- 深色 / 浅色两套「非彩色」配色 ----------------
// 主色（Brand）不随深浅模式变化，由「个性化 → 默认色」单独控制，两者互不干扰。

private val BgDark = Color(0xFF0B0B12)
private val SurfaceDark = Color(0xFF171722)
private val SurfaceLightDark = Color(0xFF242433)
private val TextPrimaryDark = Color(0xFFEFEFF7)
private val TextSecondaryDark = Color(0xFFA0A0B8)

private val BgLight = Color(0xFFF4F4F7)
private val SurfaceLightMode = Color(0xFFFFFFFF)
private val SurfaceLightLight = Color(0xFFE8E8EF)
private val TextPrimaryLight = Color(0xFF1A1A22)
private val TextSecondaryLight = Color(0xFF6B6B80)

val LocalBg = staticCompositionLocalOf { BgDark }
val LocalSurface = staticCompositionLocalOf { SurfaceDark }
val LocalSurfaceLight = staticCompositionLocalOf { SurfaceLightDark }
val LocalTextPrimary = staticCompositionLocalOf { TextPrimaryDark }
val LocalTextSecondary = staticCompositionLocalOf { TextSecondaryDark }

/** 是否为深色模式（供极少数需要按模式取值的场景使用） */
val LocalIsDark = staticCompositionLocalOf { true }

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
fun YETVTheme(
    brandColor: Color = Brand,
    dark: Boolean = true,
    content: @Composable () -> Unit
) {
    val bg = if (dark) BgDark else BgLight
    val sf = if (dark) SurfaceDark else SurfaceLightMode
    val sfl = if (dark) SurfaceLightDark else SurfaceLightLight
    val tp = if (dark) TextPrimaryDark else TextPrimaryLight
    val ts = if (dark) TextSecondaryDark else TextSecondaryLight

    val colors = if (dark) {
        darkColorScheme(
            primary = brandColor,
            onPrimary = Color.White,
            secondary = brandColor,
            background = bg,
            surface = sf,
            onBackground = tp,
            onSurface = tp,
            surfaceVariant = sfl,
            onSurfaceVariant = ts
        )
    } else {
        lightColorScheme(
            primary = brandColor,
            onPrimary = Color.White,
            secondary = brandColor,
            background = bg,
            surface = sf,
            onBackground = tp,
            onSurface = tp,
            surfaceVariant = sfl,
            onSurfaceVariant = ts
        )
    }
    CompositionLocalProvider(
        LocalBrand provides brandColor,
        LocalBg provides bg,
        LocalSurface provides sf,
        LocalSurfaceLight provides sfl,
        LocalTextPrimary provides tp,
        LocalTextSecondary provides ts,
        LocalIsDark provides dark
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography(),
            content = content
        )
    }
}
