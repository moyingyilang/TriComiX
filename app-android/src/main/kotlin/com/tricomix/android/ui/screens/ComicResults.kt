package com.tricomix.android.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tricomix.android.ui.components.ComicCard
import com.tricomix.core.model.Comic
import com.tricomix.core.model.Section

/**
 * 结果列表（首页分区与普通搜索结果共用）。
 *
 * 从 `MainActivity` 抽出来，理由与 `DetailRoute` 相同：界面代码不该堆在脚手架里。
 * 只吃 `core` 的模型，卡片用搬迁自 JMNeXt 的 [ComicCard]。
 *
 * 首页有分区概念（JMNeXt 的首页就是分区的），搜索与收藏没有 —— 所以分区为空时
 * 退化为一张平铺列表，而不是显示空的标题行。
 */
@Composable
fun ComicResults(
    sections: List<Section>,
    results: List<Comic>,
    onOpen: (Comic) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize()) {
        if (sections.isNotEmpty()) {
            sections.forEach { sec ->
                item(key = "sec-${sec.title}") {
                    Text(
                        sec.title.ifBlank { "未命名分区" },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(sec.items, key = { "${sec.title}-${it.id}" }) { comic ->
                    ComicCard(comic = comic, onClick = { onOpen(comic) })
                }
            }
        } else {
            items(results, key = { it.id }) { comic ->
                ComicCard(comic = comic, onClick = { onOpen(comic) })
            }
        }
    }
}
