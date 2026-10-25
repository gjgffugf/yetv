package com.yetvplus.nativeapp.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.yetvplus.nativeapp.YETVApp
import com.yetvplus.nativeapp.data.DoubanItem
import com.yetvplus.nativeapp.data.VideoItem
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.Surface
import com.yetvplus.nativeapp.ui.theme.TextPrimary
import com.yetvplus.nativeapp.ui.theme.TextSecondary
import java.net.URLEncoder
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import com.yetvplus.nativeapp.data.Episode
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import com.yetvplus.nativeapp.ui.theme.SurfaceLight
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween



/** 自定义背景图（本地文件）+ 暗化遮罩，全 App 生效 */
@Composable
fun AppBackground(bgPath: String, dim: Float) {
    if (bgPath.isEmpty()) return
    val bmp = try {
        BitmapFactory.decodeFile(bgPath)
    } catch (e: Exception) {
        null
    } ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f)))
        )
    }
}

@Composable
fun LoadingBox(text: String = "加载中…") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = LocalBrand.current)
            Text(
                text,
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun VideoCard(item: VideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            PosterImage(url = item.poster, ratio = 0.72f)
            Text(
                item.title,
                color = TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp)
            )
            val sub = listOfNotNull(
                item.rate.takeIf { it.isNotEmpty() },
                item.year.takeIf { it.isNotEmpty() },
                item.sourceName.takeIf { it.isNotEmpty() }
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, color = TextSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun DoubanCard(item: DoubanItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column {
            PosterImage(url = item.poster, ratio = 0.72f, badge = item.rate)
            Text(
                item.title,
                color = TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp)
            )
            if (item.year.isNotEmpty()) {
                Text(item.year, color = TextSecondary, fontSize = 10.sp)
            }
        }
    }
}

/**
 * 海报加载。直连失败（多是豆瓣防盗链）时自动回退到站点的图片代理，
 * 两级都失败才显示占位底纹。
 */
@Composable
private fun PosterImage(url: String, ratio: Float, badge: String = "") {
    val context = LocalContext.current
    val prefs = (context.applicationContext as YETVApp).prefs
    var useProxy by remember(url) { mutableStateOf(false) }

    val finalUrl = if (useProxy) {
        prefs.baseUrl + "/api/image-proxy?url=" + URLEncoder.encode(url, "UTF-8")
    } else {
        url
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
    ) {
        if (url.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Surface, MaterialTheme.colorScheme.surfaceVariant))
                )
            )
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(finalUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(Surface, SurfaceLight))
                        )
                    )
                },
                error = {
                    // 还没试过代理就再试一次，否则画占位
                    LaunchedEffect(Unit) {
                        if (!useProxy && url.startsWith("http")) useProxy = true
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                listOf(Surface, MaterialTheme.colorScheme.surfaceVariant)
                            )
                        )
                    )
                }
            )
        }
        if (badge.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
                    .align(Alignment.TopEnd)
            ) {
                Text(badge, color = Color(0xFFFFC46B), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun VideoGrid(
    items: List<VideoItem>,
    modifier: Modifier = Modifier,
    onClick: (VideoItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.key + it.title }) { item ->
            VideoCard(item) { onClick(item) }
        }
    }
}

@Composable
fun DoubanGrid(
    items: List<DoubanItem>,
    modifier: Modifier = Modifier,
    onClick: (DoubanItem) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.id + it.title }) { item ->
            DoubanCard(item) { onClick(item) }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = TextPrimary,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
fun ChipTabs(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items.size) { i ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (on) LocalBrand.current else Surface)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    items[i],
                    color = if (on) Color.White else TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ---------------- 选集：分段 + 正序/倒序 ----------------

private const val EP_PAGE_SIZE = 100

/** 每 100 集一段，返回每段的起止下标 */
private fun episodePages(total: Int): List<IntRange> {
    if (total <= 0) return emptyList()
    val out = ArrayList<IntRange>()
    var s = 0
    while (s < total) {
        val e = (s + EP_PAGE_SIZE).coerceAtMost(total)
        out.add(s until e)
        s = e
    }
    return out
}

/**
 * 选集区块：
 * - 右上角一行放「正序 / 倒序」切换，以及每 100 集一段的分段标签
 * - 集数按钮：看完=实色，看过没看完=从左到右按进度填充，未看=默认灰
 */
@Composable
fun EpisodeSection(
    episodes: List<Episode>,
    finished: (Int) -> Boolean,
    progress: (Int) -> Float,
    onPick: (Int) -> Unit
) {
    val total = episodes.size
    if (total == 0) return

    val pages = remember(total) { episodePages(total) }
    var page by remember(total) { mutableIntStateOf(0) }
    var asc by remember { mutableStateOf(true) }
    if (page >= pages.size) page = 0

    val range = pages.getOrNull(page) ?: return
    val order = if (asc) range.toList() else range.toList().reversed()

    Column(modifier = Modifier.fillMaxWidth()) {
        // 标题 + 分段 + 排序切换（同一行）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "选集（$total）",
                color = TextPrimary,
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            if (pages.size > 1) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(pages) { i, r ->
                        val label = "${r.first + 1}-${r.last + 1}集"
                        val on = i == page
                        val bg by animateColorAsState(
                            targetValue = if (on) LocalBrand.current else SurfaceLight,
                            animationSpec = tween(220), label = "segBg"
                        )
                        val fg by animateColorAsState(
                            targetValue = if (on) Color.White else TextSecondary,
                            animationSpec = tween(220), label = "segFg"
                        )
                        Box(
                            modifier = Modifier
                                .background(bg, RoundedCornerShape(12.dp))
                                .clickable { page = i }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(label, color = fg, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // 正序 / 倒序：切换时文字淡入淡出
            val orderBg by animateColorAsState(
                targetValue = SurfaceLight, animationSpec = tween(220), label = "orderBg"
            )
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .background(orderBg, RoundedCornerShape(12.dp))
                    .clickable { asc = !asc }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Crossfade(
                    targetState = asc,
                    animationSpec = tween(200),
                    label = "orderText"
                ) { isAsc ->
                    Text(
                        if (isAsc) "正序" else "倒序",
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 当前段的集数网格：换段/换序时整体淡入
        Crossfade(
            targetState = page to asc,
            animationSpec = tween(220),
            label = "epGrid",
            modifier = Modifier.fillMaxWidth()
        ) { _ ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(((order.size / 4 + 1) * 46).coerceAtMost(400).dp),
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(order) { i ->
                val done = finished(i)
                val p = if (done) 1f else progress(i).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (done) LocalBrand.current else SurfaceLight)
                        .clickable { onPick(i) }
                ) {
                    if (!done && p > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(p)
                                .fillMaxSize()
                                .background(LocalBrand.current.copy(alpha = 0.45f))
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            episodes.getOrNull(i)?.name ?: "第${i + 1}集",
                            color = if (done || p > 0f) Color.White else TextPrimary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        }
    }
}
