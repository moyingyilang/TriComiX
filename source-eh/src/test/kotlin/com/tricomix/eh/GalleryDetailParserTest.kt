package com.tricomix.eh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 详情页元数据解析单测。**样本是我们自己写的合成 HTML**，结构取自对参照实现的静态阅读。
 * 真实页面必须由使用者在自己的环境验证。
 */
class GalleryDetailParserTest {

    private val html = """
    <html><body>
      <div id="gd1"><div style="background-image:url('https://ehgt.org/c/cover.jpg')"></div></div>
      <h1 id="gn">测试作品标题</h1>
      <div id="gdc">Doujinshi</div>
      <div id="gdd">Posted: 2024-01-02 03:04 &nbsp; 12 pages &nbsp; 1.5 MB</div>
      <div id="gj">alice</div>
      <div id="rating_label">8.5</div>
      <div id="rating_count">(120)</div>
      <table id="taglist">
        <tr><td><a href="https://e-hentai.org/tag/language/chinese">language/chinese</a></td></tr>
        <tr><td><a href="https://e-hentai.org/tag/artist/bob">artist/bob</a></td></tr>
      </table>
      <a href="https://e-hentai.org/g/1234567/abc123def/">规范链接</a>
    </body></html>
    """.trimIndent()

    private fun parsed() = GalleryDetailParser.parse(html, "https://e-hentai.org/g/1234567/abc123def/")

    @Test
    fun `标题 分类 上传者`() {
        val d = parsed()
        assertEquals("测试作品标题", d.title)
        assertEquals("Doujinshi", d.category)
        assertEquals("alice", d.uploader)
    }

    @Test
    fun `gid 与 token 从链接解析`() {
        val d = parsed()
        assertEquals(1234567L, d.gid)
        assertEquals("abc123def", d.token)
    }

    @Test
    fun `封面从 gd1 的 style 取出`() {
        assertEquals("https://ehgt.org/c/cover.jpg", parsed().coverUrl)
    }

    @Test
    fun `发布时间 页数 评分`() {
        val d = parsed()
        assertEquals("2024-01-02 03:04", d.posted)
        assertEquals(12, d.pages)
        assertEquals(8.5f, d.rating)
    }

    @Test
    fun `标签来自 taglist`() {
        assertEquals(listOf("language/chinese", "artist/bob"), parsed().tags)
    }

    @Test
    fun `描述里不残留 Posted 行`() {
        val d = parsed()
        assertEquals(false, d.description?.contains("Posted"))
    }

    @Test
    fun `缺元素时返回 null 而不是编造`() {
        val d = GalleryDetailParser.parse("<html><body>空</body></html>", "https://e-hentai.org/")
        assertNull(d.title)
        assertNull(d.category)
        assertNull(d.uploader)
        assertNull(d.coverUrl)
        assertNull(d.pages)
        assertNull(d.rating)
        assertEquals(0, d.tags.size)
    }
}
