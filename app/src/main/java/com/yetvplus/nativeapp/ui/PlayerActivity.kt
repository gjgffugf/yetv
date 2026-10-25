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
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.foundation.layout.Spacer
import com.yetvplus.nativeapp.ui.screens.EpisodeSection
import com.yetvplus.nativeapp.ui.themeArgbState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.yetvplus.nativeapp.ui.screens.SourceSwitchBar
import com.yetvplus.nativeapp.ui.PlayerArgs
import com.yetvplus.nativeapp.data.VideoItem
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay

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
                    variants = PlayerArgs.variants,
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
    variants: List<VideoItem> = emptyList(),
    onClose: () -> Unit,
    onExternal: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = app.prefs

    var variantIndex by remember { mutableIntStateOf(0) }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) }
    var error by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    // 播放器控件是否可见；全屏按钮跟随它显隐，不再常驻
    var controlsVisible by remember { mutableStateOf(true) }
    // 长按快进中
    var speeding by remember { mutableStateOf(false) }
    var savedSpeed by remember { mutableFloatStateOf(1f) }

    // 切源后的分集（源自带时直接用，否则回退到打开时传入的）
    val activeUrls = remember(variantIndex, variants) {
        val v = variants.getOrNull(variantIndex)
        if (v != null && v.episodes.isNotEmpty()) v.episodes.map { it.url } else urls
    }
    val activeNames = remember(variantIndex, variants) {
        val v = variants.getOrNull(variantIndex)
        if (v != null && v.episodes.isNotEmpty()) v.episodes.map { it.name } else names
    }

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

    // 用 UpdatedState 包一层，保证原生按钮点击时拿到最新的目标状态
    val toggleFullscreen = rememberUpdatedState { fullscreen = !fullscreen }
    LaunchedEffect(fullscreen) {
        fullscreenButtonRef?.text = if (fullscreen) "退出" else "全屏"
    }

    // 长按快进：记录原速，松手还原
    fun setSpeed(v: Float) {
        if (v != 1f) {
            val cur = player.playbackParameters.speed
            if (cur == 1f) savedSpeed = cur
        }
        player.playbackParameters = androidx.media3.common.PlaybackParameters(v)
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
        val url = activeUrls.getOrNull(index) ?: ""
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
            VideoArea(
                player = player,
                onToggleFullscreen = { toggleFullscreen.value() },
                modifier = Modifier
                    .fillMaxSize()
                    .longPressFastForward(
                        onStart = { setSpeed(3f); speeding = true },
                        onEnd = { setSpeed(savedSpeed); speeding = false }
                    ),
                rounded = false,
                onControllerVisibility = { controlsVisible = it }
            )

            // 顶部返回 / 外部播放器：跟随播放控件显隐，不再常驻
            AnimatedVisibility(
                visible = controlsVisible,
                modifier = Modifier.align(Alignment.TopStart),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(180))
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                    Text(
                        activeNames.getOrNull(index) ?: "",
                        color = Color(0xCCFFFFFF), fontSize = 12.sp
                    )
                }
                Text(
                    "外部播放器", color = Color.White, fontSize = 12.sp,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .clickable { onExternal(activeUrls.getOrNull(index) ?: "") }
                )
            }
            }

            if (speeding) FastForwardHint()

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
                        onToggleFullscreen = { toggleFullscreen.value() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .longPressFastForward(
                                onStart = { setSpeed(3f); speeding = true },
                                onEnd = { setSpeed(savedSpeed); speeding = false }
                            ),
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
                                activeNames.getOrNull(index) ?: "",
                                color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1
                            )
                        }
                        Text(
                            "外部播放器", color = Color.White, fontSize = 12.sp,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clickable { onExternal(activeUrls.getOrNull(index) ?: "") }
                        )
                    }

                    if (speeding) FastForwardHint()

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
                            "共 ${activeUrls.size} 集 · 正在播放第 ${index + 1} 集",
                            color = TextSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // 上一集 / 下一集
                if (activeUrls.size > 1) {
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
                            enabled = index < activeUrls.size - 1,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (index < activeUrls.size - 1) index++
                        }
                    }
                }

                // 多源时：集数上方横向切源
                if (variants.size > 1) {
                    SourceSwitchBar(
                        variants = variants,
                        current = variantIndex,
                        onPick = { variantIndex = it }
                    )
                }
                // 集数列表（分段 + 正序/倒序 + 观看状态着色）
                if (urls.size > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    EpisodeSection(
                        episodes = activeUrls.mapIndexed { i, _ ->
                            com.yetvplus.nativeapp.data.Episode(
                                activeNames.getOrNull(i) ?: "第${i + 1}集",
                                activeUrls[i]
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

/**
 * 长按视频 3 倍速快进。
 * 按下超过 [LONG_PRESS_MS] 毫秒开始加速，松手恢复原速。
 */
private fun Modifier.longPressFastForward(
    onStart: () -> Unit,
    onEnd: () -> Unit
): Modifier = this.pointerInput(Unit) {
    // 用 awaitPointerEventScope 自己判定长按。
    // 不能用 detectTapGestures + launch：onPress 的接收者是 PressGestureScope，
    // 它不是 CoroutineScope，调 launch 会报 receiver type mismatch。
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
            // 等够时间仍未抬起、且手指没移出容差，才算长按
            val held = withTimeoutOrNull(FAST_FORWARD_HOLD_MS) {
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) break
                    if ((c.position - down.position).getDistance() > slop) break
                }
            } == null
            if (!held) continue

            onStart()
            // 等松手，然后恢复原速
            while (true) {
                val e = awaitPointerEvent()
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
            }
            onEnd()
        }
    }
}

/** 长按多久开始快进 */
private const val FAST_FORWARD_HOLD_MS = 350L

@Composable
private fun VideoArea(
    player: ExoPlayer,
    modifier: Modifier,
    rounded: Boolean,
    onControllerVisibility: ((Boolean) -> Unit)? = null,
    onToggleFullscreen: (() -> Unit)? = null
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                keepScreenOn = true
                controllerShowTimeoutMs = 2500
                // 全屏按钮并入 ExoPlayer 自带的控制条（和进度条/暂停同一层）
                if (onToggleFullscreen != null) attachControlButton(this, onToggleFullscreen)
                onControllerVisibility?.let { cb ->
                    // PlayerView 有两个同名重载，必须显式指定类型，否则重载解析歧义
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { v: Int ->
                            val visible = v == android.view.View.VISIBLE
                            // 兜底：若按钮挂在 PlayerView 上则手动同步显隐
                            findViewWithTag<android.view.View>(FS_BUTTON_TAG)?.visibility =
                                if (visible) android.view.View.VISIBLE else android.view.View.GONE
                            cb(visible)
                        }
                    )
                }
            }
        },
        modifier = modifier
    )
}

/** 控制条里全屏按钮的标记 */
private const val FS_BUTTON_TAG = "yetv_fs_button"

/** 当前全屏按钮实例（用于切换"全屏 / 退出全屏"文案） */
private var fullscreenButtonRef: android.widget.TextView? = null

/**
 * 把全屏按钮塞进播放器控制条里，和进度条、暂停、设置按钮同一层，
 * 随控制条一起显示/隐藏，不再是悬浮在上层的独立按钮。
 * 位置：底部控制行右侧，往左让开一格，避免压住设置按钮。
 */
private fun attachControlButton(playerView: PlayerView, onToggle: () -> Unit) {
    try {
        val ctx = playerView.context
        val density = ctx.resources.displayMetrics.density
        val btn = android.widget.TextView(ctx).apply {
            tag = FS_BUTTON_TAG
            text = "全屏"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x73000000.toInt())
                cornerRadius = 20f * density
            }
            setOnClickListener { onToggle() }
        }
        val size = (40 * density).toInt()
        val lp = android.widget.FrameLayout.LayoutParams(
            size, size,
            android.view.Gravity.BOTTOM or android.view.Gravity.END
        )
        lp.rightMargin = (58 * density).toInt()
        lp.bottomMargin = (42 * density).toInt()

        val cid = ctx.resources.getIdentifier("exo_controller", "id", ctx.packageName)
        val controller =
            if (cid != 0) playerView.findViewById<android.view.ViewGroup>(cid) else null
        if (controller != null) controller.addView(btn, lp) else playerView.addView(btn, lp)
        fullscreenButtonRef = btn
    } catch (ignored: Exception) {
    }
}

/** 长按快进中的提示 */
@Composable
private fun FastForwardHint() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .padding(top = 48.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text("3× 快进中…", color = Color.White, fontSize = 13.sp)
        }
    }
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
