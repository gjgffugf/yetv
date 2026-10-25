package com.yetvplus.nativeapp.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.data.DetailInfo
import com.yetvplus.nativeapp.data.VideoItem
import com.yetvplus.nativeapp.ui.NavArgs
import com.yetvplus.nativeapp.ui.PlayerActivity
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.Surface
import com.yetvplus.nativeapp.ui.theme.SurfaceLight
import com.yetvplus.nativeapp.ui.theme.TextPrimary
import com.yetvplus.nativeapp.ui.theme.TextSecondary
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import com.yetvplus.nativeapp.ui.PlayerArgs
import com.yetvplus.nativeapp.ui.screens.SourceSwitchBar
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(app: YETVApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val group = NavArgs.group
    // 当前选中的源；有多个源时可切换
    var variantIndex by remember { mutableIntStateOf(0) }
    val variants = group?.variants ?: listOf(NavArgs.video ?: VideoItem())
    val base = variants.getOrNull(variantIndex) ?: NavArgs.video ?: VideoItem()
    var info by remember { mutableStateOf(DetailInfo(base)) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(base.key) {
        // 切源时也要有明确反馈：先显示加载中，再出结果
        loading = true
        // 抓包确认：搜索结果已带完整 m3u8 分集，直接用，省掉一次详情请求
        if (base.episodes.isNotEmpty()) {
            // 极短的展示时间，避免一闪而过看不出在加载
            delay(180)
            info = DetailInfo(base, base.episodes)
            loading = false
            return@LaunchedEffect
        }
        if (base.source.isEmpty() || base.id.isEmpty()) {
            loading = false
            error = "该条目缺少 source / id，且未自带播放地址"
            return@LaunchedEffect
        }
        loading = true
        error = ""
        val res = runCatching { app.repo.detail(base.source, base.id) }
        loading = false
        if (res.isSuccess) {
            val d = res.getOrThrow()
            info = if (d.episodes.isEmpty()) d.copy(item = base) else d
            if (d.episodes.isEmpty()) error = "暂未获取到播放地址"
        } else {
            error = "详情加载失败：${res.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }

    val item = info.item
    val poster = item.poster.ifEmpty { base.poster }
    // 分集可能挂在 base 上（搜索结果自带），也可能来自详情
    val episodes = info.episodes.ifEmpty { base.episodes }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Text(
                item.title.ifEmpty { base.title },
                color = TextPrimary,
                fontSize = 17.sp,
                maxLines = 1
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            Row(modifier = Modifier.padding(bottom = 12.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(poster).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(108.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        item.title.ifEmpty { base.title },
                        color = TextPrimary, fontSize = 16.sp
                    )
                    val meta = listOfNotNull(
                        item.rate.takeIf { it.isNotEmpty() },
                        item.year.takeIf { it.isNotEmpty() },
                        item.typeName.takeIf { it.isNotEmpty() },
                        item.sourceName.takeIf { it.isNotEmpty() }
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, color = TextSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp))
                    }
                    if (item.note.isNotEmpty()) {
                        Text(item.note, color = TextSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            if (item.desc.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        item.desc, color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center) {
                        Text("加载中…", color = TextSecondary, fontSize = 13.sp)
                    }
                }
                error.isNotEmpty() -> {
                    Text(error, color = Color(0xFFFFC46B), fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 14.dp))
                }
                episodes.isNotEmpty() -> {
                    if (variants.size > 1) {
                        SourceSwitchBar(
                            variants = variants,
                            current = variantIndex,
                            onPick = { variantIndex = it }
                        )
                    }
                    EpisodeSection(
                        episodes = episodes,
                        finished = { i -> app.prefs.isFinished(base.key, i) },
                        progress = { i -> app.prefs.getProgress(base.key, i) }
                    ) { index ->
                        val urls = ArrayList(episodes.map { it.url })
                        val names = ArrayList(episodes.map { it.name })
                        val i = Intent(context, PlayerActivity::class.java).apply {
                            putStringArrayListExtra("urls", urls)
                            putStringArrayListExtra("names", names)
                            putExtra("index", index)
                            putExtra("title", item.title.ifEmpty { base.title })
                            putExtra("videoKey", base.key)
                            putExtra("total", episodes.size)
                            putExtra("poster", item.poster.ifEmpty { base.poster })
                            PlayerArgs.variants = variants
                        }
                        context.startActivity(i)
                    }
                }
                else -> {
                    Text("暂未获取到播放地址", color = TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 14.dp))
                }
            }

            Box(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 选集网格。
 * - 没看过：默认灰底
 * - 看过没看完：底色 + 从左到右按比例填充的进度条
 * - 看完：整块换色（LocalBrand.current 实色）
 */

