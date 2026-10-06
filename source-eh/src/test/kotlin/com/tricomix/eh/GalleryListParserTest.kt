package com.tricomix.eh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 列表解析单测。**样本是我们自己写的合成 HTML**（结构取自对参照实现的静态阅读），
 * 真实页面由使用者在自己环境验证。
 *
 * 注意样本的关键形状：**缩略图与标题在同一个单元格里**。参照实现是拿同一个 `element`
 * 先找 `glthumb` 再找 `glname`，最初我把它们写成两个 `td`，测试立刻失败了 —— 是样本不真实，
 * 不是解析器太严。
 */
class GalleryListParserTest {

    private val html = """
    <html><body>
    <table class="itg">
      <tr><td class="itg">分类</td><td>标题</td></tr>
      <tr>
        <td class="gl1e">
          <div class="gl1e">
            <div>Doujinshi</div>
            <a href="https://e-hentai.org/g/1234567/abc123def/">
              <img class="glthumb" data-src="https://ehgt.org/t/1234567-1.jpg" src="">
            </a>
          </div>
          <a class="glname" href="https://e-hentai.org/g/1234567/abc123def/">测试标题一</a>
          <div class="gl3e"><a href="https://e-hentai.org/uploader/alice">alice</a></div>
          <div>12 pages</div>
          <a href="https://e-hentai.org/tag/language/chinese">language/chinese</a>
          <a href="https://e-hentai.org/tag/artist/bob">artist/bob</a>
          <div id="posted_1234567">2024-01-02 03:04</div>
          <div style="width:85%"></div>
        </td>
      </tr>
      <tr>
        <td class="gl3t">
          <div class="gl3t">
            <a href="https://exhentai.org/g/7654321/fff000/">
              <img src="https://ehgt.org/t/7654321-1.jpg">
            </a>
          </div>
          <a class="glname" href="https://exhentai.org/g/7654321/fff000/">测试标题二</a>
          <div>1,234 pages</div>
        </td>
      </tr>
    </table>
    </body></html>
    """.trimIndent()

    @Test
    fun `解析出两条，并正确取到 gid 与 token`() {
        val items = GalleryListParser.parse(html, "https://e-hentai.org/")
        assertEquals(2, items.size)
        assertEquals(1234567L, items[0].gid)
        assertEquals("abc123def", items[0].token)
        assertEquals(7654321L, items[1].gid)
        assertEquals("fff000", items[1].token)
    }

    @Test
    fun `标题取自 glname`() {
        val items = GalleryListParser.parse(html, "https://e-hentai.org/")
        assertEquals("测试标题一", items[0].title)
        assertEquals("测试标题二", items[1].title)
    }

    @Test
    fun `缩略图优先 data-src 再退到 src`() {
        val items = GalleryListParser.parse(html, "https://e-hentai.org/")
        assertEquals("https://ehgt.org/t/1234567-1.jpg", items[0].thumbUrl)
        assertEquals("https://ehgt.org/t/7654321-1.jpg", items[1].thumbUrl)
    }

    @Test
    fun `页数支持千分位逗号`() {
        val items = GalleryListParser.parse(html, "https://e-hentai.org/")
        assertEquals(12, items[0].pages)
        assertEquals(1234, items[1].pages)
    }

    @Test
    fun `上传者 发布时间 标签 评分`() {
        val first = GalleryListParser.parse(html, "https://e-hentai.org/")[0]
        assertEquals("alice", first.uploader)
        assertEquals("2024-01-02 03:04", first.posted)
        assertEquals(listOf("language/chinese", "artist/bob"), first.tags)
        assertEquals(8.5f, first.rating)
    }

    @Test
    fun `表头行不会产生条目`() {
        val items = GalleryListParser.parse(html, "https://e-hentai.org/")
        assertTrue(items.none { it.title == "标题" })
    }

    @Test
    fun `没有 itg 表格时返回空列表而不是抛异常`() {
        assertEquals(0, GalleryListParser.parse("<html><body>nothing</body></html>", "https://e-hentai.org/").size)
    }

    @Test
    fun `缺少可解析链接的单元格被跳过`() {
        val onlyHeader = """<table class="itg"><tr><td class="itg">分类</td><td>标题</td></tr></table>"""
        assertEquals(0, GalleryListParser.parse(onlyHeader, "https://e-hentai.org/").size)
    }

    @Test
    fun `条目自带规范 URL`() {
        val first = GalleryListParser.parse(html, "https://e-hentai.org/")[0]
        assertEquals("https://e-hentai.org/g/1234567/abc123def/", first.url)
    }

    @Test
    fun `没有评分的条目返回 null 而不是 0`() {
        val second = GalleryListParser.parse(html, "https://e-hentai.org/")[1]
        assertNull(second.rating)
    }

    @Test
    fun `style 里写图片地址时也能取到`() {
        val styleHtml = """
        <table class="itg"><tr><td>
          <div class="gl1e"><img style="background-image:url('https://ehgt.org/t/s1.jpg')"></div>
          <a class="glname" href="https://e-hentai.org/g/1/aa/">来自 style</a>
        </td></tr></table>
        """.trimIndent()
        val items = GalleryListParser.parse(styleHtml, "https://e-hentai.org/")
        assertEquals(1, items.size)
        assertEquals("https://ehgt.org/t/s1.jpg", items[0].thumbUrl)
    }
}
