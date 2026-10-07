package com.tricomix.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tricomix.android.ui.theme.jmShape
import com.tricomix.core.model.Comic

/**
 * 列表里的作品卡片。
 *
 * **这是"底层可切换"的关键接口面**：它只吃 `core.Comic`，不认识任何源的具体类型
 * （主项目里的版本吃的是 JM 的 `ListItem`，所以必须改写 —— 只改这一个类型，卡片的观感照旧）。
 */
@Composable
fun ComicCard(
    comic: Comic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        AsyncImage(
            model = comic.coverUrl,
            contentDescription = comic.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(jmShape(12.dp)),
        )
        Text(
            text = comic.title.ifBlank { "(无标题)" },
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = comic.author ?: comic.tags.take(2).joinToString(" / "),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
