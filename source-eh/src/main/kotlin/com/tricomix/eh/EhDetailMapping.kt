package com.tricomix.eh

import com.tricomix.core.model.Chapter
import com.tricomix.core.model.Comic
import com.tricomix.core.model.ComicDetail
import com.tricomix.core.source.SourceError

/**
 * 详情页到统一模型的映射，以及 id 编码约定（`"<gid>|<token>"`）。
 *
 * 单独成文件的原因：EH 有一处**语义差异**必须在唯一的地方说清楚 ——
 * 它没有"章节"，一个作品就是一段连续的图片序列。统一模型是按章节组织的，
 * 所以这里把它表示为**单个合成的章节**（id 就是作品 id，标题就是作品标题）。
 * 这样界面层不必为 EH 破例，而将来阅读器改成"页序列"模型时，这里也只需要改一处。
 */
internal object EhDetailMapping {

    fun parseComicId(comicId: String): Pair<Long, String> {
        val parts = comicId.split('|')
        if (parts.size != 2) throw SourceError.Parse("EH 作品 id 形状应为 <gid>|<token>：$comicId")
        val gid = parts[0].toLongOrNull() ?: throw SourceError.Parse("gid 不是数字：$comicId")
        if (parts[1].isBlank()) throw SourceError.Parse("token 为空：$comicId")
        return gid to parts[1]
    }

    fun toComicDetail(comicId: String, detail: EhGalleryDetail, fallbackTitle: String?): ComicDetail {
        val title = detail.title ?: fallbackTitle.orEmpty()
        return ComicDetail(
            comic = Comic(
                sourceId = "eh",
                id = comicId,
                title = title,
                coverUrl = detail.coverUrl,
                author = detail.uploader,
                tags = detail.tags,
                description = detail.description,
            ),
            chapters = listOf(
                Chapter(
                    id = comicId,
                    title = title.ifEmpty { "全部" },
                    order = 0,
                )
            ),
        )
    }
}
