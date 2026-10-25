package com.yetvplus.nativeapp.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.data.DoubanItem
import com.yetvplus.nativeapp.data.VideoItem
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.Surface
import com.yetvplus.nativeapp.ui.theme.SurfaceLight
import com.yetvplus.nativeapp.ui.theme.TextPrimary
import com.yetvplus.nativeapp.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

// ---------------- 首页 ----------------

private val MOVIE_TAGS = listOf(
    "热门", "最新", "经典", "豆瓣高分", "冷门佳片",
    "华语", "欧美", "韩国", "日本", "动作", "喜剧", "爱情", "科幻", "悬疑", "恐怖"
)
private val TV_TAGS = listOf(
    "热门", "美剧", "英剧", "韩剧", "日剧", "国产剧", "港剧", "日本动画", "综艺", "纪录片"
)

@Composable
fun HomeScreen(app: YETVApp, onOpen: (VideoItem) -> Unit) {
    var mediaType by remember { mutableIntStateOf(0) } // 0 电影 1 剧集
    var tagIndex by remember { mutableIntStateOf(0) }
    val items = remember { mutableStateListOf<DoubanItem>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val tags = if (mediaType == 0) MOVIE_TAGS else TV_TAGS
    val type = if (mediaType == 0) "movie" else "tv"
    val tag = tags.getOrNull(tagIndex) ?: "热门"

    LaunchedEffect(mediaType, tagIndex) {
        loading = true
        error = ""
        items.clear()
        val res = runCatching { app.repo.douban(type, tag) }
        loading = false
        if (res.isSuccess) {
            items.addAll(res.getOrElse { emptyList() })
            if (items.isEmpty()) {
                error = if (app.api.lastUnauthorized) {
                    "站点要求登录（401），请到「我的」登录后再看"
                } else {
                    "暂无数据，请检查站点配置或网络"
                }
            }
        } else {
            error = "加载失败：${res.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ChipTabs(listOf("电影", "剧集"), mediaType) { mediaType = it; tagIndex = 0 }
        }
        ChipTabs(tags, tagIndex) { tagIndex = it }

        when {
            loading -> LoadingBox()
            error.isNotEmpty() -> EmptyBox(error)
            else -> DoubanGrid(items) { d ->
                scope.launch {
                    val list = runCatching { app.repo.search(d.title) }.getOrNull() ?: emptyList()
                    if (list.isNotEmpty()) {
                        val v = list.firstOrNull { it.title == d.title } ?: list.first()
                        onOpen(v.copy(poster = v.poster.ifEmpty { d.poster }))
                    }
                }
            }
        }
    }
}

// ---------------- 搜索 ----------------

@Composable
fun SearchScreen(app: YETVApp, onOpen: (VideoItem) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<VideoItem>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(submitted) {
        if (submitted.isBlank()) {
            results.clear()
            return@LaunchedEffect
        }
        loading = true
        error = ""
        results.clear()
        val res = runCatching { app.repo.search(submitted) }
        loading = false
        if (res.isSuccess) {
            results.addAll(res.getOrElse { emptyList() })
            if (results.isEmpty()) {
                error = if (app.api.lastUnauthorized) {
                    "站点要求登录（401），请到「我的」登录后再搜索"
                } else {
                    "没有找到结果"
                }
            }
        } else {
            error = "搜索失败：${res.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("搜索影片、剧集…", color = TextSecondary, fontSize = 14.sp) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Surface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Brand,
                    focusedBorderColor = Brand,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            IconButton(onClick = { submitted = keyword.trim() }) {
                Icon(Icons.Default.Search, contentDescription = "搜索", tint = Brand)
            }
        }

        when {
            loading -> LoadingBox("搜索中…")
            error.isNotEmpty() -> EmptyBox(error)
            submitted.isBlank() -> EmptyBox("输入关键词开始搜索")
            else -> VideoGrid(results) { onOpen(it) }
        }
    }
}

// ---------------- 我的 ----------------

@Composable
fun MineScreen(app: YETVApp, onSettings: () -> Unit, onLogout: () -> Unit) {
    val prefs = app.prefs
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("我的", color = TextPrimary, fontSize = 20.sp)

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    if (prefs.username.isEmpty()) "已登录" else "已登录：${prefs.username}",
                    color = TextPrimary,
                    fontSize = 15.sp
                )
                if (prefs.role.isNotEmpty()) {
                    Text("角色：${prefs.role}", color = TextSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { app.repo.logout() }
                            prefs.logout()
                            busy = false
                            onLogout()
                        }
                    },
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text(if (busy) "退出中…" else "退出登录") }
            }
        }

        if (msg.isNotEmpty()) {
            Text(msg, color = Color(0xFFFFC46B), fontSize = 13.sp)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("收藏与历史", color = TextPrimary, fontSize = 15.sp)
                Text(
                    "收藏与播放记录与网页端同步，数据存在你的站点上。",
                    color = TextSecondary, fontSize = 12.sp
                )
            }
        }

        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("应用设置")
        }
    }
}

// ---------------- 设置 ----------------

@Composable
@Composable
fun SettingsScreen(app: YETVApp, onBack: () -> Unit) {
    val prefs = app.prefs
    var bgOn by remember { mutableStateOf(prefs.bgEnabled) }
    var dim by remember { mutableFloatStateOf(prefs.bgDim) }
    var keepOn by remember { mutableStateOf(prefs.keepScreenOn) }
    var land by remember { mutableStateOf(prefs.autoLandscape) }
    var ssl by remember { mutableStateOf(prefs.ignoreSsl) }
    var apiPrefix by remember { mutableStateOf(prefs.apiPrefix) }
    var tip by remember { mutableStateOf("") }

    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = copyToInternal(app.applicationContext, uri, "bg.jpg")
        if (file != null) {
            prefs.bgPath = file.absolutePath
            prefs.bgEnabled = true
            bgOn = true
            tip = "背景图已设置"
        } else {
            tip = "读取图片失败"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 14.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "返回", tint = TextPrimary)
            }
            Text("应用设置", color = TextPrimary, fontSize = 20.sp)
        }

        SettingsCard("背景图") {
            SwitchRow("启用自定义背景图", bgOn) {
                bgOn = it
                prefs.bgEnabled = it
            }
            ActionRow("从相册选择") { pickBg.launch("image/*") }
            if (prefs.bgPath.isNotEmpty()) {
                ActionRow("清除背景图") {
                    prefs.bgPath = ""
                    prefs.bgEnabled = false
                    bgOn = false
                    tip = "已清除背景图"
                }
            }
            SliderRow(
                label = "暗化程度",
                valueText = "${(dim * 100).toInt()}%",
                value = dim,
                range = 0f..0.85f,
                onValue = { dim = it },
                onDone = { prefs.bgDim = dim }
            )
        }

        SettingsCard("播放") {
            SwitchRow("播放时保持屏幕常亮", keepOn) { keepOn = it; prefs.keepScreenOn = it }
            SwitchRow("进入播放自动横屏", land) { land = it; prefs.autoLandscape = it }
            HintText("默认进入是小窗播放，横屏请点播放器右下角的全屏按钮。")
        }

        SettingsCard("观看记录") {
            Text(
                "集数观看进度保存在本机，不上传。",
                color = TextSecondary, fontSize = 12.sp
            )
            ActionRow("清除全部观看记录") {
                clearAllProgress(prefs)
                tip = "观看记录已清除"
            }
        }

        SettingsCard("高级") {
            Text(
                "接口路径前缀（一般留空）",
                color = TextPrimary, fontSize = 14.sp
            )
            OutlinedTextField(
                value = apiPrefix,
                onValueChange = { apiPrefix = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = SurfaceLight,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Brand,
                    focusedBorderColor = Brand,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                )
            )
            ActionRow("保存前缀") {
                prefs.apiPrefix = apiPrefix
                tip = if (prefs.apiPrefix.isEmpty()) "已恢复默认路径" else "已保存前缀设置"
            }
            HintText("若站点把接口挂在子路径下才需要填，例如填 /dl。")
            SwitchRow("忽略 SSL 证书错误", ssl) { ssl = it; prefs.ignoreSsl = it }
            HintText("仅自建证书场景开启，存在安全风险。")
        }

        if (tip.isNotEmpty()) {
            Text(
                tip, color = Color(0xFFFFC46B), fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        Box(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            content()
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text, color = TextPrimary, fontSize = 14.sp,
            modifier = Modifier.weight(1f).padding(end = 10.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Brand,
                checkedTrackColor = Brand.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun ActionRow(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Brand)
    ) {
        Text(text, fontSize = 14.sp)
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    onDone: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextPrimary, fontSize = 14.sp)
            Text(valueText, color = Brand, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = onValue,
            onValueChangeFinished = onDone,
            valueRange = range,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text, color = TextSecondary, fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 清除本机所有观看进度（sp 里 wp_ / wf_ 前缀的键） */
private fun clearAllProgress(prefs: com.yetvplus.nativeapp.data.Prefs) {
    prefs.clearProgress()
}

private fun copyToInternal(context: Context, uri: Uri, name: String): File? {
    return try {
        val out = File(context.filesDir, name)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        out
    } catch (e: Exception) {
        null
    }
}
