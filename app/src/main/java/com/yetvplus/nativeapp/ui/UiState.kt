package com.yetvplus.nativeapp.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.yetvplus.nativeapp.data.VideoItem
import com.yetvplus.nativeapp.data.SearchGroup

/**
 * 主题色（ARGB）。放在可观察状态里，设置页改了立刻重组，无需重启应用。
 * 0 表示用默认 Brand。
 */
val themeArgbState = mutableIntStateOf(0)

/** 深色模式。放在可观察状态里，切换后界面立即重组。 */
val darkModeState = mutableStateOf(true)

/** 待安装的更新；非空时弹出更新提示 */
val pendingUpdate = mutableStateOf<com.yetvplus.nativeapp.ui.UpdateManager.UpdateInfo?>(null)

/** 本次启动是否已经检查过更新（避免重复弹窗） */
var updateChecked: Boolean = false

/**
 * 跨页面保留的界面状态。
 *
 * 关键用途：搜索页 → 详情页 → 返回搜索页时，
 * 输入框里的关键词和已加载的结果都不能丢（Composable 重建会重置 remember）。
 */
object UiState {
    /** 搜索框当前文字 */
    var keyword: String = ""

    /** 已提交的关键词（真正触发搜索的那个） */
    var submitted: String = ""

    /** 上次搜索结果，返回搜索页时直接复用 */
    var results: List<VideoItem> = emptyList()

    /** 整合后的搜索结果（切走再切回也保留，不用重新搜索） */
    var groups: List<SearchGroup> = emptyList()

    /** 是否已有一次成功/失败的搜索（决定显示结果还是空态） */
    var searched: Boolean = false

    /** 已经加载完成的关键词；切走再切回时用它判断要不要重新请求 */
    var loadedQuery: String = ""

    fun resetSearch() {
        keyword = ""
        submitted = ""
        results = emptyList()
        groups = emptyList()
        searched = false
        loadedQuery = ""
    }
}
