package com.tricomix.pica

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PicaSigningTest {

    private val key = ByteArray(32) { it.toByte() }

    private fun sign(nonce: String = "abc123", time: Long = 1700000000L) =
        PicaSigning.sign(key, "comics", time, nonce, "GET")

    @Test
    fun `签名原文为 5 段且顺序符合原生层结论`() {
        val s = PicaSigning.buildInput("comics", 1700000000L, "abc123", "GET", PicaCredentials.API_KEY)
        assertEquals("comics1700000000abc123GET${PicaCredentials.API_KEY}", s)
    }

    @Test
    fun `签名原文不含 baseUrl 与版本号`() {
        val s = PicaSigning.buildInput("comics", 1L, "n", "GET", "K")
        assertTrue(!s.contains("http"), "不应含 baseUrl：$s")
        assertTrue(!s.contains("picaapi"), "不应含域名：$s")
    }

    @Test
    fun `签名是小写十六进制且长度为 64`() {
        val sig = sign()
        assertEquals(64, sig.length)
        assertTrue(sig.all { it in "0123456789abcdef" }, "应为小写十六进制：$sig")
    }

    @Test
    fun `确定性：相同输入相同签名`() {
        assertEquals(sign(), sign())
    }

    @Test
    fun `nonce 或时间或方法变化都会改变签名`() {
        assertNotEquals(sign(nonce = "abc123"), sign(nonce = "abc124"))
        assertNotEquals(sign(time = 1700000000L), sign(time = 1700000001L))
        assertNotEquals(sign(), PicaSigning.sign(key, "comics", 1700000000L, "abc123", "POST"))
    }

    @Test
    fun `不同密钥产生不同签名`() {
        val other = ByteArray(32) { (it + 1).toByte() }
        assertNotEquals(sign(), PicaSigning.sign(other, "comics", 1700000000L, "abc123", "GET"))
    }

    @Test
    fun `内置密钥路径可用于方案 A`() {
        val sig = PicaSigning.signWithEmbeddedKey("comics", 1700000000L, "abc123", "GET")
        assertEquals(64, sig.length)
        assertEquals(PicaSigning.sign(PicaCredentials.signingKeyBytes, "comics", 1700000000L, "abc123", "GET"), sig)
    }

    @Test
    fun `时间同步按服务端差值更新偏移`() {
        val sync = PicaTimeSync()
        assertEquals(1700000000L, sync.timestamp(1700000000L))
        sync.updateFrom(serverTimeSeconds = 1700000100L, localTimeSeconds = 1700000000L)
        assertEquals(100L, sync.offsetSeconds)
        assertEquals(1700000100L, sync.timestamp(1700000000L))
    }
}
