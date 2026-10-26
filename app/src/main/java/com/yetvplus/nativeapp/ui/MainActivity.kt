package com.yetvplus.nativeapp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.data.VideoItem
import com.yetvplus.nativeapp.ui.admin.AdminScreen
import com.yetvplus.nativeapp.ui.screens.AppBackground
import com.yetvplus.nativeapp.ui.screens.DetailScreen
import com.yetvplus.nativeapp.ui.screens.HomeScreen
import com.yetvplus.nativeapp.ui.screens.LoginScreen
import com.yetvplus.nativeapp.ui.screens.MineScreen
import com.yetvplus.nativeapp.ui.screens.SearchScreen
import com.yetvplus.nativeapp.ui.screens.SettingsScreen
import com.yetvplus.nativeapp.ui.theme.LocalBg
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.LocalTextSecondary
import com.yetvplus.nativeapp.ui.theme.YETVTheme
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.yetvplus.nativeapp.data.SearchGroup
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yetvplus.nativeapp.data.DoubanItem
import com.yetvplus.nativeapp.ui.UpdateManager
import com.yetvplus.nativeapp.ui.pendingUpdate
import com.yetvplus.nativeapp.ui.screens.UpdateDialog
import com.yetvplus.nativeapp.ui.UiState
import androidx.compose.runtime.LaunchedEffect

object Route {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SEARCH = "search"
    const val ADMIN = "admin"
    const val MINE = "mine"
    const val DETAIL = "detail"
    const val SETTINGS = "settings"
}

/** 跨页面传递当前视频（避免把长参数塞进路由） */
object NavArgs {
    var video: VideoItem? = null

    /** 搜索整合后的分组；非空时详情页/播放页可横向切源 */
    var group: SearchGroup? = null

    /** 从首页海报点进来的豆瓣条目（此时还没有 source/id，详情里再解析） */
    var douban: DoubanItem? = null
}

/** 传给播放页的额外参数 */
object PlayerArgs {
    var variants: List<VideoItem> = emptyList()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 应用刷新率设置（默认跟随系统）
        RefreshRate.apply(this, (application as YETVApp).prefs.refreshRate)
        setContent {
            (application as YETVApp)
            // 观察可变的主题色状态，设置页一改立刻生效，不用重启
            val saved = themeArgbState.intValue
            val brand = if (saved == 0) Brand else Color(saved)
            val dark = darkModeState.value
            YETVTheme(brandColor = brand, dark = dark) { AppRoot() }
        }
    }
}

private data class TabDef(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val tabs = listOf(
    TabDef(Route.HOME, "首页", Icons.Default.Home),
    TabDef(Route.SEARCH, "搜索", Icons.Default.Search),
    TabDef(Route.ADMIN, "管理", Icons.Default.Settings),
    TabDef(Route.MINE, "我的", Icons.Default.Person)
)

/** owner / admin 才有管理页；普通用户（user）和未登录不显示 */
private fun isManager(role: String): Boolean =
    role.equals("owner", ignoreCase = true) || role.equals("admin", ignoreCase = true)

/**
 * 底部可见的栏目。普通用户登录后直接去掉「管理」。
 * 用 prefs.role 作 key：登录后 role 变化，AppRoot 重组时会自动重算。
 */
@Composable
private fun visibleTabs(app: YETVApp): List<TabDef> {
    val role = app.prefs.role
    return remember(role) {
        if (isManager(role)) tabs else tabs.filter { it.route != Route.ADMIN }
    }
}

@Composable
private fun AppRoot() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as YETVApp
    val navController = rememberNavController()

    // 每次进软件自动检查一次更新
    LaunchedEffect(Unit) {
        if (!UiState.updateChecked) {
            UiState.updateChecked = true
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val info = UpdateManager.check()
                if (info != null && info.isNewer()) {
                    pendingUpdate.value = info
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = LocalBg.current) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 背景图：选了就生效（不再需要开关）。
            // 没配置时保持纯色，随深色/浅色模式一起变。
            if (app.prefs.bgPath.isNotEmpty()) {
                AppBackground(app.prefs.bgPath, app.prefs.bgDim)
            }

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // 非管理员若因某种原因停在管理页，退回首页。
            // 否则底部 Dock 不含「管理」会被隐藏，用户就卡在这一页出不去了。
            val hasAdmin = isManager(app.prefs.role)
            LaunchedEffect(currentRoute, hasAdmin) {
                if (!hasAdmin && currentRoute == Route.ADMIN) {
                    navController.navigate(Route.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    val shown = visibleTabs(app)
                    val show = shown.any { it.route == currentRoute }
                    if (show) {
                        GlassDock(tabs = shown, currentRoute = currentRoute) { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }

            ) { padding ->
                NavHost(
                    navController = navController,
                    enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 } },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { it / 10 } },
                    // 未登录直接进登录页：站点的 middleware 会挡掉所有 /api/*
                    startDestination = if (app.prefs.isLoggedIn()) Route.HOME else Route.LOGIN,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    composable(Route.LOGIN) {
                        val enterHome: () -> Unit = {
                            navController.navigate(Route.HOME) {
                                popUpTo(Route.LOGIN) { inclusive = true }
                            }
                        }
                        LoginScreen(app, onSuccess = enterHome)
                    }
                    composable(Route.HOME) {
                        // 海报点击直达详情，不等搜索结果：先渲染框架，详情内部再异步解析源
                        HomeScreen(app) { d ->
                            NavArgs.douban = d
                            NavArgs.group = null
                            NavArgs.video = VideoItem(title = d.title, poster = d.poster)
                            navController.navigate(Route.DETAIL)
                        }
                    }
                    composable(Route.SEARCH) {
                        SearchScreen(app) { g ->
                            NavArgs.group = g
                            NavArgs.douban = null
                            NavArgs.video = g.best
                            navController.navigate(Route.DETAIL)
                        }
                    }
                    composable(Route.ADMIN) { AdminScreen(app) }
                    composable(Route.MINE) {
                        MineScreen(
                            app,
                            onSettings = { navController.navigate(Route.SETTINGS) },
                            onOpenRecord = { r ->
                                // key 形如 "source+id"，拆出来走详情接口现取分集
                                val parts = r.key.split("+", limit = 2)
                                val v = VideoItem(
                                    source = parts.getOrNull(0) ?: "",
                                    id = parts.getOrNull(1) ?: "",
                                    title = r.title,
                                    poster = r.poster
                                )
                                NavArgs.video = v
                                NavArgs.douban = null
                                NavArgs.group = SearchGroup(v.title, v.poster, listOf(v))
                                navController.navigate(Route.DETAIL)
                            },
                            onLogout = {
                                navController.navigate(Route.LOGIN) {
                                    popUpTo(Route.HOME) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(
                        Route.DETAIL,
                        enterTransition = {
                            fadeIn(tween(240)) + scaleIn(
                                tween(240), initialScale = 0.94f
                            )
                        },
                        exitTransition = { fadeOut(tween(180)) },
                        popEnterTransition = { fadeIn(tween(200)) },
                        popExitTransition = {
                            fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.96f)
                        }
                    ) {
                        DetailScreen(app) { navController.popBackStack() }
                    }
                    composable(Route.SETTINGS) {
                        SettingsScreen(app) { navController.popBackStack() }
                    }
                }

                // 更新提示（全局，不依赖当前页面）
                val up = pendingUpdate.value
                if (up != null) {
                    UpdateDialog(info = up) {
                        pendingUpdate.value = null
                    }
                }
            }
        }
    }
}

/**
 * 底部液态玻璃 Dock 栏。
 *
 * - 半透明渐变 + 高光描边 + 悬浮阴影
 * - 选中项下方有胶囊滑块（与选项内容等高、同一垂直中心，保证居中）
 * - 只通过滑块切换：长按 350ms 后滑块放大并可拖动，拖动中实时切页
 */
@Composable
private fun GlassDock(
    tabs: List<TabDef>,
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    val brand = LocalBrand.current
    val index = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val n = tabs.size.coerceAtLeast(1)
    val routeState = rememberUpdatedState(currentRoute)

    // 拖动中：滑块放大，提示"已选中，可滑动"
    var dragging by remember { mutableStateOf(false) }
    val pillScale by animateFloatAsState(
        targetValue = if (dragging) 1.16f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "pillScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // contentAlignment 保证滑块与选项行共用同一个垂直中心
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(18.dp, RoundedCornerShape(30.dp))
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.18f),
                    RoundedCornerShape(30.dp)
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            val innerPad = 6.dp
            val slotW = (maxWidth - innerPad * 2) / n
            val slotPx = with(LocalDensity.current) { slotW.toPx() }
            val padPx = with(LocalDensity.current) { innerPad.toPx() }
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            fun idxAt(x: Float) = ((x - padPx) / slotPx).toInt().coerceIn(0, n - 1)

            // 手势：长按 350ms 后进入拖动，拖动过程中实时切页
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(tabs, n) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val slop = viewConfiguration.touchSlop
                                // 记录这次按下是怎么结束的：抬起 / 移动出容差
                                var released = false
                                var moved = false
                                // 等 350ms：期间若抬起或移动超出容差就不算长按
                                val isLongPress = withTimeoutOrNull(LONG_PRESS_MS) {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!c.pressed) { released = true; break }
                                        if ((c.position - down.position).getDistance() > slop) {
                                            moved = true
                                            break
                                        }
                                    }
                                } == null

                                // 短按（未超时、未移出容差、原地抬起）＝点击切换
                                if (!isLongPress && released && !moved) {
                                    val x = down.position.x
                                    if (x in 0f..widthPx) {
                                        val t = tabs[idxAt(x)]
                                        if (t.route != routeState.value) onSelect(t.route)
                                    }
                                    continue
                                }
                                if (!isLongPress) continue
                                // 长按成立：滑块放大提示，之后拖动实时切页
                                dragging = true
                                try {
                                    while (true) {
                                        val e = awaitPointerEvent()
                                        val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!c.pressed) break
                                        val x = c.position.x
                                        if (x in 0f..widthPx) {
                                            val t = tabs[idxAt(x)]
                                            if (t.route != routeState.value) onSelect(t.route)
                                        }
                                    }
                                } finally {
                                    dragging = false
                                }
                            }
                        }
                    }
            )

            val offsetX by animateDpAsState(
                targetValue = innerPad + slotW * index,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                label = "dockPill"
            )
            Box(
                modifier = Modifier
                    .offset(x = offsetX)
                    .width(slotW)
                    .height(48.dp)
                    .graphicsLayer {
                        scaleX = pillScale
                        scaleY = pillScale
                    }
                    .clip(RoundedCornerShape(23.dp))
                    .background(brand.copy(alpha = 0.20f))
                    .border(
                        1.dp,
                        brand.copy(alpha = 0.35f),
                        RoundedCornerShape(23.dp)
                    )
            )

            // 选项只用于显示，不再单独响应点击（切换统一走滑块）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = innerPad),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { t ->
                    val selected = t.route == routeState.value
                    val size by animateDpAsState(
                        targetValue = if (selected) 25.dp else 22.dp,
                        label = "dockIcon"
                    )
                    val tint by animateColorAsState(
                        targetValue = if (selected) brand else LocalTextSecondary.current,
                        label = "dockTint"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            t.icon,
                            contentDescription = t.label,
                            tint = tint,
                            modifier = Modifier.size(size)
                        )
                        Text(
                            t.label,
                            color = tint,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Dock 长按判定时间 */
private const val LONG_PRESS_MS = 350L
