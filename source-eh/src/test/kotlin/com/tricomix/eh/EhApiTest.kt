package com.tricomix.eh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `api.php` 请求体与 `showpage` 响应的单测。
 * **样本是我们自己写的合成 JSON**（形状取自对参照实现的静态阅读），真实响应由使用者验证。
 */
class EhApiTest {

    @Test
    fun `api 入口用当前域名的 api_php`() {
        assertEquals("https://e-hentai.org/api.php", EhApi.endpoint(EhHost.E_HENTAI))
        assertEquals("https://exhentai.org/api.php", EhApi.endpoint(EhHost.EXHENTAI))
    }

    @Test
    fun `gdata 请求体形状`() {
        assertEquals(
            """{"method":"gdata","gidlist":[[123,"tok"]],"namespace":1}""",
            EhApi.gdata(123, "tok"),
        )
    }

    @Test
    fun `showpage 的页码是 1 起（对外 0 起）`() {
        val body = EhApi.showPage(123, 0, "ik", "sk")
        assertTrue(body.contains("\"page\":1"), body)
        assertTrue(EhApi.showPage(123, 4, "ik", "sk").contains("\"page\":5"))
    }

    @Test
    fun `gtoken 请求体形状`() {
        val body = EhApi.gtoken(listOf(Triple(1L, "t1", 1), Triple(1L, "t2", 2)))
        assertEquals("""{"method":"gtoken","pagelist":[[1,"t1",1],[1,"t2",2]]}""", body)
    }

    @Test
    fun `showpage 解析出图片地址与 hath`() {
        val body = """
        {"i3":"<img id=\"img\" src=\"https://abc.ehgt.org/h/x-123-456/1.jpg?hath=deadbeef&x=1\" style=\"max-width:100%\">",
         "i6":"","i7":"<a href=\"https://abc.ehgt.org/h/1.jpg?hath=deadbeeffullimg&amp;x=f\">"}
        """.trimIndent()
        val parsed = GalleryPageApiParser.parse(body)
        assertEquals("https://abc.ehgt.org/h/x-123-456/1.jpg?hath=deadbeef&x=1", parsed.imageUrl)
        assertEquals("deadbeef", parsed.hath)
    }

    @Test
    fun `新版原图用 onclick prompt`() {
        val body = """{"i3":"<img src=\"https://h/1.jpg\" style=\"\">","i7":"<a href=\"#\" onclick=\"prompt('Copy the URL below.', 'https://h/orig/1.jpg?hath=zz')\">"}"""
        val parsed = GalleryPageApiParser.parse(body)
        assertEquals("https://h/orig/1.jpg?hath=zz", parsed.originUrl)
    }

    @Test
    fun `旧版原图用 fullimg 链接`() {
        val body = """{"i3":"<img src=\"https://h/1.jpg\" style=\"\">","i6":"<a href=\"https://h/orig/1.jpg?hath=q1fullimg=q1\">"}"""
        assertEquals("https://h/orig/1.jpg?hath=q1", GalleryPageApiParser.parse(body).originUrl)
    }

    @Test
    fun `地址里没有 hath 时如实返回 null`() {
        val body = """{"i3":"<img src=\"https://h/1.jpg\" style=\"\">"}"""
        val parsed = GalleryPageApiParser.parse(body)
        assertNull(parsed.hath)
        assertNull(parsed.originUrl)
    }

    @Test
    fun `响应带 error 时明确失败而不是当成功`() {
        val body = """{"error":"Invalid page"}"""
        var failed = false
        try { GalleryPageApiParser.parse(body) } catch (e: IllegalStateException) { failed = true }
        assertTrue(failed)
    }

    @Test
    fun `缺 i3 时明确失败`() {
        var failed = false
        try { GalleryPageApiParser.parse("""{"i6":"x"}""") } catch (e: IllegalStateException) { failed = true }
        assertTrue(failed)
    }

    @Test
    fun `转义字符会被还原`() {
        val body = """{"i3":"<img src=\"https://h/a\tb.jpg\" style=\"\">"}"""
        assertEquals("https://h/a\tb.jpg", GalleryPageApiParser.parse(body).imageUrl)
    }
}
