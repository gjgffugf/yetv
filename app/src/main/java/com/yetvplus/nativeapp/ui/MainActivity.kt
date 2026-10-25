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
import com.yetvplus.nativeapp.ui.theme.Bg
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.TextSecondary
import com.yetvplus.nativeapp.ui.theme.YETVTheme
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush

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
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val saved = app.prefs.themeColor
            val brand = if (saved == 0) Brand else Color(saved)
            YETVTheme(brandColor = brand) { AppRoot() }
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

@Composable
private fun AppRoot() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as YETVApp
    val navController = rememberNavController()

    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 自定义背景图（设置页开启后生效）
            if (app.prefs.bgEnabled && app.prefs.bgPath.isNotEmpty()) {
                AppBackground(app.prefs.bgPath, app.prefs.bgDim)
            }

            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    val show = tabs.any { it.route == currentRoute }
                    if (show) {
                        GlassDock(tabs = tabs, currentRoute = currentRoute) { route ->
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
                        HomeScreen(app) { v ->
                            NavArgs.video = v
                            navController.navigate(Route.DETAIL)
                        }
                    }
                    composable(Route.SEARCH) {
                        SearchScreen(app) { v ->
                            NavArgs.video = v
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
                                NavArgs.video = VideoItem(
                                    source = parts.getOrNull(0) ?: "",
                                    id = parts.getOrNull(1) ?: "",
                                    title = r.title,
                                    poster = r.poster
                                )
                                navController.navigate(Route.DETAIL)
                            },
                            onLogout = {
                                navController.navigate(Route.LOGIN) {
                                    popUpTo(Route.HOME) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Route.DETAIL) {
                        DetailScreen(app) { navController.popBackStack() }
                    }
                    composable(Route.SETTINGS) {
                        SettingsScreen(app) { navController.popBackStack() }
                    }
                }
            }
        }
    }
}

/**
 * 底部液态玻璃 Dock 栏。
 * 半透明渐变 + 细高光描边 + 悬浮阴影，选中项用主色胶囊高亮并带缩放动画。
 */
@Composable
private fun GlassDock(
    tabs: List<TabDef>,
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    val brand = LocalBrand.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
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
                )
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { t ->
                val selected = currentRoute == t.route
                val size by animateDpAsState(
                    targetValue = if (selected) 26.dp else 22.dp,
                    label = "dockIcon"
                )
                val tint by animateColorAsState(
                    targetValue = if (selected) brand else TextSecondary,
                    label = "dockTint"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .then(
                            if (selected) {
                                Modifier.background(brand.copy(alpha = 0.16f))
                            } else Modifier
                        )
                        .clickable { onSelect(t.route) }
                        .padding(vertical = 7.dp),
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
