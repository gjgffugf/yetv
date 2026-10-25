package com.yetvplus.nativeapp.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.yetvplus.nativeapp.data.DoubanItem
import com.yetvplus.nativeapp.data.VideoItem
import com.yetvplus.nativeapp.ui.theme.Brand
import com.yetvplus.nativeapp.ui.theme.Surface
import com.yetvplus.nativeapp.ui.theme.TextPrimary
import com.yetvplus.nativeapp.ui.theme.TextSecondary
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.alpha

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
            CircularProgressIndicator(color = Brand)
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

@Composable
private fun PosterImage(url: String, ratio: Float, badge: String = "") {
    val context = LocalContext.current
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
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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
                    .background(if (on) Brand else Surface)
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
