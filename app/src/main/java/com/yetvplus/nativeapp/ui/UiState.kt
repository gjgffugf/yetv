package com.yetvplus.nativeapp.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/**
 * 跨页面保留的界面状态。
 *
 * 关键用途：搜索页 → 详情页 → 返回搜索页时，
 * 输入框里的关键词和已加载的结果都不能丢（Composable 重建会重置 remember）。
 */
object UiState {
    /**
     * 当前登录角色（owner / admin / user）。
     * 放在可观察状态里：任何地方一改，底部 Dock 立刻增删「管理」入口，
     * 不用重启应用。
     *
     * 注意：必须声明在 object 内部。放到顶层的话，
     * `UiState.roleState` 这种写法就会 Unresolved reference。
     */
    var roleState = mutableStateOf("")

    /**
     * 角色刷新计数。每次回到前台 +1，用来重新拉一次真实角色。
     *
     * 这是「重进应用后管理页不显示」的根因修复：MainActivity 是 singleTop，
     * 从后台回来不会重新 onCreate，Compose 里的 LaunchedEffect(Unit) 不重跑，
     * 角色就一直停在启动时的旧值。
     */
    var roleTick = mutableIntStateOf(0)

    /** 写入角色：只更新可观察状态（持久化由 ApiClient.saveRole 负责） */
    fun setRole(role: String) {
        roleState.value = role
    }

    /**
     * 登录态是否已失效（站点返回 401）。
     * 置为 true 后 MainActivity 会强制清登录态并退回登录页。
     */
    var sessionExpired by mutableStateOf(false)

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

    /** 本次启动是否已经检查过更新（避免重复弹窗） */
    var updateChecked: Boolean = false

    fun resetSearch() {
        keyword = ""
        submitted = ""
        results = emptyList()
        groups = emptyList()
        searched = false
        loadedQuery = ""
    }
}
