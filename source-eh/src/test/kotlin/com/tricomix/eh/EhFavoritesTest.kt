package com.tricomix.eh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** EH 收藏页的视图判定与 URL 单测（不联网，样本自写）。 */
class EhFavoritesTest {

    @Test
    fun `收藏页 URL`() {
        val url = EhUrl(EhHost.E_HENTAI)
        assertEquals("https://e-hentai.org/favorites.php", url.favorites())
        assertEquals("https://e-hentai.org/favorites.php?page=3", url.favorites(3))
    }

    @Test
    fun `有列表表格时视为列表视图`() {
        assertTrue(EhFavorites.isListView("""<table class="itg"><tr><td></td></tr></table>"""))
    }

    @Test
    fun `登录页不会被当成空收藏`() {
        val loginPage = "<html><body><form id=\"login\">Username<br>Password</form></body></html>"
        assertFalse(EhFavorites.isListView(loginPage))
    }

    @Test
    fun `列表视图能解析出条目`() {
        val html = """
        <table class="itg"><tr><td>
          <a class="glname" href="https://e-hentai.org/g/42/abcd/">收藏的作品</a>
          <div>3 pages</div>
        </td></tr></table>
        """.trimIndent()
        val items = EhFavorites.parse(html, "https://e-hentai.org/")
        assertEquals(1, items.size)
        assertEquals(42L, items[0].gid)
        assertEquals("abcd", items[0].token)
    }
}
