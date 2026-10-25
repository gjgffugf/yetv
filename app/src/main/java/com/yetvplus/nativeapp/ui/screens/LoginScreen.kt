package com.yetvplus.nativeapp.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.Surface
import com.yetvplus.nativeapp.ui.theme.TextPrimary
import com.yetvplus.nativeapp.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * 登录页（启动门）。
 *
 * 站点的 middleware 会保护所有 api 接口，未登录返回 401，
 * 所以必须先拿到 auth cookie 才能用搜索、详情等功能。
 *
 * localstorage 模式 → 只输密码；数据库模式 → 用户名 + 密码。
 */
@Composable
fun LoginScreen(app: YETVApp, onSuccess: () -> Unit, onSkip: () -> Unit = onSuccess) {
    val prefs = app.prefs

    var username by remember { mutableStateOf(prefs.lastUsername) }
    var password by remember { mutableStateOf(if (prefs.rememberPassword) prefs.savedPassword else "") }
    var rememberPwd by remember { mutableStateOf(prefs.rememberPassword) }
    var showPwd by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(if (prefs.storageType.equals("localstorage", true)) 1 else 0) }
    var showRegister by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var siteName by remember { mutableStateOf(prefs.siteName) }
    var announcement by remember { mutableStateOf(prefs.announcement) }
    val scope = rememberCoroutineScope()

    // 进入时拉一次 server-config：拿站点名、公告、存储类型、注册开关
    LaunchedEffect(Unit) {
        checking = true
        val cfg = runCatching { app.repo.serverConfig() }.getOrNull()
        checking = false
        cfg?.let {
            siteName = it.siteName.ifEmpty { "YETVPLUS" }
            announcement = it.announcement
            mode = if (it.singlePasswordMode) 1 else 0
            prefs.siteName = siteName
            prefs.announcement = it.announcement
            prefs.storageType = it.storageType
            prefs.registerEnabled = it.enableRegister
        }
        if (siteName.isEmpty()) siteName = "YETVPLUS"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 自定义背景图（设置页开启后生效）
        if (prefs.bgEnabled && prefs.bgPath.isNotEmpty()) {
            AppBackground(prefs.bgPath, prefs.bgDim)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // 站点名
            Text(
                siteName.uppercase(),
                color = TextPrimary,
                fontSize = 30.sp,
                letterSpacing = 3.sp
            )
            Text(
                "原生客户端",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(34.dp))

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
                        containerColor = Surface.copy(alpha = 0.92f)
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (checking) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Brand,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            // 单密码模式不显示用户名
                            if (mode == 0) {
                                LoginField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = "用户名",
                                    icon = { Icon(Icons.Default.Person, null, tint = TextSecondary) }
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
                                    Icon(Icons.Default.Lock, null, tint = TextSecondary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { showPwd = !showPwd }) {
                                        Icon(
                                            if (showPwd) Icons.Default.Visibility
                                            else Icons.Default.VisibilityOff,
                                            contentDescription = "显示密码",
                                            tint = TextSecondary
                                        )
                                    }
                                },
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    containerColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = Brand,
                                    focusedBorderColor = Brand,
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
                                        checkedColor = Brand,
                                        uncheckedColor = TextSecondary
                                    )
                                )
                                Text("记住密码", color = TextPrimary, fontSize = 13.sp)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Brand)
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

                            if (prefs.registerEnabled) {
                                Text(
                                    "没有账号？注册",
                                    color = Brand,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .clickable { showRegister = true; error = "" }
                                )
                            }

                            // 部分接口（如弹幕搜索）无需登录即可访问，允许跳过
                            Text(
                                "先随便看看 ›",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 2.dp)
                                    .clickable { onSkip() }
                            )
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

            Spacer(modifier = Modifier.height(28.dp))

            // 站点公告
            if (announcement.isNotBlank()) {
                Text(
                    announcement,
                    color = TextSecondary.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        // 版本号
        Text(
            "v1.0.0",
            color = TextSecondary.copy(alpha = 0.6f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
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
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Brand,
            focusedBorderColor = Brand,
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
        colors = CardDefaults.cardColors(containerColor = Surface.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("注册账号", color = TextPrimary, fontSize = 16.sp)
            LoginField(u, { u = it }, "用户名") {
                Icon(Icons.Default.Person, null, tint = TextSecondary)
            }
            LoginField(p, { p = it }, "密码") {
                Icon(Icons.Default.Lock, null, tint = TextSecondary)
            }
            LoginField(p2, { p2 = it }, "确认密码") {
                Icon(Icons.Default.Lock, null, tint = TextSecondary)
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
                colors = ButtonDefaults.buttonColors(containerColor = Brand)
            ) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("注 册", fontSize = 15.sp)
                }
            }

            Text(
                "返回登录",
                color = Brand,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { onBack() }
            )
        }
    }
}
