package com.tricomix.eh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 登录解析与表单字段的单测。**样本是我们自己写的合成 HTML**，形状取自对参照实现的静态阅读。
 * 真实登录必须由使用者用自己的账号在真实环境验证 —— 本项目不代跑。
 */
class EhSignInParserTest {

    @Test
    fun `成功时返回用户名`() {
        val html = "<html><body><p>You are now logged in as: alice<</body></html>"
        assertEquals("alice", EhSignInParser.parse(html))
    }

    @Test
    fun `失败时抛错并带上服务端原话（h4 形式）`() {
        val html = "<h4>The error returned was:</h4>\n<p>Sorry, an error occurred</p>"
        var message: String? = null
        try { EhSignInParser.parse(html) } catch (e: IllegalStateException) { message = e.message }
        assertTrue(message?.contains("Sorry, an error occurred") == true, "实际：$message")
    }

    @Test
    fun `失败时抛错（postcolor 形式）`() {
        val html = "<span class=\"postcolor\">Your account has been suspended</span>"
        var message: String? = null
        try { EhSignInParser.parse(html) } catch (e: IllegalStateException) { message = e.message }
        assertTrue(message?.contains("suspended") == true, "实际：$message")
    }

    @Test
    fun `两种标记都没有时明确报"无法解析"，不猜成功`() {
        var failed = false
        try { EhSignInParser.parse("<html>nothing</html>") } catch (e: IllegalStateException) { failed = true }
        assertTrue(failed)
    }

    @Test
    fun `表单字段与地址符合协议`() {
        assertEquals("https://forums.e-hentai.org/index.php?act=Login&CODE=01", EhSignIn.URL)
        assertEquals("https://forums.e-hentai.org/index.php?act=Login&CODE=00", EhSignIn.REFERER)
        val form = EhSignIn.form("u", "p")
        assertEquals("u", form["UserName"])
        assertEquals("p", form["PassWord"])
        assertEquals("Log me in", form["submit"])
        assertEquals("1", form["CookieDate"])
        assertEquals("off", form["temporary_https"])
    }
}
