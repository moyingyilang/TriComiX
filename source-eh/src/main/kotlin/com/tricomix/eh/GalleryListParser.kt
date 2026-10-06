package com.tricomix.eh

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 列表页解析（首页 / 搜索 / 标签页共用同一套结构）。
 *
 * 依据：对参照实现 `GalleryListParser` 的**结构阅读**（它遍历 `table.itg` 的单元格，
 * 用 `glname` 取标题链接、`glthumb` 取缩略图、`gl1e/gl3e/gl3t/gl5t` 区分布局变体、
 * `posted_<gid>` 取发布时间），本实现是**我们自己写的**，不复制其代码。
 *
 * **未验证**：从未对真实站点发过请求；选择器来自静态阅读，站点改版会让它失效 ——
 * 因此 [parse] 对"没有找到任何条目"与"结构变了"都不抛异常，而是返回空列表，
 * 由调用方结合 HTTP 状态判断（避免把解析失败伪装成"没有结果"是做不到的，
 * 但我们至少不在这里编造数据）。
 */
object GalleryListParser {

    private val GALLERY_HREF = Regex("""/g/(\d+)/([0-9a-fA-F]+)/?""")
    private val PAGES_TEXT = Regex("""([0-9,]+)\s*pages?""", RegexOption.IGNORE_CASE)

    fun parse(html: String, baseUrl: String): List<EhGalleryItem> = parse(Jsoup.parse(html, baseUrl))

    fun parse(document: Document): List<EhGalleryItem> {
        val table = document.getElementsByClass("itg").firstOrNull() ?: return emptyList()
        val out = mutableListOf<EhGalleryItem>()
        for (row in table.select("tr")) {
            for (cell in row.select("td")) {
                parseCell(cell)?.let { out.add(it) }
            }
        }
        return out
    }

    /** 一个单元格若含 `glname` 才算作品（表头与广告位会被自然排除）。 */
    private fun parseCell(cell: Element): EhGalleryItem? {
        val nameLink = cell.selectFirst(".glname a") ?: cell.selectFirst(".glname") ?: return null
        val href = nameLink.attr("href")
        val m = GALLERY_HREF.find(href) ?: return null
        val gid = m.groupValues[1].toLongOrNull() ?: return null
        val token = m.groupValues[2]

        val title = nameLink.text().trim().ifEmpty {
            cell.selectFirst(".glink")?.text()?.trim().orEmpty()
        }

        return EhGalleryItem(
            gid = gid,
            token = token,
            title = title,
            category = categoryOf(cell),
            thumbUrl = thumbOf(cell),
            uploader = uploaderOf(cell),
            posted = cell.getElementById("posted_$gid")?.text()?.trim(),
            pages = pagesOf(cell),
            tags = tagsOf(cell),
            rating = ratingOf(cell),
        )
    }

    /** 分类是单元格里第一个 `div` 的文本（参照实现用单元格文本查表，这里只如实取文本）。 */
    private fun categoryOf(cell: Element): String? =
        cell.selectFirst("div")?.text()?.trim()?.takeIf { it.isNotEmpty() }

    /** 缩略图：优先 `glthumb img` 的 `src`/`data-src`，其次 `gl1e`/`gl3t` 里的 `img`。 */
    private fun thumbOf(cell: Element): String? {
        val img = cell.selectFirst(".glthumb img")
            ?: cell.selectFirst(".gl1e img")
            ?: cell.selectFirst(".gl3t img")
            ?: cell.selectFirst(".gl3e img")
            ?: return null
        val candidates = listOf(img.attr("data-src"), img.attr("src"), styleUrl(img.attr("style")))
        return candidates.firstOrNull { it.isNotBlank() }
    }

    /** 有些布局把图片地址写在 `style` 的 `url(...)` 里。 */
    private fun styleUrl(style: String): String {
        val i = style.indexOf("url(")
        if (i < 0) return ""
        val rest = style.substring(i + 4)
        val end = rest.indexOf(')')
        if (end < 0) return ""
        return rest.substring(0, end).trim().trim('\'', '"')
    }

    private fun uploaderOf(cell: Element): String? =
        cell.selectFirst(".gl3e a")?.text()?.trim()?.takeIf { it.isNotEmpty() }
            ?: cell.select("a").firstOrNull { it.attr("href").contains("/uploader/") }?.text()?.trim()

    private fun pagesOf(cell: Element): Int? {
        val text = cell.text()
        return PAGES_TEXT.find(text)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
    }

    /** 标签链接形如 `/tag/<tag>`。 */
    private fun tagsOf(cell: Element): List<String> =
        cell.select("a[href*=tag]").mapNotNull { el ->
            val href = el.attr("href")
            val idx = href.indexOf("/tag/")
            if (idx < 0) null else href.substring(idx + 5).trim('/').takeIf { it.isNotEmpty() }
        }.distinct()

    /** 评分写在某个元素的 `style` 宽度里（形如 `width:80%`），转成 0..10 的分。 */
    private fun ratingOf(cell: Element): Float? {
        for (el in cell.select("[style*=width]")) {
            val style = el.attr("style")
            val m = Regex("""width\s*:\s*(\d+(?:\.\d+)?)%""").find(style) ?: continue
            val pct = m.groupValues[1].toFloatOrNull() ?: continue
            return pct / 10f
        }
        return null
    }
}
