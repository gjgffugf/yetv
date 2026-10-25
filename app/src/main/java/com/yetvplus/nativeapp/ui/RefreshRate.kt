package com.yetvplus.nativeapp.ui

import android.app.Activity
import android.os.Build
import android.view.Display
import kotlin.math.roundToInt

/**
 * 刷新率切换。
 *
 * 原理：Android 6.0（API 23）起可以给窗口指定 `preferredDisplayModeId`，
 * 系统会切到该模式（含目标刷新率）。只在设备**支持**的模式里挑最接近的，
 * 不支持就保持原样，不会报错。
 */
object RefreshRate {

    val OPTIONS = listOf(60, 90, 120, 144)

    /** 设备实际支持的刷新率（去重、升序） */
    fun supported(activity: Activity): List<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return listOf(60)
        return try {
            val display = activity.windowManager?.defaultDisplay ?: return listOf(60)
            display.supportedModes
                .map { it.refreshRate.roundToInt() }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            listOf(60)
        }
    }

    /**
     * 应用目标刷新率。
     * 挑选方式：优先刷新率完全匹配；同刷新率下取分辨率最高的（避免变模糊）。
     */
    fun apply(activity: Activity, targetHz: Int) {
        if (targetHz <= 0) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val display = activity.windowManager?.defaultDisplay ?: return
            val modes = display.supportedModes ?: return
            val current = display.mode
            val curW = current?.physicalWidth ?: 0

            var best: Display.Mode? = null
            var bestDelta = Int.MAX_VALUE
            var bestPixels = -1
            for (m in modes) {
                val hz = m.refreshRate.roundToInt()
                // 分辨率需与当前一致（或当前未知时不限制），否则会强制换分辨率
                if (curW > 0 && m.physicalWidth != curW) continue
                val delta = kotlin.math.abs(hz - targetHz)
                val pixels = m.physicalWidth * m.physicalHeight
                when {
                    delta < bestDelta -> {
                        bestDelta = delta; best = m; bestPixels = pixels
                    }
                    delta == bestDelta && pixels > bestPixels -> {
                        best = m; bestPixels = pixels
                    }
                }
            }
            val mode = best ?: return
            val params = activity.window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                params.preferredDisplayModeId = mode.modeId
                activity.window.attributes = params
            }
        } catch (e: Exception) {
            // 个别 ROM 不支持，忽略即可
        }
    }
}
