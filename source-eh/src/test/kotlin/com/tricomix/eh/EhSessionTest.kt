package com.tricomix.eh

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 会话（cookie）可被应用导出与恢复的单测 —— 该站登录态就是 cookie，
 * 所以持久化它等于"记住登录"。
 */
class EhSessionTest {

    private val url = "https://e-hentai.org/".toHttpUrl()

    private fun cookie(name: String, value: String) =
        Cookie.Builder().domain("e-hentai.org").path("/").name(name).value(value)
            .expiresAt(Long.MAX_VALUE).build()

    @Test
    fun `cookie 能导出并在新源里恢复`() {
        val first = EhSource(host = EhHost.E_HENTAI)
        first.client.cookieJar.saveFromResponse(url, listOf(cookie("ipb_member_id", "123")))
        val saved = first.client.cookieJar.snapshot()
        assertEquals(1, saved.size, "应导出一条 cookie")

        // 模拟重启：新源载入前次导出的 cookie
        val second = EhSource(host = EhHost.E_HENTAI)
        second.client.cookieJar.restore(saved, url)
        val loaded = second.client.cookieJar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("ipb_member_id", loaded[0].name)
    }

    @Test
    fun `源暴露的 host 与 URL 构造一致`() {
        val source = EhSource(host = EhHost.EXHENTAI)
        assertEquals(EhHost.EXHENTAI, source.host)
        assertEquals("https://exhentai.org/", source.host.baseUrl + "/")
    }
}
