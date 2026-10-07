package com.tricomix.eh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 列表解析单测。样本**照抄真实页面的行结构**（抓取一张真实搜索结果页后得到），
 * 而不是我凭想象写的形状。
 *
 * 真实行结构（`table.itg.gltc`，四列）：
 * ```
 * <tr><td class="gl1c glcat">分类</td>
 *     <td class="gl2c"><div class="glthumb"><img src="…"></div><div id="postedpop_<gid>">时间</div></td>
 *     <td><a class="glname" href="/g/<gid>/<token>/">标题</a> <div>12 pages</div> <a href="/tag/…">…</a></td>
 *     <td><span class="glhide">上传者</span></td></tr>
 * ```
 */
class GalleryListParserTest {

    private val html = """
    <html><body>
    <table class="itg gltc">
      <tr><th></th><th>Published</th><th>Title</th><th class="glhide">Uploader</th></tr>
      <tr>
        <td class="gl1c glcat"><div class="cn ct1">Misc</div></td>
        <td class="gl2c">
          <div class="glthumb" id="it4236322"><div><img src="https://ehgt.org/w/02/698/01728-2huj354s.webp" /></div></div>
          <div><div id="postedpop_4236322">2026-10-06 21:27</div></div>
        </td>
        <td>
          <a class="glname" href="https://e-hentai.org/g/4236322/e84511a768/">Style Test [AI Generated]</a>
          <div class="glink">Style Test [AI Generated]</div>
          <div>12 pages</div>
          <a href="https://e-hentai.org/tag/language/chinese">language/chinese</a>
          <div style="width:85%"></div>
        </td>
        <td><span class="glhide">someUploader</span></td>
      </tr>
      <tr>
        <td class="gl1c glcat"><div class="cn ct1">Doujinshi</div></td>
        <td class="gl2c">
          <div class="glthumb"><div><img data-src="https://ehgt.org/t/2.webp" src="" /></div></div>
          <div><div id="postedpop_7654321">2026-01-01 00:00</div></div>
        </td>
        <td><a class="glname" href="https://exhentai.org/g/7654321/fff000/">第二条</a><div>1,234 pages</div></td>
        <td><span class="glhide">另一作者</span></td>
      </tr>
    </table>
    </body></html>
    """.trimIndent()

    private fun parsed() = GalleryListParser.parse(html, "https://e-hentai.org/")

    @Test
    fun `解析出两条并取到 gid 与 token`() {
        val items = parsed()
        assertEquals(2, items.size)
        assertEquals(4236322L, items[0].gid)
        assertEquals("e84511a768", items[0].token)
        assertEquals(7654321L, items[1].gid)
        assertEquals("fff000", items[1].token)
    }

    @Test
    fun `缩略图能取到（真实布局里它在另一列）`() {
        val items = parsed()
        assertEquals("https://ehgt.org/w/02/698/01728-2huj354s.webp", items[0].thumbUrl)
        assertEquals("https://ehgt.org/t/2.webp", items[1].thumbUrl, "应优先 data-src")
    }

    @Test
    fun `上传者取自 glhide（真实布局里也在另一列）`() {
        assertEquals("someUploader", parsed()[0].uploader)
        assertEquals("另一作者", parsed()[1].uploader)
    }

    @Test
    fun `分类取自 glcat`() {
        assertEquals("Misc", parsed()[0].category)
        assertEquals("Doujinshi", parsed()[1].category)
    }

    @Test
    fun `发布时间取自 postedpop_ 前缀的 id`() {
        assertEquals("2026-10-06 21:27", parsed()[0].posted)
        assertEquals("2026-01-01 00:00", parsed()[1].posted)
    }

    @Test
    fun `标题 页数 标签 评分`() {
        val first = parsed()[0]
        assertEquals("Style Test [AI Generated]", first.title)
        assertEquals(12, first.pages)
        assertEquals(listOf("language/chinese"), first.tags)
        assertEquals(8.5f, first.rating)
        assertEquals(1234, parsed()[1].pages, "千分位逗号要能解析")
    }

    @Test
    fun `表头行不会产生条目`() {
        assertTrue(parsed().none { it.title == "Title" })
    }

    @Test
    fun `没有 itg 表格时返回空列表而不是抛异常`() {
        assertEquals(0, GalleryListParser.parse("<html>nothing</html>", "https://e-hentai.org/").size)
    }

    @Test
    fun `缺评分时返回 null 而不是 0`() {
        assertNull(parsed()[1].rating)
    }

    @Test
    fun `条目自带规范 URL`() {
        assertEquals("https://e-hentai.org/g/4236322/e84511a768/", parsed()[0].url)
    }
}

/** 追加：真实布局里 glhide 同时含上传者与页数，必须剥掉页数。 */
class EhUploaderCleanupTest {

    @Test
    fun `glhide 里的页数会被剥离`() {
        val html = """
        <table class="itg gltc"><tr>
          <td class="gl1c glcat"><div>Misc</div></td>
          <td class="gl2c"><div class="glthumb"><img src="https://ehgt.org/x.webp"></div></td>
          <td><a class="glname" href="https://e-hentai.org/g/1/aa/">T</a></td>
          <td><span class="glhide">Cichol24 189 pages</span></td>
        </tr></table>
        """.trimIndent()
        val item = GalleryListParser.parse(html, "https://e-hentai.org/")[0]
        assertEquals("Cichol24", item.uploader)
        assertEquals(189, item.pages)
    }
}
