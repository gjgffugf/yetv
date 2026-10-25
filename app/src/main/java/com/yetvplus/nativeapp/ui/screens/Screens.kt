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
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Spacer
import com.yetvplus.nativeapp.ui.UiState
import androidx.compose.foundation.layout.width
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.yetvplus.nativeapp.data.WatchRecord
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.yetvplus.nativeapp.ui.theme.THEME_PRESETS
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.weight
import androidx.compose.ui.layout.ContentScale

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
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val tags = if (mediaType == 0) MOVIE_TAGS else TV_TAGS
    val type = if (mediaType == 0) "movie" else "tv"
    val tag = tags.getOrNull(tagIndex) ?: "热门"

    LaunchedEffect(mediaType, tagIndex, retry) {
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
            val m = res.exceptionOrNull()?.message ?: "未知错误"
            error = if (m.contains("timeout", true)) {
                "加载超时，可点下方按钮重试"
            } else {
                "加载失败：$m"
            }
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
            error.isNotEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(error, color = Color(0xFFFFC46B), fontSize = 13.sp,
                    textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { retry++ },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalBrand.current)
                ) { Text("重试") }
            }
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
    val prefs = app.prefs
    // 关键词与结果放在进程级单例里，详情页返回后不丢
    var keyword by remember { mutableStateOf(UiState.keyword) }
    var submitted by remember { mutableStateOf(UiState.submitted) }
    val results = remember { mutableStateListOf<VideoItem>().apply { addAll(UiState.results) } }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var attempt by remember { mutableIntStateOf(0) }
    var history by remember { mutableStateOf(prefs.searchHistory) }
    val scope = rememberCoroutineScope()

    fun run(q: String) {
        val t = q.trim()
        if (t.isEmpty()) return
        keyword = t
        UiState.keyword = t
        prefs.addSearchHistory(t)
        history = prefs.searchHistory
        submitted = t
        UiState.submitted = t
        UiState.searched = true
    }

    LaunchedEffect(submitted, attempt) {
        if (submitted.isBlank()) {
            results.clear()
            UiState.results = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = ""
        results.clear()
        val res = runCatching { app.repo.search(submitted) }
        loading = false
        if (res.isSuccess) {
            val list = res.getOrElse { emptyList() }
            results.addAll(list)
            UiState.results = list
            if (results.isEmpty()) {
                error = if (app.api.lastUnauthorized) {
                    "站点要求登录（401），请登录后重试"
                } else {
                    "没有找到结果"
                }
            }
        } else {
            // 超时 / 网络失败：给一个「重试」按钮，Cloudflare 偶发超时很常见
            val msg = res.exceptionOrNull()?.message ?: "未知错误"
            error = if (msg.contains("timeout", true)) {
                "请求超时，可点下方按钮重试"
            } else {
                "搜索失败：$msg"
            }
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
                onValueChange = { keyword = it; UiState.keyword = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("搜索影片、剧集…", color = TextSecondary, fontSize = 14.sp) },
                shape = RoundedCornerShape(12.dp),
                // 键盘回车直接搜索
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { run(keyword) }),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Surface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = LocalBrand.current,
                    focusedBorderColor = LocalBrand.current,
                    unfocusedBorderColor = Color.Transparent
                )
            )
            IconButton(onClick = { run(keyword) }) {
                Icon(Icons.Default.Search, contentDescription = "搜索", tint = LocalBrand.current)
            }
        }

        // 搜索历史
        if (history.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("搜索历史", color = TextSecondary, fontSize = 12.sp,
                    modifier = Modifier.weight(1f))
                Text("清空", color = LocalBrand.current, fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        prefs.clearSearchHistory()
                        history = emptyList()
                    })
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { h ->
                    Box(
                        modifier = Modifier
                            .background(SurfaceLight, RoundedCornerShape(14.dp))
                            .clickable { run(h) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(h, color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        when {
            loading -> LoadingBox("搜索中…")
            error.isNotEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(error, color = Color(0xFFFFC46B), fontSize = 13.sp,
                    textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { attempt++ },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalBrand.current)
                ) { Text("重试") }
            }
            submitted.isBlank() -> EmptyBox("输入关键词开始搜索")
            else -> VideoGrid(results) { onOpen(it) }
        }
    }
}

// ---------------- 我的 ----------------

@Composable
fun MineScreen(
    app: YETVApp,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
    onOpenRecord: (WatchRecord) -> Unit = {}
) {
    val prefs = app.prefs
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // 每次进入重新读，从播放页返回后能立刻看到新记录
    var records by remember { mutableStateOf(prefs.watchRecords()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { records = prefs.watchRecords() }

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

        // ---- 本地观看记录 ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("观看记录（${records.size}）", color = TextPrimary, fontSize = 16.sp,
                modifier = Modifier.weight(1f))
            if (records.isNotEmpty()) {
                Text("清空", color = LocalBrand.current, fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        prefs.clearWatchRecords()
                        records = emptyList()
                    })
            }
        }

        if (records.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("还没有观看记录", color = TextPrimary, fontSize = 14.sp)
                    Text(
                        "播放任意一集后会自动记录进度，数据只存在本机。",
                        color = TextSecondary, fontSize = 12.sp
                    )
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    records.forEach { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenRecord(r) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(46.dp)
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceLight)
                            ) {
                                if (r.poster.isNotEmpty()) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(r.poster)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(r.title.ifEmpty { "未命名" }, color = TextPrimary,
                                    fontSize = 14.sp, maxLines = 2)
                                Text(
                                    "看到第 ${r.episode + 1} 集",
                                    color = TextSecondary, fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            Text("删除", color = TextSecondary, fontSize = 11.sp,
                                modifier = Modifier.clickable {
                                    prefs.removeWatchRecord(r.key)
                                    records = prefs.watchRecords()
                                })
                        }
                    }
                }
            }
        }

        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("应用设置")
        }
    }
}

// ---------------- 设置 ----------------

@Composable
fun SettingsScreen(app: YETVApp, onBack: () -> Unit) {
    val prefs = app.prefs
    var bgOn by remember { mutableStateOf(prefs.bgEnabled) }
    var dim by remember { mutableFloatStateOf(prefs.bgDim) }
    var keepOn by remember { mutableStateOf(prefs.keepScreenOn) }
    var land by remember { mutableStateOf(prefs.autoLandscape) }
    var tip by remember { mutableStateOf("") }
    var themeArgb by remember { mutableIntStateOf(prefs.themeColor) }

    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = copyToInternal(app.applicationContext, uri, "bg.jpg")
        if (file != null) {
            prefs.bgPath = file.absolutePath
            prefs.bgEnabled = true
            bgOn = true
            tip = "背景图已选择，记得点「保存背景设置」"
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

        // ---- 个性化 ----
        SettingsCard("个性化") {
            Text("软件默认色", color = TextPrimary, fontSize = 14.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                THEME_PRESETS.forEach { (name, argb) ->
                    val selected = themeArgb == argb
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(Color(argb))
                            .then(
                                if (selected) {
                                    Modifier.background(Color.Transparent)
                                } else Modifier
                            )
                            .clickable {
                                themeArgb = argb
                                prefs.themeColor = argb
                                tip = "已切换为$name"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
            ActionRow("恢复默认色") {
                themeArgb = 0
                prefs.themeColor = 0
                tip = "已恢复默认色"
            }
        }

        // ---- 背景图 ----
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
                onDone = { }
            )
            // 明确点保存才写入
            ActionRow("保存背景设置") {
                prefs.bgEnabled = bgOn
                prefs.bgDim = dim
                tip = "背景设置已保存"
            }
        }

        // ---- 播放 ----
        SettingsCard("播放") {
            SwitchRow("播放时保持屏幕常亮", keepOn) { keepOn = it; prefs.keepScreenOn = it }
            SwitchRow("进入播放自动横屏", land) { land = it; prefs.autoLandscape = it }
            HintText("默认进入是小窗播放，横屏请点播放器右下角的全屏按钮。")
        }

        // ---- 缓存与记录 ----
        SettingsCard("缓存与记录") {
            Text(
                "首页榜单本地缓存 24 小时，观看进度与记录仅存本机。",
                color = TextSecondary, fontSize = 12.sp
            )
            ActionRow("清除首页缓存") {
                prefs.clearCache()
                tip = "首页缓存已清除"
            }
            ActionRow("清除全部观看记录") {
                prefs.clearProgress()
                prefs.clearWatchRecords()
                tip = "观看记录已清除"
            }
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
                checkedThumbColor = LocalBrand.current,
                checkedTrackColor = LocalBrand.current.copy(alpha = 0.5f)
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
        colors = ButtonDefaults.buttonColors(containerColor = LocalBrand.current)
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
            Text(valueText, color = LocalBrand.current, fontSize = 13.sp)
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
