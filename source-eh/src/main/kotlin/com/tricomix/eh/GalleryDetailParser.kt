package com.tricomix.eh

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 详情页（`/g/<gid>/<token>/`）的**元数据**解析。
 *
 * 依据：对参照实现 `GalleryDetailParser` 的结构阅读，它用到的元素 id 为
 * `gn`（标题）、`gj`（上传者）、`gd1`（封面，写在 style 里）、`gdc`（分类）、
 * `gdd`（描述/日期区）、`taglist`（标签表）、`rating_label`/`rating_count`（评分）。
 *
 * **注意**：该作品**页图不在详情页 HTML 里**（参照实现另走 `api.php` 的 JSON，键含
 * `i3`/`i6`/`i7`/`fullimg`）。那是 [GalleryPageApiParser] 的职责，本轮未实现。
 *
 * **未验证**：从未对真实站点发过请求。
 */
data class EhGalleryDetail(
    val gid: Long?,
    val token: String?,
    val title: String?,
    val category: String?,
    val uploader: String?,
    val coverUrl: String?,
    val posted: String?,
    val pages: Int?,
    val rating: Float?,
    val tags: List<String>,
    val description: String?,
)

object GalleryDetailParser {

    private val HREF = Regex("""/g/(\d+)/([0-9a-fA-F]+)/?""")
    private val PAGES_TEXT = Regex("""([0-9,]+)\s*pages?""", RegexOption.IGNORE_CASE)
    private val RATING_TEXT = Regex("""([0-9]+(?:\.[0-9]+)?)""")

    fun parse(html: String, baseUrl: String): EhGalleryDetail = parse(Jsoup.parse(html, baseUrl))

    fun parse(document: Document): EhGalleryDetail {
        val gidToken = findGidToken(document)
        return EhGalleryDetail(
            gid = gidToken?.first,
            token = gidToken?.second,
            title = text(document, "gn"),
            category = text(document, "gdc"),
            uploader = text(document, "gj"),
            coverUrl = coverOf(document),
            posted = postedOf(document),
            pages = pagesOf(document),
            rating = ratingOf(document),
            tags = tagsOf(document),
            description = descriptionOf(document),
        )
    }

    /** gid/token 从页面里的任一作品链接上取（详情页自身链接或规范链接）。 */
    private fun findGidToken(document: Document): Pair<Long, String>? {
        for (a in document.select("a[href]")) {
            val m = HREF.find(a.attr("href")) ?: continue
            val gid = m.groupValues[1].toLongOrNull() ?: continue
            return gid to m.groupValues[2]
        }
        val m = HREF.find(document.location()) ?: return null
        return m.groupValues[1].toLongOrNull()?.let { it to m.groupValues[2] }
    }

    private fun text(document: Document, id: String): String? =
        document.getElementById(id)?.text()?.trim()?.takeIf { it.isNotEmpty() }

    /** 封面在 `gd1` 第一个子元素的 style 里（`background-image:url(...)`）。 */
    private fun coverOf(document: Document): String? {
        val div = document.getElementById("gd1")?.child(0) ?: return null
        val style = div.attr("style")
        val i = style.indexOf("url(")
        if (i < 0) return null
        val rest = style.substring(i + 4)
        val end = rest.indexOf(')')
        if (end < 0) return null
        return rest.substring(0, end).trim().trim('\'', '"').takeIf { it.isNotEmpty() }
    }

    /** 发布时间在 `gdd` 区里（形如 `Posted: 2024-01-02 03:04`）。 */
    private fun postedOf(document: Document): String? {
        val gdd = document.getElementById("gdd") ?: return null
        val m = Regex("""Posted:\s*([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\s+[0-9:]+)?)""", RegexOption.IGNORE_CASE)
            .find(gdd.text())
        return m?.groupValues?.get(1)?.trim()
    }

    private fun pagesOf(document: Document): Int? {
        val scope = document.getElementById("gdd") ?: document
        val m = PAGES_TEXT.find(scope.text()) ?: return null
        return m.groupValues[1].replace(",", "").toIntOrNull()
    }

    private fun ratingOf(document: Document): Float? {
        val label = text(document, "rating_label") ?: return null
        val m = RATING_TEXT.find(label) ?: return null
        return m.groupValues[1].toFloatOrNull()
    }

    /** 标签在 `taglist` 表里，每个 `a` 指向 `/tag/<tag>`。 */
    private fun tagsOf(document: Document): List<String> {
        val list = document.getElementById("taglist") ?: return emptyList()
        return list.select("a[href]").mapNotNull { el ->
            val href = el.attr("href")
            val i = href.indexOf("/tag/")
            if (i < 0) null else href.substring(i + 5).trim('/').takeIf { it.isNotEmpty() }
        }.distinct()
    }

    private fun descriptionOf(document: Document): String? {
        val el: Element = document.getElementById("gdd") ?: return null
        // gdd 里除发布时间外还可能有简介；只取去掉 Posted 行后的剩余文本，且要求非空。
        val raw = el.text().replace(Regex("""Posted:\s*[0-9-]+(?:\s+[0-9:]+)?"""), "").trim()
        return raw.takeIf { it.isNotEmpty() }
    }
}
