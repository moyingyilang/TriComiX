package com.tricomix.eh

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 详情页（`/g/<gid>/<token>/`）的解析。
 *
 * 依据：对参照实现 `GalleryDetailParser` / `GalleryPageParser` 的结构阅读：
 * - 元素 id：`gn`（标题）、`gj`（上传者）、`gd1`（封面，写在 style 里）、`gdc`（分类）、
 *   `gdd`（描述/日期区）、`taglist`（标签表）、`rating_label`/`rating_count`（评分）；
 * - **`showKey`**：详情页里的 JS 变量 `var showkey="…"`（`showpage` 请求必需）；
 * - **`pageTokens`**：每一页的链接形如 `/s/<imgkey>/<gid>-<page>`，其中的 `imgkey`
 *   就是 `showpage` 需要的逐页键。
 *
 * 正则与字段提取都是我们自己写的（只借用其**形状**），不复制参照代码。
 *
 * **未验证**：从未对真实站点发过请求。站点改版会让选择器与正则失效，此时解析结果为空/为 null，
 * 由调用方按"缺什么就报什么错"处理。
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
    /** 详情页 JS 变量 `showkey`，[EhApi.showPage] 必需。 */
    val showKey: String? = null,
    /** 各页链接 `/s/<imgkey>/<gid>-<page>` 里的 `imgkey`，按出现顺序去重。 */
    val pageTokens: List<String> = emptyList(),
)

object GalleryDetailParser {

    private val HREF = Regex("/g/(\\d+)/([0-9a-fA-F]+)/?")
    private val PAGES_TEXT = Regex("([0-9,]+)\\s*pages?", RegexOption.IGNORE_CASE)
    private val RATING_TEXT = Regex("([0-9]+(?:\\.[0-9]+)?)")
    private val SHOW_KEY = Regex("var\\s+showkey\\s*=\\s*\"([0-9a-zA-Z]+)\"")

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
            showKey = showKeyOf(document),
            pageTokens = pageTokensOf(document, gidToken?.first),
        )
    }

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

    private fun postedOf(document: Document): String? {
        val gdd = document.getElementById("gdd")?.text() ?: return null
        val m = Regex("Posted:\\s*([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\\s+[0-9:]+)?)", RegexOption.IGNORE_CASE).find(gdd)
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
        val raw = el.text().replace(Regex("Posted:\\s*[0-9-]+(?:\\s+[0-9:]+)?"), "").trim()
        return raw.takeIf { it.isNotEmpty() }
    }

    /** 详情页里的 `var showkey="…";`。 */
    private fun showKeyOf(document: Document): String? =
        SHOW_KEY.find(document.html())?.groupValues?.get(1)

    /**
     * 逐页 `imgkey`：页链接形如 `/s/<imgkey>/<gid>-<page>`。
     * 限定 gid 可以避免把页面里其它同类链接（例如别人的作品）算进来。
     */
    private fun pageTokensOf(document: Document, gid: Long?): List<String> {
        if (gid == null) return emptyList()
        val re = Regex("/s/([0-9a-fA-F]{6,})/$gid-\\d+")
        return re.findAll(document.html()).map { it.groupValues[1] }.distinct().toList()
    }
}
