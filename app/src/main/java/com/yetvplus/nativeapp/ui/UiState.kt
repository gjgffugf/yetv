package com.yetvplus.nativeapp.ui

import com.yetvplus.nativeapp.data.VideoItem

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

    /** 是否已有一次成功/失败的搜索（决定显示结果还是空态） */
    var searched: Boolean = false

    fun resetSearch() {
        keyword = ""
        submitted = ""
        results = emptyList()
        searched = false
    }
}
