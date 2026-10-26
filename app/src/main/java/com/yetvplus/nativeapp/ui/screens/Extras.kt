package com.yetvplus.nativeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.ui.UpdateManager
import com.yetvplus.nativeapp.ui.darkModeState
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import com.yetvplus.nativeapp.ui.theme.LocalSurface
import com.yetvplus.nativeapp.ui.theme.LocalSurfaceLight
import com.yetvplus.nativeapp.ui.theme.LocalTextPrimary
import com.yetvplus.nativeapp.ui.theme.LocalTextSecondary
import kotlinx.coroutines.launch

/**
 * 深色 / 浅色模式切换。
 *
 * 只切换「非彩色」部分（背景、卡片、文字），主色仍由「个性化 → 软件默认色」
 * 单独控制，两者互不影响、可以共存。
 */
@Composable
fun DarkModeToggle(app: YETVApp) {
    val dark = darkModeState.value
    Box(
        modifier = Modifier
            .background(LocalSurfaceLight.current, RoundedCornerShape(12.dp))
            .clickable {
                val next = !dark
                app.prefs.darkMode = next
                darkModeState.value = next
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (dark) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = if (dark) "深色模式" else "浅色模式",
                tint = LocalBrand.current,
                modifier = Modifier.size(16.dp)
            )
            Text(
                if (dark) "深色" else "浅色",
                color = LocalTextPrimary.current,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 5.dp)
            )
        }
    }
}

/**
 * 版本与检查更新。
 * 「检查更新」手动触发一次请求；每次进软件也会自动检查一次（见 AppRoot）。
 */
@Composable
fun UpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = LocalSurface.current),
        shape = RoundedCornerShape(14.dp),
        // 卡片纵向多留 2dp
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = LocalBrand.current,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    "当前版本 v${UpdateManager.currentName()}",
                    color = LocalTextPrimary.current,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                if (tip.isNotEmpty()) {
                    Text(tip, color = LocalTextSecondary.current, fontSize = 11.sp)
                }
            }
            if (checking) {
                CircularProgressIndicator(
                    color = LocalBrand.current,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    "检查更新",
                    color = LocalBrand.current,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .background(LocalSurfaceLight.current, RoundedCornerShape(10.dp))
                        .clickable {
                            checking = true
                            tip = ""
                            scope.launch {
                                val info = UpdateManager.check()
                                checking = false
                                if (info != null && info.isNewer()) {
                                    com.yetvplus.nativeapp.ui.pendingUpdate.value = info
                                } else {
                                    tip = "已是最新版本"
                                }
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * 更新提示弹窗。
 * force = true 时不提供「稍后再说」，必须更新。
 */
@Composable
fun UpdateDialog(
    info: UpdateManager.UpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var percent by remember { mutableStateOf(-1) }
    var failed by remember { mutableStateOf(false) }
    val downloading = percent in 0..99

    Dialog(onDismissRequest = { if (!info.force && !downloading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = LocalSurface.current,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "发现新版本 v${info.versionName}",
                    color = LocalTextPrimary.current,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (info.changelog.isNotBlank()) {
                    Text(
                        info.changelog,
                        color = LocalTextSecondary.current,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                when {
                    failed -> {
                        Text("下载失败，请检查网络后重试", color = Color(0xFFFF8A80), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    downloading -> {
                        LinearProgressIndicator(
                            progress = percent / 100f,
                            color = LocalBrand.current,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "下载中… $percent%",
                            color = LocalTextSecondary.current,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!info.force && !downloading) {
                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LocalSurfaceLight.current
                                )
                            ) {
                                Text("稍后再说", color = LocalTextPrimary.current, fontSize = 13.sp)
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = {
                                failed = false
                                percent = 0
                                scope.launch {
                                    val f = UpdateManager.download(context, info) { p ->
                                        percent = p
                                        if (p < 0) failed = true
                                    }
                                    if (f != null) {
                                        UpdateManager.install(context, f)
                                    } else {
                                        failed = true
                                    }
                                }
                            },
                            enabled = !downloading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalBrand.current
                            )
                        ) {
                            Text(
                                if (downloading) "下载中" else "立即更新",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
