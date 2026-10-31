package com.yetvplus.nativeapp.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.VideoView
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.ui.screens.AppBackground
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import com.yetvplus.nativeapp.ui.theme.LocalTextPrimary
import com.yetvplus.nativeapp.ui.theme.LocalTextSecondary
import com.yetvplus.nativeapp.ui.theme.YETVTheme
import kotlinx.coroutines.delay

/**
 * 启动页。
 *
 * 两件事：
 * 1. **开屏素材**（可选）：放了图片或视频就先展示一下，没放就跳过不停留。
 * 2. **启动加载**：依次完成「连接服务器」「加载注册配置」两步，
 *    显示进度条与当前步骤文案。
 *
 * **24 小时缓存**：上次完整加载后 24 小时内不再显示这个加载页，直接进主页。
 *
 * 素材路径（动态查找，目录为空也能编译）：
 * - 开屏图片：`res/drawable-nodpi/splash_image.png`（或 .jpg）
 * - 开屏视频：`res/raw/splash_video.mp4`
 * - 启动图标：`res/drawable-nodpi/splash_icon.png`（或 .jpg），没有就用应用名文字
 */
class SplashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as YETVApp

        // 24 小时内已完成过加载 → 不显示加载页，直接进主页
        if (app.prefs.splashValid) {
            goMain()
            return
        }

        setContent {
            // 注意：YETVTheme 的参数名是 `dark`，写成 darkMode 会报
            // "Cannot find a parameter with this name"
            YETVTheme(dark = com.yetvplus.nativeapp.ui.darkModeState.value) {
                SplashScreen(onDone = { goMain() })
            }
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

/**
 * 把异常翻译成人能看懂的原因。
 * 之前连不上服务器时静默跳过了，用户完全不知道发生了什么。
 */
private fun describeError(e: Throwable?): String {
    if (e == null) return "连接失败（未知原因）"
    val m = (e.message ?: "").lowercase()
    return when {
        m.contains("connection reset") || m.contains("connectionreset") ->
            "连接被重置（Connection Reset）：服务器主动断开了连接"
        m.contains("timeout") || m.contains("timed out") ->
            "连接超时：服务器在规定时间内没有响应"
        m.contains("failed to connect") || m.contains("unable to resolve") ||
            m.contains("unknownhost") || m.contains("enameNotFound") ->
            "无法连接：域名解析失败或服务器不可达"
        m.contains("403") -> "访问被拒绝（403）：可能被网关策略拦截"
        m.contains("401") -> "未授权（401）：登录态可能已失效"
        m.contains("404") -> "接口不存在（404）：站点地址可能填错了"
        m.contains("ssl") || m.contains("certificate") ->
            "证书错误：HTTPS 校验未通过"
        else -> "连接失败：${e.javaClass.simpleName} - ${e.message ?: "未知原因"}"
    }
}

/** 启动加载的两个步骤 */
private val SPLASH_STEPS = listOf("正在连接服务器", "加载注册配置")

@Composable
private fun SplashScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as YETVApp

    // 开屏素材：视频优先，其次图片
    val videoId = remember {
        context.resources.getIdentifier("splash_video", "raw", context.packageName)
    }
    val imageId = remember {
        context.resources.getIdentifier("splash_image", "drawable", context.packageName)
    }
    val iconId = remember {
        context.resources.getIdentifier("splash_icon", "drawable", context.packageName)
    }

    var step by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = progress, tween(300), label = "splashProgress"
    )

    // 失败原因（连接被重置、超时等），非空时显示错误 + 重试按钮
    var failReason by remember { mutableStateOf("") }
    // 重试计数：点击「重试」+1，重新触发加载
    var retryTick by remember { mutableIntStateOf(0) }

    // 真实执行两步加载
    LaunchedEffect(retryTick) {
        failReason = ""
        done = false
        progress = 0.15f

        // 步骤 1：连接服务器（拿 server-config）
        step = 0
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { app.repo.serverConfig() }
        }
        val cfg = result.getOrNull()

        if (cfg == null) {
            // 关键：连不上时**不再静默跳过**，把真实原因显示出来让用户重试
            failReason = describeError(result.exceptionOrNull())
            progress = 0.15f
            return@LaunchedEffect
        }

        progress = 0.55f

        // 步骤 2：加载注册配置（复用上面那份 server-config，不重复请求）
        step = 1
        app.prefs.registerEnabled = cfg.enableRegister
        // 站点若要求邀请码，一并记下来
        app.prefs.registerNeedInvite = cfg.requireInvite
        progress = 1f

        // 记下完成时间，24 小时内不再显示
        app.prefs.splashLoadedAt = System.currentTimeMillis()

        delay(260)
        done = true
    }

    LaunchedEffect(done) {
        if (done) {
            delay(120)
            onDone()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景沿用自定义背景，视觉上和主界面一致
        if (app.prefs.bgPath.isNotEmpty()) {
            AppBackground(app.prefs.bgPath, app.prefs.bgDim)
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B12)))
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 中间上方：图标或视频
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    videoId != 0 -> {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setVideoPath(
                                        "android.resource://${ctx.packageName}/$videoId"
                                    )
                                    setOnPreparedListener { it.isLooping = true; start() }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    imageId != 0 -> {
                        Image(
                            painter = painterResource(id = imageId),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    iconId != 0 -> {
                        Image(
                            painter = painterResource(id = iconId),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }
                    else -> {
                        CircularProgressIndicator(
                            color = LocalBrand.current,
                            modifier = Modifier.size(52.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 中间：应用名
            Text(
                "YETV PLUS",
                color = LocalTextPrimary.current,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.weight(1f))

            // 较底下：进度条 + 当前加载项
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = animProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = LocalBrand.current,
                    trackColor = Color.White.copy(alpha = 0.18f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (failReason.isNotEmpty()) {
                    // 连接失败：显示真实原因 + 重试按钮，不再静默跳过
                    Text(
                        failReason,
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    OutlinedButton(
                        onClick = { retryTick++ },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("重试", fontSize = 13.sp) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "跳过",
                        color = LocalTextSecondary.current,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { onDone() }
                            .padding(6.dp)
                    )
                } else {
                    Text(
                        SPLASH_STEPS.getOrNull(step) ?: "",
                        color = LocalTextSecondary.current,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(56.dp))
        }
    }
}
