package com.yetvplus.nativeapp.ui

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.yetvplus.nativeapp.ui.theme.YETVTheme

private const val PLAYER_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
private const val PLAYER_REFERER = "https://mtv.ffurry.cc.cd/"

class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val urls = intent.getStringArrayListExtra("urls") ?: arrayListOf()
        val names = intent.getStringArrayListExtra("names") ?: arrayListOf()
        val index = intent.getIntExtra("index", 0)
        val title = intent.getStringExtra("title") ?: ""

        val app = application as YETVApp
        if (app.prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        requestedOrientation = if (app.prefs.autoLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }

        setContent {
            YETVTheme {
                PlayerScreen(
                    urls = urls,
                    names = names,
                    startIndex = index,
                    title = title,
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
    urls: List<String>,
    names: List<String>,
    startIndex: Int,
    title: String,
    onClose: () -> Unit,
    onExternal: (String) -> Unit
) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) }
    var error by remember { mutableStateOf("") }

    val player = remember {
        // m3u8 直链常校验 Referer / UA，这里统一带上，否则容易 403
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_UA)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to PLAYER_REFERER,
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

    DisposableEffect(index) {
        val url = urls.getOrNull(index) ?: ""
        if (url.isNotEmpty()) {
            error = ""
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.play()
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = e.message ?: "播放失败"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 标题栏
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
                if (names.size > index) {
                    Text(names.getOrNull(index) ?: "", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                }
            }
            Text(
                "外部播放器",
                color = Color.White, fontSize = 12.sp,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .clickable { onExternal(urls.getOrNull(index) ?: "") }
            )
        }

        if (error.isNotEmpty()) {
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

        // 上一集 / 下一集
        if (urls.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PlayerButton("上一集", enabled = index > 0) {
                    if (index > 0) index--
                }
                PlayerButton("下一集", enabled = index < urls.size - 1) {
                    if (index < urls.size - 1) index++
                }
            }
        }
    }
}

@Composable
private fun PlayerButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (enabled) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.07f),
                androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (enabled) Color.White else Color(0x66FFFFFF), fontSize = 13.sp)
    }
}
