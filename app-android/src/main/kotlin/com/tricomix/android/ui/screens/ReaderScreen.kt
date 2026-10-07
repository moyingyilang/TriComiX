package com.tricomix.android.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.tricomix.android.LiteFeatures
import com.tricomix.android.data.image.JmImage
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

/**
 * 阅读屏（搬迁自 JMNeXt 的 `ReaderScreen`，先搬"取图与翻页"这条主线）。
 *
 * 与原项目一致的处理：
 * - **反切片**：JM 的图在服务端被切条打乱，直接用 [JmImage.unscramble]（含 `bands` 预检，
 *   算不出条带时原样返回）—— 这段逻辑本该属于阅读器，之前以私有函数躺在脚手架里；
 * - **预取**：按 [LiteFeatures] 的窗口预取相邻页，翻页命中缓存；
 * - **节流**：预取之间留间隔（EH 对密集请求会拒绝，并发取多张反而更易失败）；
 * - **取图请求头**：带浏览器 UA 与 Referer（EH 的图片主机拒绝裸请求）。
 *
 * 尚未搬的交互：左右滑动翻页、双指缩放（下一轮做）。只吃 `core` 与 [ComicSource]。
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBack) { Text("返回") }
        Button(enabled = index > 0, onClick = { index -= 1 }) { Text("上一页") }
        Button(enabled = index < pages.size - 1, onClick = { index += 1 }) { Text("下一页") }
    }
    Text(
        "${index + 1} / ${pages.size}　预取 ${LiteFeatures.prefetchBefore}/${LiteFeatures.prefetchAfter}",
        style = MaterialTheme.typography.bodySmall,
    )

    val page = pages.getOrNull(index)
    if (page != null) {
        LaunchedEffect(chapter.id, index) {
            bitmap = null
            imageError = null
            source.imageRequest(page, ImageQuality.HIGH).fold(
                onSuccess = { req ->
                    val key = "${chapter.id}#${req.url}"
                    val bmp = fetchCached(key, req.url, req.unscramble, page.extra["aid"]?.toIntOrNull())
                    if (bmp == null) imageError = "图片下载或解码失败" else {
                        bitmap = bmp
                        onStatus("${index + 1}/${pages.size}")
                    }
                    prefetch(scope, source, pages, index, chapter.id)
                },
                onFailure = { imageError = "取图失败：${it.message}" },
            )
        }
    }

    when {
        imageError != null -> Text("错误：$imageError")
        bitmap == null -> CircularProgressIndicator(Modifier.padding(top = 12.dp))
        else -> Image(
            bitmap = bitmap!!,
            contentDescription = "第 ${index + 1} 页",
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
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
