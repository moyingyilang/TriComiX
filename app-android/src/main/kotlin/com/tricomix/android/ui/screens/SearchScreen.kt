package com.tricomix.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tricomix.android.ui.SearchHistory
import com.tricomix.android.ui.components.LoadingBox
import com.tricomix.android.ui.components.ErrorBox
import com.tricomix.android.ui.components.MessageState
import com.tricomix.core.model.Comic
import com.tricomix.core.model.Paged
import com.tricomix.core.model.Section
import com.tricomix.core.source.Capability
import com.tricomix.core.source.ComicSource
import kotlinx.coroutines.launch

/**
 * 搜索屏（搬迁自 JMNeXt 的 `SearchScreen`，按 `docs/ui-port-plan.md` 第六节的清单适配）。
 *
 * 只认 `core` 的模型与 [ComicSource]。原实现里的排序/日期筛选、热词、随机推荐在统一接口里
 * **没有位置**，因此这里不显示这些控件 —— 按能力显隐，而不是给一个点了没反应的按钮
 * （缺口已记入 `docs/ui-port-plan.md`）。
 *
 * 搜索历史属于本机状态，由上层自己保存（[SearchHistory]），不依赖任何图源。
 */
@Composable
fun SearchRoute(
    source: ComicSource,
    sourceName: String,
    history: SearchHistory,
    login: @Composable () -> Unit,
    onOpen: (Comic) -> Unit,
    onRecentsChanged: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("输入关键词后点搜索") }
    var sections by remember { mutableStateOf<List<Section>>(emptyList()) }
    var results by remember { mutableStateOf<List<Comic>>(emptyList()) }
    val caps = source.capabilities
    var recents by remember { mutableStateOf(history.all()) }

    fun run(what: String, fetch: suspend ComicSource.() -> Result<*>) {
        busy = true
        status = "$what…"
        scope.launch {
            try {
                fetch(source).fold(
                    onSuccess = { payload ->
                        when (payload) {
                            is List<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val secs = payload as List<Section>
                                sections = secs
                                results = secs.flatMap { it.items }
                                status = "$what ${results.size} 条（${secs.size} 个分区）"
                            }
                            is Paged<*> -> {
                                @Suppress("UNCHECKED_CAST")
                                val paged = payload as Paged<Comic>
                                sections = emptyList()
                                results = paged.items
                                status = "$what ${paged.items.size} 条"
                            }
                            else -> status = "$what：无法识别的返回类型"
                        }
                    },
                    onFailure = { status = "${what}失败：${it.message}" },
                )
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        login()

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("关键词") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        recents.take(5).takeIf { it.isNotEmpty() }?.let { words ->
            Text(
                "最近搜索（点一下填入）：" + words.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.clickable { query = words.first() }.padding(top = 4.dp),
            )
        }

        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy && query.isNotBlank() && caps.contains(Capability.SEARCH),
                onClick = {
                    recents = history.add(query)
                    onRecentsChanged()
                    run("搜索") { search(query, 1) }
                },
            ) { Text("搜索") }
            Button(
                enabled = !busy && caps.contains(Capability.HOME),
                onClick = { run("首页") { home() } },
            ) { Text("首页") }
            Button(
                enabled = !busy && caps.contains(Capability.FAVORITES),
                onClick = { run("收藏") { favorites(1) } },
            ) { Text("收藏") }
            if (recents.isNotEmpty()) {
                Button(onClick = { history.clear(); recents = emptyList(); onRecentsChanged() }) { Text("清历史") }
            }
        }

        if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp))
        Text(status, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        capabilityHint(caps).takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Divider(Modifier.padding(vertical = 8.dp))

        ComicResults(sections = sections, results = results, onOpen = onOpen)
    }
}
