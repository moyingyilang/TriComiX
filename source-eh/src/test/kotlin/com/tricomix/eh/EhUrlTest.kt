package com.tricomix.eh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EhUrlTest {

    private val e = EhUrl(EhHost.E_HENTAI)
    private val x = EhUrl(EhHost.EXHENTAI)

    @Test
    fun `首页与搜索`() {
        assertEquals("https://e-hentai.org/", e.home())
        assertEquals("https://e-hentai.org/?f_search=abc", e.search("abc"))
        assertEquals("https://e-hentai.org/?f_search=abc&page=2", e.search("abc", 2))
    }

    @Test
    fun `查询词会被 URL 编码`() {
        assertTrue(e.search("a b&c").contains("a+b%26c"), e.search("a b&c"))
    }

    @Test
    fun `作品与分页 URL 形状`() {
        assertEquals("https://exhentai.org/g/123/abcdef/", x.gallery(123, "abcdef"))
        assertEquals("https://exhentai.org/g/123/abcdef/?p=3", x.galleryPage(123, "abcdef", 3))
    }

    @Test
    fun `收藏与归档`() {
        assertEquals("https://e-hentai.org/gallerypopups.php?gid=1&t=tok&act=addfav", e.addFavorite(1, "tok"))
        assertEquals("https://e-hentai.org/archiver.php?gid=1&token=tok", e.archive(1, "tok"))
        assertEquals("https://e-hentai.org/archiver.php?gid=1&token=tok&or=1", e.archive(1, "tok", original = true))
    }
}
