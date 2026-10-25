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
import com.yetvplus.nativeapp.ui.theme.LocalSurface
import com.yetvplus.nativeapp.ui.theme.LocalTextPrimary
import com.yetvplus.nativeapp.ui.theme.LocalTextSecondary
import java.net.URLEncoder
import com.yetvplus.nativeapp.ui.theme.LocalBrand
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import com.yetvplus.nativeapp.data.Episode
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import com.yetvplus.nativeapp.ui.theme.LocalSurfaceLight
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import com.yetvplus.nativeapp.data.SearchGroup
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.border



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
                color = LocalTextSecondary.current,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = LocalTextSecondary.current, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

/** 海报 + 标题的可复用卡片；badge 显示在右上角 */
@Composable
fun PosterCard(item: VideoItem, badge: String = "") {
    Column {
        PosterImage(url = item.poster, ratio = 0.72f, badge = badge)
        Text(
            item.title,
            color = LocalTextPrimary.current,
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
            Text(sub, color = LocalTextSecondary.current, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
fun VideoCard(item: VideoItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        PosterCard(item)
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
                color = LocalTextPrimary.current,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp)
            )
            if (item.year.isNotEmpty()) {
                Text(item.year, color = LocalTextSecondary.current, fontSize = 10.sp)
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
            .background(LocalSurface.current)
    ) {
        if (url.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(LocalSurface.current, MaterialTheme.colorScheme.surfaceVariant))
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
                            Brush.linearGradient(listOf(LocalSurface.current, LocalSurfaceLight.current))
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
                                listOf(LocalSurface.current, MaterialTheme.colorScheme.surfaceVariant)
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
    loadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onClick: (DoubanItem) -> Unit
) {
    val state = rememberLazyGridState()

    // 滑到倒数第二行就提前请求下一页，避免到底才加载导致卡顿
    LaunchedEffect(state, items.size) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= items.size - 4 && items.isNotEmpty()) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.id + it.title }) { item ->
            DoubanCard(item) { onClick(item) }
        }
        if (loadingMore) {
            // 整行居中：不占一个网格格子，避免只出现在最左列
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = LocalBrand.current,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
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
                    .background(if (on) LocalBrand.current else LocalSurface.current)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    items[i],
                    color = if (on) Color.White else LocalTextSecondary.current,
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
/** 正在播放的那一集用金色边框标记 */
private val EpisodeBorderGold = Color(0xFFD9A441)

@Composable
fun EpisodeSection(
    episodes: List<Episode>,
    finished: (Int) -> Boolean,
    progress: (Int) -> Float,
    /** 正在播放的集数下标；-1 表示没有（例如刚切换了源、还没选集）。
     *  必须放在 onPick 之前：Kotlin 的尾随 lambda 只会绑定到最后一个参数，
     *  若它排在 onPick 后面，调用处的尾随 lambda 就会被误当成 playingIndex。 */
    playingIndex: Int = -1,
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
                color = LocalTextPrimary.current,
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
                            targetValue = if (on) LocalBrand.current else LocalSurfaceLight.current,
                            animationSpec = tween(220), label = "segBg"
                        )
                        val fg by animateColorAsState(
                            targetValue = if (on) Color.White else LocalTextSecondary.current,
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
                targetValue = LocalSurfaceLight.current, animationSpec = tween(220), label = "orderBg"
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
                        color = LocalTextPrimary.current,
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
                val isPlaying = i == playingIndex
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (done) LocalBrand.current else LocalSurfaceLight.current)
                        // 金色边框画在内容之后、外框之内，
                        // 用的是 border() 而不是 padding，不会压缩内部的进度填充
                        .then(
                            if (isPlaying) {
                                Modifier.border(2.dp, EpisodeBorderGold, RoundedCornerShape(8.dp))
                            } else Modifier
                        )
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
                            color = if (done || p > 0f) Color.White else LocalTextPrimary.current,
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

// ---------------- 源切换横条 ----------------

/**
 * 集数下方横向可滑动的源切换条。
 * 每个按钮右上角角标显示该源拥有的集数。
 */
@Composable
fun SourceSwitchBar(
    variants: List<VideoItem>,
    current: Int,
    onPick: (Int) -> Unit
) {
    if (variants.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "播放源（${variants.size}）",
            color = LocalTextPrimary.current,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(variants) { i, v ->
                val on = i == current
                val bg by animateColorAsState(
                    targetValue = if (on) LocalBrand.current else LocalSurfaceLight.current,
                    animationSpec = tween(200),
                    label = "srcBg"
                )
                val fg by animateColorAsState(
                    targetValue = if (on) Color.White else LocalTextPrimary.current,
                    animationSpec = tween(200),
                    label = "srcFg"
                )
                Box(
                    modifier = Modifier
                        .background(bg, RoundedCornerShape(10.dp))
                        .clickable { if (!on) onPick(i) }
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            v.sourceName.ifEmpty { v.source.ifEmpty { "源${i + 1}" } },
                            color = fg,
                            fontSize = 12.sp
                        )
                        // 右上角角标：该源的集数
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .background(
                                    if (on) Color.White.copy(alpha = 0.22f) else LocalSurface.current,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                if (v.episodes.isNotEmpty()) "${v.episodes.size}集" else "?",
                                color = fg,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- 整合结果网格 ----------------

/** 搜索整合后的网格：一个卡片一部片，右上角标显示有几个源 */
@Composable
fun GroupGrid(
    items: List<SearchGroup>,
    modifier: Modifier = Modifier,
    onClick: (SearchGroup) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.key + it.title }) { g ->
            val badge = if (g.variants.size > 1) "${g.variants.size}源" else ""
            Box(modifier = Modifier.clickable { onClick(g) }) {
                PosterCard(g.best, badge)
            }
        }
    }
}
