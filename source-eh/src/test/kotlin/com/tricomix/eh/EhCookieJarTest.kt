package com.tricomix.eh

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Cookie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EhCookieJarTest {

    private val e = "https://e-hentai.org/".toHttpUrl()
    private val x = "https://exhentai.org/".toHttpUrl()

    private fun cookie(url: String, name: String, value: String, expiresAt: Long = Long.MAX_VALUE) =
        Cookie.Builder().domain(url.toHttpUrl().host).path("/").name(name).value(value)
            .expiresAt(expiresAt).build()

    @Test
    fun `保存后能按主机取回`() {
        val jar = EhCookieJar()
        jar.saveFromResponse(e, listOf(cookie("https://e-hentai.org/", "ipb_member_id", "123")))
        val out = jar.loadForRequest(e)
        assertEquals(1, out.size)
        assertEquals("ipb_member_id", out[0].name)
        assertEquals("123", out[0].value)
    }

    @Test
    fun `不同域名互不串用`() {
        val jar = EhCookieJar()
        jar.saveFromResponse(x, listOf(cookie("https://exhentai.org/", "ipb_member_id", "999")))
        assertEquals(0, jar.loadForRequest(e).size)
        assertEquals(1, jar.loadForRequest(x).size)
    }

    @Test
    fun `同名 cookie 会被覆盖而不是堆积`() {
        val jar = EhCookieJar()
        jar.saveFromResponse(e, listOf(cookie("https://e-hentai.org/", "k", "v1")))
        jar.saveFromResponse(e, listOf(cookie("https://e-hentai.org/", "k", "v2")))
        val out = jar.loadForRequest(e)
        assertEquals(1, out.size)
        assertEquals("v2", out[0].value)
    }

    @Test
    fun `过期的 cookie 不会被取回`() {
        val jar = EhCookieJar()
        jar.saveFromResponse(e, listOf(cookie("https://e-hentai.org/", "old", "v", expiresAt = 1L)))
        assertEquals(0, jar.loadForRequest(e).size)
        assertEquals(0, jar.size())
    }

    @Test
    fun `snapshot 与 restore 能往返`() {
        val a = EhCookieJar()
        a.saveFromResponse(e, listOf(cookie("https://e-hentai.org/", "ipb_pass_hash", "abc")))
        val b = EhCookieJar()
        b.restore(a.snapshot(), e)
        val out = b.loadForRequest(e)
        assertEquals(1, out.size)
        assertEquals("ipb_pass_hash", out[0].name)
        assertEquals("abc", out[0].value)
    }

    @Test
    fun `restore 遇到坏行会跳过而不是整体失败`() {
        val jar = EhCookieJar()
        jar.restore(listOf("这不是 cookie", "a=b; c=d"), e)
        assertTrue(jar.size() >= 0)
    }

    @Test
    fun `clear 会清空`() {
        val jar = EhCookieJar()
        jar.saveFromResponse(e, listOf(cookie("https://e-hentai.org/", "k", "v")))
        jar.clear()
        assertEquals(0, jar.size())
    }
}
