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
import com.yetvplus.nativeapp.ui.theme.LocalSurface
import com.yetvplus.nativeapp.ui.theme.LocalTextPrimary
import com.yetvplus.nativeapp.ui.theme.LocalTextSecondary
import kotlinx.coroutines.launch
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size

private val TABS = listOf("站点配置", "视频源", "分类", "用户", "高级")

@Composable
fun AdminScreen(app: YETVApp) {
    val prefs = app.prefs
    var tab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "管理面板",
            color = LocalTextPrimary.current,
            fontSize = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        if (!prefs.isLoggedIn()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "请先在登录页用站长账号登录\n登录后再回到本页操作",
                    color = LocalTextSecondary.current, fontSize = 14.sp,
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
                    color = LocalTextSecondary.current, fontSize = 14.sp,
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
    // 正在编辑的源下标；null 表示新增模式
    var editingIndex by remember { mutableIntStateOf(-1) }
    // 待删除的源下标；非负时弹确认框
    var pendingDelete by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        val r = runCatching { app.repo.sources() }.getOrElse { emptyList() }
        loading = false
        list.clear()
        list.addAll(r)
    }

    if (loading) { LoadingBox(); return }

    // 删除确认
    if (pendingDelete >= 0) {
        val target = list.getOrNull(pendingDelete)
        ConfirmDialog(
            title = "删除视频源",
            message = "确定要删除「${target?.name?.ifEmpty { target.key } ?: ""}」吗？此操作不可撤销。",
            onConfirm = {
                val t = list.getOrNull(pendingDelete)
                pendingDelete = -1
                if (t != null) {
                    scope.launch {
                        val payload = JsonObject().apply {
                            addProperty("key", t.key)
                            addProperty("deleted", true)
                        }
                        val err = runCatching { app.repo.saveSource(payload) }
                            .getOrElse { it.message ?: "删除失败" }
                        if (err.isEmpty()) {
                            list.remove(t)
                            if (editingIndex == list.size) editingIndex = -1
                            tip = "已删除"
                        } else tip = err
                    }
                }
            },
            onDismiss = { pendingDelete = -1 }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("已配置 ${list.size} 个视频源", color = LocalTextPrimary.current, fontSize = 14.sp)
        list.forEachIndexed { idx, s ->
            Card(
                colors = CardDefaults.cardColors(containerColor = LocalSurface.current),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            s.name.ifEmpty { s.key },
                            color = LocalTextPrimary.current,
                            fontSize = 14.sp
                        )
                        Text(
                            s.api,
                            color = LocalTextSecondary.current,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }
                    // 右侧：编辑 / 删除
                    Text(
                        "编辑",
                        color = LocalBrand.current,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable {
                                editingIndex = idx
                                key = s.key
                                name = s.name
                                api = s.api
                                detail = s.detail
                                tip = "正在编辑「${s.name.ifEmpty { s.key }}」，改完点保存"
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    Text(
                        "删除",
                        color = Color(0xFFE5484D),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { pendingDelete = idx }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Text(
            if (editingIndex >= 0) "编辑视频源" else "添加视频源",
            color = LocalTextPrimary.current,
            fontSize = 14.sp
        )
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
                        val item = SourceItem(key.trim(), name.trim(), api.trim(), detail.trim())
                        if (editingIndex >= 0 && editingIndex < list.size) {
                            list[editingIndex] = item
                            tip = "已保存修改"
                        } else {
                            list.add(item)
                            tip = "已保存"
                        }
                        editingIndex = -1
                        key = ""; name = ""; api = ""; detail = ""
                    } else tip = err
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (editingIndex >= 0) "保存修改" else "保存视频源") }

        if (editingIndex >= 0) {
            Text(
                "取消编辑",
                color = LocalTextSecondary.current,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        editingIndex = -1
                        key = ""; name = ""; api = ""; detail = ""
                        tip = ""
                    }
                    .padding(vertical = 6.dp)
            )
        }

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
        Text("添加自定义分类", color = LocalTextPrimary.current, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { type = "movie" }) { Text("电影") }
            Button(onClick = { type = "tv" }) { Text("剧集") }
        }
        Text("当前类型：$type", color = LocalTextSecondary.current, fontSize = 12.sp)
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
        Text("用户 ${users.size} 个", color = LocalTextPrimary.current, fontSize = 14.sp)
        users.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = LocalSurface.current),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "${item.username}（${item.role}）${if (item.banned) " · 已封禁" else ""}",
                    color = LocalTextPrimary.current, fontSize = 13.sp,
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
        Text("配置文件（config.json）", color = LocalTextPrimary.current, fontSize = 14.sp)
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

        Text("配置订阅", color = LocalTextPrimary.current, fontSize = 14.sp)
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
        Text(text, color = LocalTextPrimary.current, fontSize = 14.sp)
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

// ---------------- 通用确认弹窗 ----------------

/** 危险操作前的确认弹窗（删除视频源等） */
@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定删除",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(18.dp),
            color = LocalSurface.current,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    title,
                    color = LocalTextPrimary.current,
                    fontSize = 17.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    message,
                    color = LocalTextSecondary.current,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LocalSurfaceLight.current
                            )
                        ) {
                            Text("取消", color = LocalTextPrimary.current, fontSize = 13.sp)
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = {
                                onConfirm()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE5484D)
                            )
                        ) { Text(confirmText, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}
