package com.tricomix.pica

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PicaSigningTest {

    private val key = ByteArray(32) { it.toByte() }
    private val apiKey = "TEST-API-KEY-NOT-REAL"

    private fun sign(nonce: String = "abc123", time: Long = 1700000000L) = PicaSigning.sign(
        signingKey = key,
        baseUrl = PicaSigning.BASE_URL,
        path = "comics",
        time = time,
        nonce = nonce,
        httpMethod = "GET",
        apiKey = apiKey,
        appVersion = "2.2.1.3.3.4",
        buildVersion = "44",
    )

    @Test
    fun `签名原文按协议顺序拼接`() {
        val s = PicaSigning.buildInput(
            PicaSigning.BASE_URL, "comics", 1700000000L, "abc123", "GET", apiKey, "2.2.1.3.3.4", "44"
        )
        assertEquals("${PicaSigning.BASE_URL}comics1700000000abc123GET${apiKey}2.2.1.3.3.444", s)
    }

    @Test
    fun `签名是小写十六进制且长度为 64`() {
        val sig = sign()
        assertEquals(64, sig.length)
        assertTrue(sig.all { it in "0123456789abcdef" }, "应只含小写十六进制字符：$sig")
    }

    @Test
    fun `相同输入产生相同签名（确定性）`() {
        assertEquals(sign(), sign())
    }

    @Test
    fun `nonce 变化会改变签名`() {
        assertNotEquals(sign(nonce = "abc123"), sign(nonce = "abc124"))
    }

    @Test
    fun `时间戳变化会改变签名`() {
        assertNotEquals(sign(time = 1700000000L), sign(time = 1700000001L))
    }

    @Test
    fun `不同密钥产生不同签名（密钥确实参与运算）`() {
        val other = ByteArray(32) { (it + 1).toByte() }
        val a = PicaSigning.sign(other, PicaSigning.BASE_URL, "comics", 1700000000L, "abc123", "GET", apiKey, "2.2.1.3.3.4", "44")
        assertNotEquals(sign(), a)
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
