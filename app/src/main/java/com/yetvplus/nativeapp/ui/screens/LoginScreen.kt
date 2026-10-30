package com.yetvplus.nativeapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yetvplus.nativeapp.R
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import com.yetvplus.nativeapp.ui.theme.LocalSurface
import com.yetvplus.nativeapp.ui.theme.LocalTextPrimary
import com.yetvplus.nativeapp.ui.theme.LocalTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext

/** 当前应用版本（与 build.gradle 保持一致） */
const val APP_VERSION = "1.0.2"

/**
 * 登录页（启动门）。
 *
 * 站点的 middleware 会保护 api 接口，未登录返回 401，
 * 所以必须先拿到 auth cookie 才能用搜索、详情等功能。
 *
 * localstorage 模式 → 只输密码；数据库模式 → 用户名 + 密码。
 *
 * 布局要点：
 * - 交互卡片垂直居中
 * - 顶部站点名「打字机」效果（打完停 1.5s → 删除 → 再打印），带光标
 * - 背景图用 ContentScale.Crop，输入法弹出时不缩放、不位移
 */
@Composable
fun LoginScreen(app: YETVApp, onSuccess: () -> Unit) {
    val prefs = app.prefs
    // OIDC 按钮要用浏览器打开授权地址，需要 Context。
    // 之前直接用 `context.startActivity` 但这个函数里没有 context，故报 Unresolved reference。
    val context = LocalContext.current

    var username by remember { mutableStateOf(prefs.lastUsername) }
    var password by remember { mutableStateOf(if (prefs.rememberPassword) prefs.savedPassword else "") }
    var rememberPwd by remember { mutableStateOf(prefs.rememberPassword) }
    var showPwd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(if (prefs.storageType.equals("localstorage", true)) 1 else 0) }

    // 注册 / OIDC 开关：启动时从 Prefs 读一次，管理页改动通过 tick 同步
    var regTick by remember { mutableIntStateOf(0) }
    var oidcTick by remember { mutableIntStateOf(0) }
    val registerOn = remember(regTick) { mutableStateOf(prefs.registerEnabled) }
    val oidcOn = remember(oidcTick) { mutableStateOf(prefs.oidcEnabled) }
    var showRegister by remember { mutableStateOf(false) }
    var siteName by remember { mutableStateOf(prefs.siteName) }
    var announcement by remember { mutableStateOf(prefs.announcement) }
    val scope = rememberCoroutineScope()

    // 注意：进入登录页不发起任何网络请求。
    // 输入框与登录按钮立刻渲染出来，只有点「登录」才真正访问站点。

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景图：用户选过就用用户的，否则用内置默认图。
        // Crop 保证铺满不变形；输入法弹出时布局不动（Manifest 里是 adjustNothing）。
        if (prefs.bgPath.isNotEmpty()) {
            AppBackground(prefs.bgPath, prefs.bgDim)
        } else {
            Image(
                painter = painterResource(id = R.drawable.bg_default),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 压暗一点，保证卡片上的文字可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = prefs.bgDim.coerceIn(0f, 0.85f)))
            )
        }

        // 内容整体垂直居中
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 站点名：打字机效果
            TypewriterTitle(text = siteName.uppercase().ifEmpty { "YETVPLUS" })

            Spacer(modifier = Modifier.height(30.dp))

            if (showRegister) {
                RegisterCard(
                    busy = busy,
                    onBack = { showRegister = false },
                    onSubmit = { u, p ->
                        if (u.isBlank() || p.isBlank()) {
                            error = "请填写用户名和密码"
                            return@RegisterCard
                        }
                        busy = true
                        error = ""
                        scope.launch {
                            val err = runCatching { app.repo.register(u.trim(), p) }
                                .getOrElse { it.message ?: "注册失败" }
                            busy = false
                            if (err.isBlank()) {
                                prefs.username = u.trim()
                                prefs.lastUsername = u.trim()
                                onSuccess()
                            } else {
                                error = err
                            }
                        }
                    }
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = LocalSurface.current.copy(alpha = 0.94f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 「登录」标题：输入框上方居中、标准粗体
                        Text(
                            "登 录",
                            color = LocalTextPrimary.current,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 单密码模式不显示用户名
                        if (mode == 0) {
                            LoginField(
                                value = username,
                                onValueChange = { username = it },
                                label = "用户名",
                                icon = {
                                    Icon(
                                        Icons.Default.Person,
                                        null,
                                        tint = LocalTextSecondary.current
                                    )
                                }
                            )
                        }

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码", fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (showPwd) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, null, tint = LocalTextSecondary.current)
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPwd = !showPwd }) {
                                    Icon(
                                        if (showPwd) Icons.Default.Visibility
                                        else Icons.Default.VisibilityOff,
                                        contentDescription = "显示密码",
                                        tint = LocalTextSecondary.current
                                    )
                                }
                            },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                containerColor = Color.Transparent,
                                focusedTextColor = LocalTextPrimary.current,
                                unfocusedTextColor = LocalTextPrimary.current,
                                cursorColor = LocalBrand.current,
                                focusedBorderColor = LocalBrand.current,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
                            )
                        )

                        // 记住密码
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { rememberPwd = !rememberPwd },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberPwd,
                                onCheckedChange = { rememberPwd = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = LocalBrand.current,
                                    uncheckedColor = LocalTextSecondary.current
                                )
                            )
                            Text("记住密码", color = LocalTextPrimary.current, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (password.isBlank()) {
                                    error = "请输入密码"
                                    return@Button
                                }
                                if (mode == 0 && username.isBlank()) {
                                    error = "请输入用户名"
                                    return@Button
                                }
                                busy = true
                                error = ""
                                scope.launch {
                                    val err = runCatching {
                                        app.repo.login(username.trim(), password, mode == 1)
                                    }.getOrElse { it.message ?: "登录失败" }
                                    busy = false
                                    if (err.isBlank()) {
                                        prefs.username = username.trim()
                                        prefs.lastUsername = username.trim()
                                        prefs.rememberPassword = rememberPwd
                                        if (rememberPwd) prefs.savedPassword = password
                                        onSuccess()
                                    } else {
                                        error = err
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LocalBrand.current)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text("登 录", fontSize = 15.sp)
                            }
                        }

                        // 注册开关用可观察状态：管理页一改这里立刻出现，不用重启
                        if (registerOn.value) {
                            Text(
                                "没有账号？注册",
                                color = LocalBrand.current,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clickable { showRegister = true; error = "" }
                            )
                        }

                        // OIDC 登录（管理页开启后出现，点按钮用浏览器打开授权页）
                        if (oidcOn.value) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val url = prefs.oidcAuthUrl
                                    if (url.isNotBlank()) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(prefs.oidcButtonText.ifEmpty { "其他登录" })
                            }
                        }
                    }
                }
            }

            if (error.isNotEmpty()) {
                Text(
                    error,
                    color = Color(0xFFFF8A80),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }

            // 站点公告
            if (announcement.isNotBlank()) {
                Text(
                    announcement,
                    color = LocalTextSecondary.current.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 22.dp)
                )
            }
        }

        // 版本号
        Text(
            "v$APP_VERSION",
            color = LocalTextSecondary.current.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
        )
    }
}

/**
 * 打字机效果的标题。
 * 逐字打印 → 停留 1.5 秒 → 逐字删除 → 短暂停顿 → 再打印。
 * 光标始终闪烁。
 */
@Composable
private fun TypewriterTitle(text: String) {
    var shown by remember { mutableStateOf("") }
    var cursor by remember { mutableStateOf(true) }

    // 打字 / 删除循环
    LaunchedEffect(text) {
        if (text.isEmpty()) return@LaunchedEffect
        while (true) {
            for (i in 1..text.length) {
                shown = text.substring(0, i)
                delay(140)
            }
            delay(1500)          // 打完停留 1.5 秒
            for (i in text.length downTo 0) {
                shown = text.substring(0, i)
                delay(70)
            }
            delay(450)           // 删完稍作停顿再开始下一轮
        }
    }

    // 光标闪烁
    LaunchedEffect(Unit) {
        while (true) {
            cursor = !cursor
            delay(530)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            shown,
            color = LocalTextPrimary.current,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp
        )
        // 光标：用固定宽度占位，避免文字长度变化时抖动
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .width(3.dp)
                .height(26.dp)
                .background(if (cursor) LocalBrand.current else Color.Transparent)
        )
    }
}

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: @Composable () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        leadingIcon = icon,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            containerColor = Color.Transparent,
            focusedTextColor = LocalTextPrimary.current,
            unfocusedTextColor = LocalTextPrimary.current,
            cursorColor = LocalBrand.current,
            focusedBorderColor = LocalBrand.current,
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f)
        )
    )
}

@Composable
private fun RegisterCard(
    busy: Boolean,
    onBack: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = LocalSurface.current.copy(alpha = 0.94f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("注册账号", color = LocalTextPrimary.current, fontSize = 16.sp)
            LoginField(u, { u = it }, "用户名") {
                Icon(Icons.Default.Person, null, tint = LocalTextSecondary.current)
            }
            LoginField(p, { p = it }, "密码") {
                Icon(Icons.Default.Lock, null, tint = LocalTextSecondary.current)
            }
            LoginField(p2, { p2 = it }, "确认密码") {
                Icon(Icons.Default.Lock, null, tint = LocalTextSecondary.current)
            }

            if (localError.isNotEmpty()) {
                Text(localError, color = Color(0xFFFF8A80), fontSize = 12.sp)
            }

            Button(
                onClick = {
                    when {
                        u.isBlank() || p.isBlank() -> localError = "请填写完整"
                        p != p2 -> localError = "两次密码不一致"
                        p.length < 6 -> localError = "密码至少 6 位"
                        else -> {
                            localError = ""
                            onSubmit(u, p)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalBrand.current)
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("注 册", fontSize = 15.sp)
                }
            }

            Text(
                "返回登录",
                color = LocalBrand.current,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { onBack() }
            )
        }
    }
}
