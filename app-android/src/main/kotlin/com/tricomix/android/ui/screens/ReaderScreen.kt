package com.tricomix.android.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tricomix.android.LiteFeatures
import com.tricomix.android.data.image.JmImage
import com.tricomix.android.ui.components.ErrorBox
import com.tricomix.android.ui.components.GlassSurface
import com.tricomix.android.ui.components.LoadingBox
import com.tricomix.core.model.Chapter
import com.tricomix.core.model.Comic
import com.tricomix.core.model.ImageQuality
import com.tricomix.core.model.PageRef
import com.tricomix.core.model.UnscrambleSpec
import com.tricomix.core.source.ComicSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs

/**
 * 阅读屏（搬迁自 JMNeXt 的 `ReaderScreen`）。
 *
 * 交互：**左右滑动翻页**（本轮补上，此前只能点按钮）；按钮保留，方便单手与误触恢复。
 * 与原项目一致的处理：反切片（[JmImage] 含 bands 预检）、按 [LiteFeatures] 窗口预取并节流、
 * 取图带浏览器 UA 与 Referer（EH 图片主机拒绝裸请求）。
 *
 * 尚未搬：双指缩放。只吃 `core` 与 [ComicSource]。
 */
@Composable
fun ReaderRoute(
    comic: Comic,
    chapter: Chapter,
    pages: List<PageRef>,
    source: ComicSource,
    onBack: () -> Unit,
    onStatus: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var index by remember(chapter.id) { mutableStateOf(0) }
    var bitmap by remember(chapter.id, index) { mutableStateOf<ImageBitmap?>(null) }
    var imageError by remember(chapter.id, index) { mutableStateOf<String?>(null) }

    fun go(delta: Int) {
        val next = (index + delta).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        if (next != index) index = next
    }

    // 控制条包进 JMNeXt 的玻璃容器（GlassSurface），与顶栏质感一致
    GlassSurface(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBack) { Text("返回") }
        Button(enabled = index > 0, onClick = { go(-1) }) { Text("上一页") }
        Button(enabled = index < pages.size - 1, onClick = { go(1) }) { Text("下一页") }
    }
    }
    Text(
        "${index + 1} / ${pages.size}　预取 ${LiteFeatures.prefetchBefore}/${LiteFeatures.prefetchAfter}　左右滑动可翻页",
        style = MaterialTheme.typography.labelSmall,
    )

    val page = pages.getOrNull(index)
    if (page != null) {
        LaunchedEffect(chapter.id, index) {
            bitmap = null
            imageError = null
            source.imageRequest(page, ImageQuality.HIGH).fold(
                onSuccess = { req ->
                    val bmp = fetchCached("${chapter.id}#${req.url}", req.url, req.unscramble, page.extra["aid"]?.toIntOrNull())
                    if (bmp == null) {
                        imageError = "图片下载或解码失败"
                    } else {
                        bitmap = bmp
                        onStatus("${index + 1}/${pages.size}")
                        prefetch(scope, source, pages, index, chapter.id)
                    }
                },
                onFailure = { imageError = "取图失败：${it.message}" },
            )
        }
    }

    when {
        imageError != null -> ErrorBox(message = imageError.orEmpty())
        bitmap == null -> LoadingBox(Modifier.padding(top = 12.dp))
        else -> Image(
            bitmap = bitmap!!,
            contentDescription = "第 ${index + 1} 页",
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                // 横向滑动翻页：一次拖动只翻一页（按位移方向判定，阈值避免误触）
                .pointerInput(chapter.id, index, pages.size) {
                    var consumed = false
                    detectHorizontalDragGestures(
                        onDragStart = { consumed = false },
                        onDragEnd = { consumed = false },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!consumed && abs(dragAmount) > 60f) {
                                consumed = true
                                go(if (dragAmount < 0) 1 else -1)
                            }
                        },
                    )
                },
            contentScale = ContentScale.Fit,
        )
    }
}

private val imageClient = OkHttpClient()

/** 按页缓存：键含图片地址（地址自带 hath 等信息，足以唯一标识一页）。 */
private val pageCache = mutableMapOf<String, ImageBitmap>()

private suspend fun fetchCached(
    key: String,
    url: String,
    spec: UnscrambleSpec?,
    aid: Int?,
): ImageBitmap? {
    pageCache[key]?.let { return it }
    val raw = withContext(Dispatchers.IO) {
        runCatching {
            imageClient.newCall(
                Request.Builder().url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                    )
                    .header("Referer", "https://e-hentai.org/")
                    .build(),
            ).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    } ?: return null
    val out = if (spec != null && aid != null) {
        runCatching { JmImage.unscramble(raw, aid, spec.seed) }.getOrDefault(raw)
    } else {
        raw
    }
    val image = out.asImageBitmap()
    pageCache[key] = image
    return image
}

/** 按 LiteFeatures 的窗口预取相邻页；失败静默（尽力而为，不打断阅读）。 */
private fun prefetch(
    scope: kotlinx.coroutines.CoroutineScope,
    source: ComicSource,
    pages: List<PageRef>,
    current: Int,
    chapterId: String,
) {
    val targets = ((current - LiteFeatures.prefetchBefore)..(current + LiteFeatures.prefetchAfter))
        .filter { it != current && it in pages.indices }
    if (targets.isEmpty()) return
    scope.launch {
        for (i in targets) {
            kotlinx.coroutines.delay(500)
            val p = pages.getOrNull(i) ?: continue
            runCatching {
                source.imageRequest(p, ImageQuality.HIGH).getOrNull()?.let { req ->
                    fetchCached("$chapterId#${req.url}", req.url, req.unscramble, p.extra["aid"]?.toIntOrNull())
                }
            }
        }
    }
}
