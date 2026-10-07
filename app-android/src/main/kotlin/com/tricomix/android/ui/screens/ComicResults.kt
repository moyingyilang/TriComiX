package com.tricomix.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tricomix.android.ui.components.ComicCard
import com.tricomix.core.model.Comic
import com.tricomix.core.model.Section

/**
 * 结果列表（首页分区与搜索结果共用）。
 *
 * 两种排布，对应 JMNeXt 的两种用法：
 * - **有分区时**（首页信息流）：每个分区是「标题 + 一行横向滑动的卡片」——
 *   这正是 JMNeXt 首页的形态；早先我把分区里的卡片竖着排，一个几十条的分区就会拉出一条长列表，
 *   观感与真应用差很远；
 * - **无分区时**（搜索/收藏）：就是竖排列表。
 *
 * 只吃 `core` 的模型，卡片用搬迁自 JMNeXt 的 [ComicCard]。
 */
@Composable
fun ComicResults(
    sections: List<Section>,
    results: List<Comic>,
    onOpen: (Comic) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 横向卡片宽度：手机上大约一屏露出 2.5 张，与常见漫画应用一致
    val cardWidth = 132.dp

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        if (sections.isNotEmpty()) {
            sections.forEach { sec ->
                item(key = "sec-${sec.title}") {
                    Text(
                        sec.title.ifBlank { "未命名分区" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
                    )
                }
                item(key = "row-${sec.title}") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(sec.items, key = { "${sec.title}-${it.id}" }) { comic ->
                            Column(Modifier.width(cardWidth)) {
                                ComicCard(comic = comic, onClick = { onOpen(comic) })
                            }
                        }
                    }
                }
            }
        } else {
            items(results, key = { it.id }) { comic ->
                ComicCard(comic = comic, onClick = { onOpen(comic) })
            }
        }
    }
}
