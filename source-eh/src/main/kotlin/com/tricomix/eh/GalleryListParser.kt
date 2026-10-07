package com.tricomix.eh

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 列表页解析（首页 / 搜索 / 标签页 / 收藏页共用同一套结构）。
 *
 * **依据不是猜的**：抓了一张真实的搜索结果页对照，实际结构为
 * ```
 * <table class="itg gltc">
 *   <tr><td class="gl1c glcat">分类</td>
 *       <td class="gl2c"><div class="glthumb"><img src="…"></div>
 *                        <div id="postedpop_<gid>">时间</div> …</td>
 *       <td>…<a class="glname">标题</a>…</td>
 *       <td>…<span class="glhide">上传者</span>…</td></tr>
 * ```
 * 由此纠正了三处早先的错误：
 * 1. 缩略图与上传者**不在标题所在单元格里**（紧凑布局按列分开）→ 改为**按行取值**；
 * 2. 时间元素的 id 是 **`postedpop_<gid>`**（早先写的是 `posted_<gid>`，故一直取不到）；
 * 3. 早先依赖的 `gl1e/gl3e/gl3t/gl5t` 是**另一种布局**的类名，真实 `gltc` 布局里出现次数为 0。
 *
 * 仍然**不保证**覆盖站点所有布局：取不到就如实留空（页面上确实没有的字段不编造）。
 */
object GalleryListParser {

    private val GALLERY_HREF = Regex("/g/(\\d+)/([0-9a-fA-F]+)/?")
    private val PAGES_TEXT = Regex("([0-9,]+)\\s*pages?", RegexOption.IGNORE_CASE)
    private val RATING_WIDTH = Regex("width\\s*:\\s*(\\d+(?:\\.\\d+)?)%")

    fun parse(html: String, baseUrl: String): List<EhGalleryItem> = parse(Jsoup.parse(html, baseUrl))

    fun parse(document: Document): List<EhGalleryItem> {
        val table = document.getElementsByClass("itg").firstOrNull() ?: return emptyList()
        val out = mutableListOf<EhGalleryItem>()
        for (row in table.select("tr")) {
            parseRow(row)?.let { out.add(it) }
        }
        return out
    }

    /** 一行若含 `glname` 或作品链接才算条目（表头行自然被排除）。 */
    private fun parseRow(row: Element): EhGalleryItem? {
        val nameLink = row.selectFirst(".glname a") ?: row.selectFirst(".glname")
        val href = nameLink?.attr("href").orEmpty()
        val m = GALLERY_HREF.find(href) ?: findGalleryHrefInRow(row) ?: return null
        val gid = m.groupValues[1].toLongOrNull() ?: return null
        val token = m.groupValues[2]

        val title = nameLink?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: row.selectFirst(".glink")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: row.selectFirst("a[href*=/g/]")?.text()?.trim().orEmpty()

        return EhGalleryItem(
            gid = gid,
            token = token,
            title = title,
            category = row.selectFirst(".glcat")?.text()?.trim()?.takeIf { it.isNotEmpty() },
            thumbUrl = thumbOf(row),
            uploader = uploaderOf(row),
            posted = row.getElementById("postedpop_$gid")?.text()?.trim()
                ?: row.getElementById("posted_$gid")?.text()?.trim(),
            pages = pagesOf(row),
            tags = tagsOf(row),
            rating = ratingOf(row),
        )
    }

    private fun findGalleryHrefInRow(row: Element): MatchResult? {
        for (a in row.select("a[href]")) {
            GALLERY_HREF.find(a.attr("href"))?.let { return it }
        }
        return null
    }

    /** 缩略图：先 `glthumb img`，再退到行内任意 `img`；优先 `data-src`，其次 `src`，再其次 style。 */
    private fun thumbOf(row: Element): String? {
        val img = row.selectFirst(".glthumb img") ?: row.selectFirst("img") ?: return null
        val candidates = listOf(img.attr("data-src"), img.attr("src"), styleUrl(img.attr("style")))
        return candidates.firstOrNull { it.isNotBlank() }
    }

    private fun styleUrl(style: String): String {
        val i = style.indexOf("url(")
        if (i < 0) return ""
        val rest = style.substring(i + 4)
        val end = rest.indexOf(')')
        if (end < 0) return ""
        return rest.substring(0, end).trim().trim('\'', '"')
    }

    /**
     * 上传者。真实布局里 `glhide` 元素同时含上传者与页数（如 `Cichol24 189 pages`），
     * 所以优先取其中的链接文本，其次把页数片段剥掉 —— 不能把"作者: Cichol24 189 pages"这样交出去。
     */
    private fun uploaderOf(row: Element): String? {
        row.selectFirst(".glhide a")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val raw = row.selectFirst(".glhide")?.text()?.trim() ?: return null
        val cleaned = PAGES_TEXT.replace(raw, "").trim()
        return cleaned.takeIf { it.isNotEmpty() }
    }

    private fun pagesOf(row: Element): Int? {
        val m = PAGES_TEXT.find(row.text()) ?: return null
        return m.groupValues[1].replace(",", "").toIntOrNull()
    }

    private fun tagsOf(row: Element): List<String> =
        row.select("a[href*=tag]").mapNotNull { el ->
            val href = el.attr("href")
            val i = href.indexOf("/tag/")
            if (i < 0) null else href.substring(i + 5).trim('/').takeIf { it.isNotEmpty() }
        }.distinct()

    private fun ratingOf(row: Element): Float? {
        for (el in row.select("[style*=width]")) {
            val m = RATING_WIDTH.find(el.attr("style")) ?: continue
            val pct = m.groupValues[1].toFloatOrNull() ?: continue
            return pct / 10f
        }
        return null
    }
}
