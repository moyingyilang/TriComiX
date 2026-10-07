package com.tricomix.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tricomix.android.ui.SearchHistory
import com.tricomix.android.ui.components.BottomBarItem
import com.tricomix.android.ui.components.ErrorBox
import com.tricomix.android.ui.components.FloatingBottomBar
import com.tricomix.android.ui.components.LoadingBox
import com.tricomix.android.ui.components.MessageState
import com.tricomix.core.model.Comic
import com.tricomix.core.model.Paged
import com.tricomix.core.model.Section
import com.tricomix.core.source.Capability
import com.tricomix.core.source.ComicSource
import kotlinx.coroutines.launch

/** 底部导航的三个页签（对应源的三类列表）。 */
enum class SearchTab(val label: String) {
    Home("首页"),
    Search("搜索"),
    Favorites("收藏"),
}

/**
 * 搜索屏（搬迁自 JMNeXt 的 `SearchScreen`，按 `docs/ui-port-plan.md` 第六节的清单适配）。
 *
 * 底部导航用 JMNeXt 自家的 [FloatingBottomBar]，取代早先手搓的三颗按钮 —— 页签切换会
 * **按源能力自动取对应列表**：源没有首页能力时切过去也不会发请求，而是明确说明不支持。
 *
 * 只认 `core` 的模型与 [ComicSource]。原实现里的排序/日期筛选、热词、随机推荐在统一接口里
 * 没有位置，因此这里不显示（缺口已记入 `docs/ui-port-plan.md`）。
 * 搜索历史属于本机状态，由上层保存（[SearchHistory]）。
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
    var tab by remember { mutableStateOf(SearchTab.Search) }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("输入关键词后点搜索") }
    var sections by remember { mutableStateOf<List<Section>>(emptyList()) }
    var results by remember { mutableStateOf<List<Comic>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf(history.all()) }
    val caps = source.capabilities

    fun run(what: String, fetch: suspend ComicSource.() -> Result<*>) {
        busy = true
        failed = false
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
                    onFailure = {
                        failed = true
                        status = "${what}失败：${it.message}"
                    },
                )
            } finally {
                busy = false
            }
        }
    }

    // 切页签时按能力取列表（没有该能力的源不会发请求）
    LaunchedEffect(tab, sourceName) {
        when (tab) {
            SearchTab.Home -> if (caps.contains(Capability.HOME)) run("首页") { home() }
            SearchTab.Favorites -> if (caps.contains(Capability.FAVORITES)) run("收藏") { favorites(1) }
            SearchTab.Search -> Unit
        }
    }

    Column(Modifier.fillMaxSize()) {
        login()

        if (tab == SearchTab.Search) {
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
                if (recents.isNotEmpty()) {
                    Button(onClick = { history.clear(); recents = emptyList(); onRecentsChanged() }) { Text("清历史") }
                }
            }
        }

        if (busy) LoadingBox(Modifier.padding(top = 8.dp))
        if (failed) {
            ErrorBox(message = status)
        } else {
            Text(status, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
        capabilityHint(caps).takeIf { it.isNotEmpty() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }

        Box(Modifier.weight(1f)) {
            when {
                !caps.contains(tab.capability()) && tab != SearchTab.Search ->
                    MessageState(title = "该源不支持${tab.label}", description = "换一个页签，或换一个图源")
                results.isEmpty() && sections.isEmpty() && !busy ->
                    MessageState(title = "还没有内容", description = "换个关键词试试")
                else -> ComicResults(sections = sections, results = results, onOpen = onOpen)
            }
        }

        FloatingBottomBar(
            items = listOf(
                BottomBarItem(SearchTab.Home.label, Icons.Filled.Home),
                BottomBarItem(SearchTab.Search.label, Icons.Filled.Search),
                BottomBarItem(SearchTab.Favorites.label, Icons.Filled.Favorite),
            ),
            selectedIndex = tab.ordinal,
            onSelect = { tab = SearchTab.entries[it] },
        )
    }
}

/** 页签对应的能力（搜索页签没有额外能力要求）。 */
private fun SearchTab.capability(): Capability = when (this) {
    SearchTab.Home -> Capability.HOME
    SearchTab.Favorites -> Capability.FAVORITES
    SearchTab.Search -> Capability.SEARCH
}
