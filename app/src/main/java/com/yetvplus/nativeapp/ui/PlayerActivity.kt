package com.yetvplus.nativeapp.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.Surface
import com.yetvplus.nativeapp.ui.theme.SurfaceLight
import com.yetvplus.nativeapp.ui.theme.TextPrimary
import com.yetvplus.nativeapp.ui.theme.TextSecondary
import com.yetvplus.nativeapp.ui.theme.YETVTheme
import kotlinx.coroutines.delay
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.foundation.layout.Spacer
import com.yetvplus.nativeapp.ui.screens.EpisodeSection
import com.yetvplus.nativeapp.ui.themeArgbState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween

private const val PLAYER_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 播放页按设置切刷新率，高刷屏上画面更顺
        RefreshRate.apply(this, (application as YETVApp).prefs.refreshRate)

        val urls = intent.getStringArrayListExtra("urls") ?: arrayListOf()
        val names = intent.getStringArrayListExtra("names") ?: arrayListOf()
        val index = intent.getIntExtra("index", 0)
        val title = intent.getStringExtra("title") ?: ""
        val videoKey = intent.getStringExtra("videoKey") ?: ""
        val poster = intent.getStringExtra("poster") ?: ""

        val app = application as YETVApp
        if (app.prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // 正常情况下一律竖屏；横屏只在用户点右下角全屏按钮时才切
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

        setContent {
            val saved = themeArgbState.intValue
            val brand = if (saved == 0) Brand else Color(saved)
            YETVTheme(brandColor = brand) {
                PlayerScreen(
                    app = app,
                    urls = urls,
                    names = names,
                    startIndex = index,
                    title = title,
                    poster = poster,
                    videoKey = videoKey,
                    onClose = { finish() },
                    onExternal = { url -> openExternal(url) }
                )
            }
        }
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setDataAndType(Uri.parse(url), "video/*")
            })
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (ignored: Exception) {
            }
        }
    }
}

@Composable
private fun PlayerScreen(
    app: YETVApp,
    urls: List<String>,
    names: List<String>,
    startIndex: Int,
    title: String,
    poster: String,
    videoKey: String,
    onClose: () -> Unit,
    onExternal: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = app.prefs

    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) }
    var error by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    // 播放器控件是否可见；全屏按钮跟随它显隐，不再常驻
    var controlsVisible by remember { mutableStateOf(true) }

    val player = remember {
        val referer = prefs.baseUrl + "/"
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_UA)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to referer,
                    "Accept" to "*/*",
                    "Accept-Language" to "zh-CN,zh;q=0.9"
                )
            )
            .setAllowCrossProtocolRedirects(true)
        val dataSource = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSource)
            )
            .build()
            .apply { playWhenReady = true }
    }

    // 切集 / 退出前落盘进度
    fun saveProgress() {
        if (videoKey.isEmpty()) return
        val dur = player.duration
        if (dur > 0) {
            val pos = player.currentPosition.coerceIn(0, dur)
            prefs.setProgress(videoKey, index, pos.toFloat() / dur.toFloat())
            prefs.lastEpisode = index
            prefs.putWatchRecord(videoKey, title, poster, index)
        }
    }

    // 切集：先存上一集，再加载新一集（有记录就续播）
    DisposableEffect(index) {
        val url = urls.getOrNull(index) ?: ""
        if (url.isNotEmpty()) {
            error = ""
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()

            val saved = if (videoKey.isNotEmpty()) prefs.getProgress(videoKey, index) else 0f
            val done = if (videoKey.isNotEmpty()) prefs.isFinished(videoKey, index) else false
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY && !done && saved > 0.02f && saved < 0.95f) {
                        val d = player.duration
                        if (d > 0) player.seekTo((d * saved).toLong())
                        player.removeListener(this)
                    }
                }
            })
            player.play()
        }
        onDispose { saveProgress() }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = e.message ?: "播放失败"
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress()
            player.removeListener(listener)
            player.release()
        }
    }

    // 播放中每 5 秒记一次进度
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            saveProgress()
        }
    }

    // 全屏切换：横屏 + 隐藏状态栏
    DisposableEffect(fullscreen) {
        activity?.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        activity?.window?.apply {
            if (fullscreen) addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            else clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (fullscreen) {
            // ---- 全屏模式 ----
            VideoArea(player = player, modifier = Modifier.fillMaxSize(), rounded = false)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                    Text(
                        names.getOrNull(index) ?: "",
                        color = Color(0xCCFFFFFF), fontSize = 12.sp
                    )
                }
                Text(
                    "外部播放器", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clickable { onExternal(urls.getOrNull(index) ?: "") }
                )
            }

            // 退出全屏按钮：同样放右下角
            IconButton(
                onClick = { fullscreen = false },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "退出全屏", tint = Color.White)
            }

            if (error.isNotEmpty()) {
                ErrorOverlay(error)
            }
        } else {
            // ---- 小窗模式（默认）：顶部 16:9，下方是集数 ----
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    VideoArea(
                        player = player,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        rounded = false,
                        onControllerVisibility = { controlsVisible = it }
                    )

                    // 返回 + 标题
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .align(Alignment.TopStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                            Text(
                                names.getOrNull(index) ?: "",
                                color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1
                            )
                        }
                        Text(
                            "外部播放器", color = Color.White, fontSize = 12.sp,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable { onExternal(urls.getOrNull(index) ?: "") }
                        )
                    }

                    // 全屏按钮：视频窗口右下角，跟随播放控件一起显隐
                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(180))
                    ) {
                        IconButton(
                            onClick = { fullscreen = true },
                            modifier = Modifier
                                .padding(10.dp)
                                .size(38.dp)
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(19.dp))
                        ) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "全屏",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (error.isNotEmpty()) {
                        ErrorOverlay(error)
                    }
                }

                // 标题行
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        title.ifEmpty { "播放" },
                        color = TextPrimary, fontSize = 17.sp, maxLines = 2
                    )
                    if (urls.size > 1) {
                        Text(
                            "共 ${urls.size} 集 · 正在播放第 ${index + 1} 集",
                            color = TextSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // 上一集 / 下一集
                if (urls.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PlayerButton("上一集", enabled = index > 0, modifier = Modifier.weight(1f)) {
                            if (index > 0) index--
                        }
                        PlayerButton(
                            "下一集",
                            enabled = index < urls.size - 1,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (index < urls.size - 1) index++
                        }
                    }
                }

                // 集数列表（分段 + 正序/倒序 + 观看状态着色）
                if (urls.size > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    EpisodeSection(
                        episodes = urls.mapIndexed { i, _ ->
                            com.yetvplus.nativeapp.data.Episode(
                                names.getOrNull(i) ?: "第${i + 1}集",
                                urls[i]
                            )
                        },
                        finished = { i -> prefs.isFinished(videoKey, i) },
                        progress = { i -> prefs.getProgress(videoKey, i) },
                        onPick = { index = it }
                    )
                }

                Box(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VideoArea(
    player: ExoPlayer,
    modifier: Modifier,
    rounded: Boolean,
    onControllerVisibility: ((Boolean) -> Unit)? = null
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                keepScreenOn = true
                controllerShowTimeoutMs = 2500
                onControllerVisibility?.let { cb ->
                    // PlayerView 有两个同名重载，必须显式指定类型，否则重载解析歧义
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v: Int ->
                            cb(v == android.view.View.VISIBLE)
                        }
                    )
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun ErrorOverlay(error: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(error, color = Color(0xFFFFC46B), fontSize = 13.sp)
            Text(
                "可尝试「外部播放器」",
                color = Color.White, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PlayerButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(
                if (enabled) SurfaceLight else Surface,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) TextPrimary else TextSecondary,
            fontSize = 13.sp
        )
    }
}
