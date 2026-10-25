package com.yetvplus.nativeapp.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.data.SourceItem
import com.yetvplus.nativeapp.data.UserItem
import com.yetvplus.nativeapp.ui.screens.ChipTabs
import com.yetvplus.nativeapp.ui.screens.LoadingBox
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.Surface
import com.yetvplus.nativeapp.ui.theme.TextPrimary
import com.yetvplus.nativeapp.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.yetvplus.nativeapp.data.RawResponse
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable

private val TABS = listOf("站点配置", "视频源", "分类", "用户", "高级", "诊断")

@Composable
fun AdminScreen(app: YETVApp) {
    val prefs = app.prefs
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "管理面板",
            color = TextPrimary,
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        if (!prefs.isLoggedIn()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "请先在登录页用站长账号登录\n登录后再回到本页操作",
                    color = TextSecondary, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return
        }

        val role = prefs.role
        if (role.isNotEmpty() && role != "owner" && role != "admin") {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "当前账号角色为「$role」，无管理权限\n请用站长（owner）或管理员账号登录",
                    color = TextSecondary, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            return
        }

        ChipTabs(TABS, tab) { tab = it }

        // 顶部栏目切换带横向淡入过渡
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 8 }) togetherWith
                        (fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 8 })
            },
            label = "adminTab"
        ) { t ->
            when (t) {
                0 -> SiteTab(app)
                1 -> SourceTab(app)
                2 -> CategoryTab(app)
                3 -> UserTab(app)
                4 -> AdvancedTab(app)
                5 -> DiagnoseTab(app)
            }
        }
    }
}

// ---------------- 站点配置 ----------------

@Composable
private fun SiteTab(app: YETVApp) {
    var name by remember { mutableStateOf("") }
    var announcement by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        val cfg = runCatching { app.repo.adminSiteConfig() }.getOrNull()
        loading = false
        cfg?.let {
            name = it.siteName
            announcement = it.announcement
            register = it.enableRegister
            comments = it.enableComments
        }
    }

    if (loading) { LoadingBox(); return }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text("站点名称", fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = announcement, onValueChange = { announcement = it },
            label = { Text("站点公告", fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth(), minLines = 3)
        SwitchRow2("开放注册", register) { register = it }
        SwitchRow2("启用评论", comments) { comments = it }
        Button(
            onClick = {
                val payload = JsonObject().apply {
                    addProperty("SiteName", name)
                    addProperty("Announcement", announcement)
                    addProperty("EnableRegistration", register)
                    addProperty("EnableComments", comments)
                }
                scope.launch {
                    tip = runCatching { app.repo.saveSite(payload) }
                        .getOrElse { it.message ?: "保存失败" }
                        .ifEmpty { "已保存" }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存站点配置") }

        if (tip.isNotEmpty()) Text(tip, color = Color(0xFFFFC46B), fontSize = 13.sp)
    }
}

// ---------------- 视频源 ----------------

@Composable
private fun SourceTab(app: YETVApp) {
    val list = remember { mutableStateListOf<SourceItem>() }
    var key by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var api by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        val r = runCatching { app.repo.sources() }.getOrElse { emptyList() }
        loading = false
        list.clear()
        list.addAll(r)
    }

    if (loading) { LoadingBox(); return }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("已配置 ${list.size} 个视频源", color = TextPrimary, fontSize = 14.sp)
        list.forEach { s ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(s.name.ifEmpty { s.key }, color = TextPrimary, fontSize = 14.sp)
                    Text(s.api, color = TextSecondary, fontSize = 11.sp, maxLines = 2)
                }
            }
        }

        Text("添加视频源", color = TextPrimary, fontSize = 14.sp)
        OutlinedTextField(value = key, onValueChange = { key = it },
            label = { Text("Key（小写字母/数字）", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text("名称", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = api, onValueChange = { api = it },
            label = { Text("API 地址", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = detail, onValueChange = { detail = it },
            label = { Text("Detail 地址（可选）", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                if (key.isBlank() || api.isBlank()) { tip = "Key 与 API 不能为空"; return@Button }
                val payload = JsonObject().apply {
                    addProperty("key", key.trim())
                    addProperty("name", name.trim())
                    addProperty("api", api.trim())
                    addProperty("detail", detail.trim())
                }
                scope.launch {
                    val err = runCatching { app.repo.saveSource(payload) }
                        .getOrElse { it.message ?: "保存失败" }
                    if (err.isEmpty()) {
                        list.add(SourceItem(key.trim(), name.trim(), api.trim(), detail.trim()))
                        key = ""; name = ""; api = ""; detail = ""
                        tip = "已保存"
                    } else tip = err
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存视频源") }

        if (tip.isNotEmpty()) Text(tip, color = Color(0xFFFFC46B), fontSize = 13.sp)
    }
}

// ---------------- 分类 ----------------

@Composable
private fun CategoryTab(app: YETVApp) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("movie") }
    var query by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("添加自定义分类", color = TextPrimary, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { type = "movie" }) { Text("电影") }
            Button(onClick = { type = "tv" }) { Text("剧集") }
        }
        Text("当前类型：$type", color = TextSecondary, fontSize = 12.sp)
        OutlinedTextField(value = name, onValueChange = { name = it },
            label = { Text("显示名称", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = query, onValueChange = { query = it },
            label = { Text("搜索关键词（如 华语 / 哈利波特）", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth())
        Button(
            onClick = {
                if (name.isBlank()) { tip = "请填名称"; return@Button }
                val payload = JsonObject().apply {
                    addProperty("name", name.trim())
                    addProperty("type", type)
                    addProperty("query", query.trim().ifEmpty { name.trim() })
                }
                scope.launch {
                    tip = runCatching { app.repo.saveCategory(payload) }
                        .getOrElse { it.message ?: "保存失败" }
                        .ifEmpty { "已保存" }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存分类") }

        if (tip.isNotEmpty()) Text(tip, color = Color(0xFFFFC46B), fontSize = 13.sp)
    }
}

// ---------------- 用户 ----------------

@Composable
private fun UserTab(app: YETVApp) {
    val users = remember { mutableStateListOf<UserItem>() }
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("user") }
    var tip by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        val r = runCatching { app.repo.adminUsers() }.getOrElse { emptyList() }
        loading = false
        users.clear()
        users.addAll(r)
    }

    if (loading) { LoadingBox(); return }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("用户 ${users.size} 个", color = TextPrimary, fontSize = 14.sp)
        users.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${item.username}（${item.role}）${if (item.banned) " · 已封禁" else ""}",
                    color = TextPrimary, fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedTextField(value = u, onValueChange = { u = it },
            label = { Text("新用户名", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = p, onValueChange = { p = it },
            label = { Text("密码", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { role = "user" }) { Text("普通用户") }
            Button(onClick = { role = "admin" }) { Text("管理员") }
        }
        Button(
            onClick = {
                if (u.isBlank() || p.isBlank()) { tip = "请填写完整"; return@Button }
                val payload = JsonObject().apply {
                    addProperty("username", u.trim())
                    addProperty("password", p)
                    addProperty("role", role)
                }
                scope.launch {
                    val err = runCatching { app.repo.saveUser(payload) }
                        .getOrElse { it.message ?: "保存失败" }
                    if (err.isEmpty()) {
                        users.add(UserItem(u.trim(), role))
                        u = ""; p = ""
                        tip = "已保存"
                    } else tip = err
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("添加用户") }

        if (tip.isNotEmpty()) Text(tip, color = Color(0xFFFFC46B), fontSize = 13.sp)
    }
}

// ---------------- 高级 ----------------

@Composable
private fun AdvancedTab(app: YETVApp) {
    var json by remember { mutableStateOf("") }
    var subUrl by remember { mutableStateOf("") }
    var autoUpdate by remember { mutableStateOf(false) }
    var tip by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("配置文件（config.json）", color = TextPrimary, fontSize = 14.sp)
        OutlinedTextField(
            value = json, onValueChange = { json = it },
            label = { Text("粘贴完整 JSON 配置", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(), minLines = 6
        )
        Button(
            onClick = {
                if (json.isBlank()) { tip = "内容为空"; return@Button }
                scope.launch {
                    tip = runCatching { app.repo.saveConfigFile(json) }
                        .getOrElse { it.message ?: "保存失败" }
                        .ifEmpty { "已提交" }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("提交配置文件") }

        Text("配置订阅", color = TextPrimary, fontSize = 14.sp)
        OutlinedTextField(value = subUrl, onValueChange = { subUrl = it },
            label = { Text("订阅地址", fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth())
        SwitchRow2("自动更新", autoUpdate) { autoUpdate = it }
        Button(
            onClick = {
                if (subUrl.isBlank()) { tip = "请填订阅地址"; return@Button }
                scope.launch {
                    tip = runCatching { app.repo.pullSubscription(subUrl.trim(), autoUpdate) }
                        .getOrElse { it.message ?: "拉取失败" }
                        .ifEmpty { "已拉取" }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("拉取订阅") }

        if (tip.isNotEmpty()) Text(tip, color = Color(0xFFFFC46B), fontSize = 13.sp)
    }
}

@Composable
private fun SwitchRow2(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = TextPrimary, fontSize = 14.sp)
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

// ---------------- 接口诊断 ----------------

/**
 * 接口诊断：逐个请求关键接口，把状态码、耗时、原始响应文本直接显示出来。
 *
 * 用途：抓不到包时，用它一次性看清"到底哪个接口通、返回什么结构"，
 * 比猜字段可靠。响应可点击复制 / 分享出去。
 */
@Composable
private fun DiagnoseTab(app: YETVApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    val results = remember { mutableStateListOf<RawResponse>() }

    val paths = listOf(
        "/api/admin/config",
        "/api/server-config",
        "/api/config/sources",
        "/api/config/custom_category"
    )

    fun run() {
        scope.launch {
            running = true
            results.clear()
            for (p in paths) {
                val r = withContext(Dispatchers.IO) { app.api.getRaw(p) }
                results.add(r)
            }
            running = false
        }
    }

    LaunchedEffect(Unit) { run() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("接口诊断", color = TextPrimary, fontSize = 17.sp)
        Text(
            "下面列出各接口的真实状态码与原始响应。点「复制」可把内容发给开发者校准字段。",
            color = TextSecondary, fontSize = 12.sp
        )
        Button(
            onClick = { run() },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (running) "请求中…" else "重新诊断") }

        results.forEach { r ->
            val okColor = if (r.ok) Color(0xFF4CAF50) else Color(0xFFE5484D)
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(okColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (r.error) "ERR" else r.code.toString(),
                                color = Color.White, fontSize = 11.sp
                            )
                        }
                        Text(
                            " ${r.ms}ms",
                            color = TextSecondary, fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "复制",
                            color = LocalBrand.current, fontSize = 12.sp,
                            modifier = Modifier.clickable {
                                val cm = context.getSystemService(
                                    android.content.Context.CLIPBOARD_SERVICE
                                ) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "resp", "URL: ${r.url}\nCODE: ${r.code}\n${r.body}"
                                    )
                                )
                                android.widget.Toast.makeText(
                                    context, "已复制", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                    Text(
                        r.url.substringAfter("/api"),
                        color = TextPrimary, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    // 原始响应：截断显示，避免超长
                    val shown = if (r.body.length > 600) r.body.take(600) + "…（已截断）" else r.body
                    Text(
                        shown.ifEmpty { "（空响应体）" },
                        color = TextSecondary, fontSize = 11.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
