package com.yetvplus.nativeapp.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.yetvplus.nativeapp.data.VideoItem
import com.yetvplus.nativeapp.ui.screens.EpisodeSection
import com.yetvplus.nativeapp.ui.screens.SourceSwitchBar
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import com.yetvplus.nativeapp.ui.theme.LocalSurfaceLight
import com.yetvplus.nativeapp.ui.theme.LocalTextPrimary
import com.yetvplus.nativeapp.ui.theme.LocalTextSecondary
import com.yetvplus.nativeapp.ui.theme.YETVTheme
import com.yetvplus.nativeapp.ui.darkModeState
import com.yetvplus.nativeapp.ui.themeArgbState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val PLAYER_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

/** 控件自动隐藏的等待时间 */
private const val CONTROLS_TIMEOUT_MS = 3500L

/** 长按多久开始 3× 快进 */
private const val FAST_FORWARD_HOLD_MS = 350L

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
        // 详情页选的是哪个源，播放器就从哪个源开始播。
        // 之前没传这个值，播放器一律从 0 号源起播，
        // 于是「详情页切了源、进播放器还是第一个源」。
        val variantIndex = intent.getIntExtra("variantIndex", 0)

        val app = application as YETVApp
        if (app.prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        // 正常情况下一律竖屏；横屏只在用户点全屏按钮时才切
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

        setContent {
            val saved = themeArgbState.intValue
            val brand = if (saved == 0) Brand else Color(saved)
            YETVTheme(brandColor = brand, dark = darkModeState.value) {
                PlayerScreen(
                    app = app,
                    urls = urls,
                    variants = PlayerArgs.variants,
                    names = names,
                    startIndex = index,
                    startVariant = variantIndex,
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
        if (url.isBlank()) return
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
    startVariant: Int,
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

    var variantIndex by remember { mutableIntStateOf(startVariant.coerceIn(0, (variants.size - 1).coerceAtLeast(0))) }
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) }

    // 当前源的分集
    val activeUrls = remember(variantIndex, variants) {
        val v = variants.getOrNull(variantIndex)
        if (v != null && v.episodes.isNotEmpty()) v.episodes.map { it.url } else urls
    }
    val activeNames = remember(variantIndex, variants) {
        val v = variants.getOrNull(variantIndex)
        if (v != null && v.episodes.isNotEmpty()) v.episodes.map { it.name } else names
    }

    /**
     * 真正在播的地址。
     * 只有「用户点了某一集」才会变 —— 单纯切换源不会改它，
     * 所以切源不会打断当前播放，金色边框也会消失（需要重新选集）。
     */
    var playUrl by remember { mutableStateOf(activeUrls.getOrNull(index) ?: urls.getOrNull(0) ?: "") }
    /** 递增计数：点同一集时也能强制重新加载 */
    var playToken by remember { mutableIntStateOf(0) }

    var error by remember { mutableStateOf("") }
    var fullscreen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableLongStateOf(0L) }
    var speeding by remember { mutableStateOf(false) }
    var savedSpeed by remember { mutableFloatStateOf(1f) }
    var loadStep by remember { mutableStateOf("准备中…") }
    var loading by remember { mutableStateOf(true) }

    // 正在播放的是第几集（在当前源的分集里找）
    val playingIndex = activeUrls.indexOf(playUrl)

    val player = remember {
        val referer = prefs.baseUrl + "/"
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_UA)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to referer,
                    "Accept" to "*/*",
                    "Accept-Language" to "zh-CN,zh;q=0.9",
                    "Connection" to "keep-alive"
                )
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val dataSource = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
        ExoPlayer.Builder(context)
            // 关键：用 DefaultMediaSourceFactory 而不是只给 HlsMediaSource。
            // 只挂 HLS 时，任何非 m3u8 的地址（mp4/mkv/无扩展名的直链）
            // 都会解析失败 —— 这就是「所有源都播放失败、网页端正常」的原因。
            .setMediaSourceFactory(
                androidx.media3.exoplayer.DefaultMediaSourceFactory(dataSource)
            )
            .build()
            .apply { playWhenReady = true }
    }

    val toggleFullscreen = rememberUpdatedState { fullscreen = !fullscreen }

    fun showControls() {
        controlsVisible = true
        interaction = System.currentTimeMillis()
    }

    // 一段时间没操作就自动收起控件
    LaunchedEffect(interaction) {
        if (interaction == 0L) return@LaunchedEffect
        delay(CONTROLS_TIMEOUT_MS)
        controlsVisible = false
    }

    fun setSpeed(v: Float) {
        if (v != 1f) {
            val cur = player.playbackParameters.speed
            if (cur == 1f) savedSpeed = cur
        }
        player.playbackParameters = androidx.media3.common.PlaybackParameters(v)
    }

    // 落盘进度
    fun saveProgress() {
        if (videoKey.isEmpty()) return
        val dur = player.duration
        if (dur > 0) {
            val pos = player.currentPosition.coerceIn(0, dur)
            val ep = if (playingIndex >= 0) playingIndex else index
            prefs.setProgress(videoKey, ep, pos.toFloat() / dur.toFloat())
            prefs.lastEpisode = ep
            prefs.putWatchRecord(videoKey, title, poster, ep)
        }
    }

    // 加载地址：只有 playUrl（或强制刷新计数）变化时才重新准备，
    // 所以切换源不会打断当前播放；而在新源里点某一集就会真正换源。
    DisposableEffect(playUrl, playToken) {
        val url = playUrl
        if (url.isNotEmpty()) {
            error = ""
            loadStep = "正在解析地址…"
            loading = true
            player.setMediaItem(MediaItem.fromUri(url))
            loadStep = "正在缓冲…"
            player.prepare()

            val ep = activeUrls.indexOf(url)
            val saved = if (videoKey.isNotEmpty() && ep >= 0) prefs.getProgress(videoKey, ep) else 0f
            val done = if (videoKey.isNotEmpty() && ep >= 0) prefs.isFinished(videoKey, ep) else false
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            loading = true
                            loadStep = "正在缓冲…"
                        }
                        Player.STATE_READY -> {
                            loading = false
                            loadStep = "即将播放"
                            if (!done && saved > 0.02f && saved < 0.95f) {
                                val d = player.duration
                                if (d > 0) player.seekTo((d * saved).toLong())
                            }
                            player.removeListener(this)
                        }
                        Player.STATE_ENDED -> {
                            loading = false
                            loadStep = "播放结束"
                        }
                        Player.STATE_IDLE -> {
                            loading = true
                            loadStep = "准备中…"
                        }
                    }
                }
            }
            player.addListener(listener)
            player.play()
        }
        onDispose { saveProgress() }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                loading = false
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
            // ---- 全屏 ----
            Box(modifier = Modifier.fillMaxSize()) {
                VideoArea(
                    player = player,
                    modifier = Modifier
                        .fillMaxSize()
                        .longPressFastForward(
                            onStart = { setSpeed(3f); speeding = true },
                            onEnd = { setSpeed(savedSpeed); speeding = false }
                        )
                        .tapToToggleControls { showControls(); controlsVisible = !controlsVisible }
                )
                PlayerOverlay(
                    visible = controlsVisible,
                    player = player,
                    fullscreen = fullscreen,
                    title = title,
                    episodeName = activeNames.getOrNull(if (playingIndex >= 0) playingIndex else index) ?: "",
                    showTopBar = true,
                    onClose = onClose,
                    onExternal = { onExternal(playUrl) },
                    onToggleFullscreen = { toggleFullscreen.value(); showControls() }
                )
                if (speeding) FastForwardHint()
                if (loading && error.isEmpty()) LoadingHint(loadStep)
                if (error.isNotEmpty()) ErrorOverlay(error)
            }
        } else {
            // ---- 小窗（默认）：顶部 16:9，下方是集数 ----
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
                            .aspectRatio(16f / 9f)
                            .longPressFastForward(
                                onStart = { setSpeed(3f); speeding = true },
                                onEnd = { setSpeed(savedSpeed); speeding = false }
                            )
                            .tapToToggleControls { showControls(); controlsVisible = !controlsVisible }
                    )
                    PlayerOverlay(
                        visible = controlsVisible,
                        player = player,
                        fullscreen = fullscreen,
                        title = title,
                        episodeName = activeNames.getOrNull(if (playingIndex >= 0) playingIndex else index) ?: "",
                        showTopBar = true,
                        onClose = onClose,
                        onExternal = { onExternal(playUrl) },
                        onToggleFullscreen = { toggleFullscreen.value(); showControls() }
                    )
                    if (speeding) FastForwardHint()
                    if (loading && error.isEmpty()) LoadingHint(loadStep)
                    if (error.isNotEmpty()) ErrorOverlay(error)
                }

                // 标题行
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        title.ifEmpty { "播放" },
                        color = LocalTextPrimary.current, fontSize = 17.sp, maxLines = 2
                    )
                    if (activeUrls.size > 1) {
                        Text(
                            if (playingIndex >= 0) "共 ${activeUrls.size} 集 · 正在播放第 ${playingIndex + 1} 集"
                            else "共 ${activeUrls.size} 集 · 切换源后请重新选择剧集",
                            color = LocalTextSecondary.current, fontSize = 12.sp,
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
                        PlayerButton("上一集", enabled = playingIndex > 0, modifier = Modifier.weight(1f)) {
                            val n = (if (playingIndex >= 0) playingIndex else index) - 1
                            if (n >= 0) { index = n; playUrl = activeUrls[n] }
                        }
                        PlayerButton(
                            "下一集",
                            enabled = (if (playingIndex >= 0) playingIndex else index) < activeUrls.size - 1,
                            modifier = Modifier.weight(1f)
                        ) {
                            val n = (if (playingIndex >= 0) playingIndex else index) + 1
                            if (n < activeUrls.size) { index = n; playUrl = activeUrls[n] }
                        }
                    }
                }

                // 多源切换
                if (variants.size > 1) {
                    SourceSwitchBar(
                        variants = variants,
                        current = variantIndex,
                        onPick = {
                            // 只换源，不动 playUrl —— 当前播放不中断，
                            // 金色边框会因为找不到地址而消失，等用户重新选集。
                            variantIndex = it
                        }
                    )
                }

                // 集数列表
                if (activeUrls.size > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    EpisodeSection(
                        episodes = activeUrls.mapIndexed { i, _ ->
                            com.yetvplus.nativeapp.data.Episode(
                                activeNames.getOrNull(i) ?: "第${i + 1}集",
                                activeUrls[i]
                            )
                        },
                        playingIndex = playingIndex,
                        finished = { i -> prefs.isFinished(videoKey, i) },
                        progress = { i -> prefs.getProgress(videoKey, i) },
                        onPick = { i ->
                            index = i
                            val u = activeUrls[i]
                            // 点的是同一集也要能重新加载
                            if (u == playUrl) playToken++ else playUrl = u
                        }
                    )
                }

                Box(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ---------------- 手势 ----------------

/** 长按 3× 快进 */
private fun Modifier.longPressFastForward(
    onStart: () -> Unit,
    onEnd: () -> Unit
): Modifier = this.pointerInput(Unit) {
    // 用 awaitPointerEventScope 自己判定长按。
    // detectTapGestures 的 onPress 接收者是 PressGestureScope，
    // 它不是 CoroutineScope，里面调 launch 会报 receiver type mismatch。
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop
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
            while (true) {
                val e = awaitPointerEvent()
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
            }
            onEnd()
        }
    }
}

/** 单击切换控件显隐 */
private fun Modifier.tapToToggleControls(onTap: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(onTap = { onTap() })
    }

// ---------------- 视频区与控制层 ----------------

/**
 * 视频画面。
 * useController = false：不用 ExoPlayer 自带控制条，
 * 控制层（进度条 + 全屏按钮）完全由 Compose 自己画，
 * 这样它们天生在同一层，显隐动画必然一致。
 */
@Composable
private fun VideoArea(
    player: ExoPlayer,
    modifier: Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                keepScreenOn = true
            }
        },
        modifier = modifier
    )
}

/**
 * 播放控制层：顶部返回 / 外部播放器，底部控制条。
 *
 * 底部控制条里依次是：播放暂停、**进度条**、时间、全屏按钮。
 * 它们同属一个 AnimatedVisibility，淡入淡出完全同步。
 * 全屏按钮是正方形的图标按钮（IconButton 本身就是 48dp 正方形）。
 */
@Composable
private fun PlayerOverlay(
    visible: Boolean,
    player: ExoPlayer,
    fullscreen: Boolean,
    title: String,
    episodeName: String,
    showTopBar: Boolean,
    onClose: () -> Unit,
    onExternal: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 顶部
            if (showTopBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, color = Color.White, fontSize = 14.sp, maxLines = 1)
                        if (episodeName.isNotEmpty()) {
                            Text(episodeName, color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1)
                        }
                    }
                    Text(
                        "外部播放器", color = Color.White, fontSize = 12.sp,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { onExternal() }
                    )
                }
            }

            // 底部控制条
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayPauseButton(player)

                var position by remember { mutableLongStateOf(0L) }
                var duration by remember { mutableLongStateOf(0L) }
                LaunchedEffect(Unit) {
                    while (true) {
                        position = player.currentPosition.coerceAtLeast(0L)
                        duration = player.duration.takeIf { it > 0 } ?: 0L
                        delay(300)
                    }
                }
                Text(
                    formatTime(position),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                PlayerProgressBar(
                    player = player,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    formatTime(duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )

                // 全屏按钮：正方形图标按钮，和进度条同一层、同一动画
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (fullscreen) "退出全屏" else "全屏",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(player: ExoPlayer) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    LaunchedEffect(Unit) {
        while (true) {
            playing = player.isPlaying
            delay(300)
        }
    }
    IconButton(onClick = {
        if (player.isPlaying) player.pause() else player.play()
    }) {
        Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "暂停" else "播放",
            tint = Color.White
        )
    }
}

/**
 * 自绘进度条：轨道 + 缓冲 + 已播 + 可拖动圆点。
 * 放在控制条里，与全屏按钮同一层。
 */
@Composable
private fun PlayerProgressBar(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val draggingNow by rememberUpdatedState(dragging)

    LaunchedEffect(Unit) {
        while (true) {
            if (!draggingNow) {
                position = player.currentPosition.coerceAtLeast(0L)
                buffered = player.bufferedPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it > 0 } ?: 0L
            }
            delay(200)
        }
    }

    BoxWithConstraints(
        modifier = modifier.height(32.dp),
        contentAlignment = Alignment.Center
    ) {
        val totalWidth = maxWidth
        val dur = duration
        val played = when {
            dragging -> dragFraction
            dur > 0 -> (position.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
        val bufferedFrac = if (dur > 0) (buffered.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f

        // 轨道
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            if (dur > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFrac)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.45f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(played)
                        .fillMaxHeight()
                        .background(LocalBrand.current)
                )
            }
        }

        // 拖动圆点
        if (totalWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .offset(x = (totalWidth * played) - 5.5.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }

        // 拖动 / 点击定位
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragging = false
                            val d = player.duration
                            if (d > 0) player.seekTo((dragFraction * d).toLong())
                        },
                        onDragCancel = { dragging = false },
                        onDrag = { change, _ ->
                            dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val d = player.duration
                        if (d > 0) {
                            val f = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            player.seekTo((f * d).toLong())
                        }
                    }
                }
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

// ---------------- 提示层 ----------------

/** 开始加载时的步骤提示 + 转圈 */
@Composable
private fun LoadingHint(step: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = LocalBrand.current,
                modifier = Modifier.size(34.dp),
                strokeWidth = 3.dp
            )
            Text(
                step,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
}

/** 长按快进中的提示 */
@Composable
private fun FastForwardHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) LocalSurfaceLight.current else LocalSurfaceLight.current.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) LocalTextPrimary.current else LocalTextSecondary.current,
            fontSize = 13.sp
        )
    }
}
