package com.tricomix.eh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 图片页解析单测。样本**照抄真实图片页的关键片段**（抓取 `/s/<imgkey>/<gid>-1` 后得到）。
 *
 * 重点防两个坑：
 * 1. 页面顶部有界面图标 img，**不能**把它们当内容图（真实页里内容图带 `style`，图标不带）；
 * 2. 内容图也可能只出现在复制按钮的 prompt 里。
 */
class GalleryPageParserTest {

    @Test
    fun `内容图取自带 style 的 img（图标不带 style，因此不会误取）`() {
        val html = """
        <img src="https://ehgt.org/g/f.png" />
        <img src="https://ehgt.org/g/p.png" />
        <div id="i3"><a href="#"><img id="img" src="https://abc.ehgt.org/h/x/1.png?hath=deadbeef" style="max-width:100%" /></a></div>
        """.trimIndent()
        val parsed = GalleryPageParser.parse(html)
        assertEquals("https://abc.ehgt.org/h/x/1.png?hath=deadbeef", parsed.imageUrl)
        assertEquals("deadbeef", parsed.hath)
    }

    @Test
    fun `没有带 style 的 img 时退回复制按钮里的地址`() {
        val html = """<script>prompt('Copy the URL below.', 'https://e-hentai.org/r/abc/forumtoken/4236322-1/pic.png')</script>"""
        assertEquals("https://e-hentai.org/r/abc/forumtoken/4236322-1/pic.png", GalleryPageParser.parse(html).imageUrl)
    }

    @Test
    fun `原图取自 Download original 链接`() {
        val html = """
        <img src="https://h/1.png?hath=aa" style="x" />
        <a href="https://e-hentai.org/fullimg/4236322/1/hgj7knaany1/pic.png">Download original</a>
        """.trimIndent()
        assertEquals("https://e-hentai.org/fullimg/4236322/1/hgj7knaany1/pic.png", GalleryPageParser.parse(html).originUrl)
    }

    @Test
    fun `只有图标时明确失败，而不是把图标当图片`() {
        val html = """<img src="https://ehgt.org/g/f.png" /><img src="https://ehgt.org/g/n.png" />"""
        var failed = false
        try { GalleryPageParser.parse(html) } catch (e: IllegalStateException) { failed = true }
        assertTrue(failed, "宁可失败也不能返回图标地址")
    }

    @Test
    fun `没有 hath 时如实为 null`() {
        val html = """<img src="https://h/1.png" style="x" />"""
        val parsed = GalleryPageParser.parse(html)
        assertNull(parsed.hath)
        assertNull(parsed.originUrl)
    }
}
