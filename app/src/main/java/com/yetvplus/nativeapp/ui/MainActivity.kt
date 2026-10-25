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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
            YETVTheme { AppRoot() }
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
                        NavigationBar(containerColor = Color.Transparent) {
                            tabs.forEach { t ->
                                val selected = currentRoute == t.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(t.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label, fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Brand,
                                        selectedTextColor = Brand,
                                        indicatorColor = Color.White.copy(alpha = 0.10f),
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                NavHost(
                    navController = navController,
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
                        LoginScreen(app, onSuccess = enterHome, onSkip = enterHome)
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
