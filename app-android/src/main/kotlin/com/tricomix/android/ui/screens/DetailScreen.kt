package com.tricomix.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tricomix.core.model.Chapter
import com.tricomix.core.model.Comic
import com.tricomix.core.model.PageRef
import com.tricomix.core.source.Capability
import com.tricomix.core.source.ComicSource
import kotlinx.coroutines.launch

/**
 * 详情屏（搬迁自 JMNeXt 的 `DetailScreen`，按 `docs/ui-port-plan.md` 第九节的清单适配）。
 *
 * 只认 `core` 的模型与 [ComicSource]：原实现里的 `AlbumDetail` / `ListItem` / 跟踪 / 点赞 /
 * 下载 / 收藏夹等都未进入统一接口，因此这里**不显示**对应入口 —— 按能力显隐，
 * 而不是给一个点了没反应的按钮。
 *
 * 章节规则与 JMNeXt 原文一致（其注释亦如此记载）：
 * - 多章节：列出全部；
 * - 单章节：等价于"从头开始"；
 * - **无章节**：调用方已用作品 id 合成唯一章节（见 `MainActivity.openDetail`）。
 */
@Composable
fun DetailRoute(
    comic: Comic,
    chapters: List<Chapter>,
    lastChapterId: String? = null,
    source: ComicSource,
    onBack: () -> Unit,
    onOpenReader: (Comic, Chapter, List<PageRef>) -> Unit,
    onStatus: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    fun open(chapter: Chapter) {
        busy = true
        onStatus("载入页列表…")
        scope.launch {
            source.pages(chapter.id).fold(
                onSuccess = { pages ->
                    onStatus("共 ${pages.size} 页")
                    onOpenReader(comic, chapter, pages)
                },
                onFailure = { onStatus("页列表失败：${it.message}") },
            )
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBack) { Text("返回") }
            if (source.capabilities.contains(Capability.FAVORITE_WRITE)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            source.toggleFavorite(comic.id).fold(
                                onSuccess = { onStatus("已更新收藏") },
                                onFailure = { onStatus("收藏失败：${it.message}") },
                            )
                            busy = false
                        }
                    },
                ) { Text("收藏/取消") }
            }
            if (chapters.isNotEmpty()) {
                Button(enabled = !busy, onClick = { open(chapters.first()) }) { Text("从头开始") }
                chapters.firstOrNull { it.id == lastChapterId }?.let { last ->
                    Button(enabled = !busy, onClick = { open(last) }) { Text("继续阅读") }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") {
                comic.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = comic.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                Text(
                    comic.title.ifBlank { "(无标题)" },
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                comic.author?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                // 详情简介（core.Comic 自带，JMNeXt 的详情页也有这一段）
                comic.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(desc, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                if (comic.tags.isNotEmpty()) {
                    Text(comic.tags.joinToString("　"), style = MaterialTheme.typography.bodySmall)
                }
                // 该源缺什么能力就写出来（目标要求"按能力显隐，或明确提示该源不支持"）
                Text("章节数：${chapters.size}", style = MaterialTheme.typography.labelSmall)
                capabilityHint(source.capabilities).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                if (busy) CircularProgressIndicator(Modifier.padding(vertical = 6.dp))
                Divider(Modifier.padding(vertical = 8.dp))
            }

            if (chapters.isEmpty()) {
                item(key = "no-chapter") { Text("这个源没有给出章节") }
            } else {
                items(chapters, key = { it.id }) { chapter ->
                    Column(
                        Modifier.fillMaxWidth().clickable(enabled = !busy) { open(chapter) }.padding(12.dp),
                    ) {
                        Text(chapter.title.ifBlank { "第 ${chapter.order + 1} 话" })
                    }
                    Divider()
                }
            }
        }
    }
}

/** 与搜索页使用同一份能力提示，避免两处措辞不一致。 */
internal fun capabilityHint(caps: Set<Capability>): String {
    val labels = listOf(
        Capability.HOME to "首页",
        Capability.SEARCH to "搜索",
        Capability.FAVORITES to "收藏",
        Capability.LOGIN to "登录",
        Capability.HISTORY to "历史",
        Capability.DOWNLOAD to "下载",
    )
    val missing = labels.filterNot { caps.contains(it.first) }.map { it.second }
    return if (missing.isEmpty()) "" else "该源不支持：" + missing.joinToString("、")
}
