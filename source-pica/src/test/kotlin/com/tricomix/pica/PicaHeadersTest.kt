package com.tricomix.pica

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PicaHeadersTest {

    private val key = ByteArray(32) { it.toByte() }

    private fun headers(token: String? = null, time: Long = 1700000000L, nonce: String = "n1") =
        PicaHeaders.build(
            path = "comics/123",
            method = "GET",
            time = time,
            nonce = nonce,
            appUuid = "test-uuid",
            token = token,
            signingKey = key,
        )

    @Test
    fun `包含协议要求的全部请求头`() {
        val h = headers()
        for (name in listOf(
            "api-key", "accept", "app-platform", "app-version", "app-build-version",
            "app-uuid", "app-channel", "image-quality", "time", "nonce", "signature",
        )) {
            assertTrue(h.containsKey(name), "缺少请求头 $name：${h.keys}")
        }
    }

    @Test
    fun `签名与签名函数一致（不是另算一套）`() {
        val h = headers()
        val expect = PicaSigning.sign(key, "comics/123", 1700000000L, "n1", "GET")
        assertEquals(expect, h["signature"])
        assertEquals(64, h["signature"]!!.length)
    }

    @Test
    fun `time 与 nonce 原样随请求发出（必须与签名时一致）`() {
        val h = headers(time = 1234567890L, nonce = "abc")
        assertEquals("1234567890", h["time"])
        assertEquals("abc", h["nonce"])
    }

    @Test
    fun `没有令牌时不带 authorization`() {
        assertFalse(headers().containsKey("authorization"))
        assertFalse(headers(token = "").containsKey("authorization"))
    }

    @Test
    fun `有令牌时带上 authorization`() {
        assertEquals("tok-123", headers(token = "tok-123")["authorization"])
    }

    @Test
    fun `不同质量档位会改变 image-quality 头`() {
        val low = PicaHeaders.build(
            path = "comics/1", method = "GET", time = 1L, nonce = "n", appUuid = "u",
            imageQuality = PicaImageQuality.LOW.value, signingKey = key,
        )
        assertEquals("low", low["image-quality"])
        assertEquals("original", PicaImageQuality.ORIGINAL.value)
    }

    @Test
    fun `修改路径会改变签名（说明路径确实参与签名）`() {
        val a = PicaHeaders.build("comics/1", "GET", 1700000000L, "n1", "u", signingKey = key)
        val b = PicaHeaders.build("comics/2", "GET", 1700000000L, "n1", "u", signingKey = key)
        assertTrue(a["signature"] != b["signature"])
    }
}

/** 追加：`app-nonce` 必须被发送（此前漏发导致登录 401）。 */
class PicaAppNonceTest {

    @Test
    fun `请求头包含 app-nonce`() {
        val h = PicaHeaders.build("auth/sign-in", "POST", 1700000000L, "n", "uuid")
        assertEquals(PicaHeaders.APP_NONCE, h["app-nonce"])
    }
}

/** 追加：UA、Content-Type、固定 uuid、build-version 必须与完整客户端一致。 */
class PicaHeaderParityTest {

    private fun h() = PicaHeaders.build("auth/sign-in", "POST", 1700000000L, PicaHeaders.APP_NONCE)

    @Test
    fun `必须发送 User-Agent`() {
        assertEquals("okhttp/3.8.1", h()["User-Agent"])
    }

    @Test
    fun `所有请求都带 Content-Type`() {
        assertEquals(PicaHeaders.JSON_CONTENT_TYPE, h()["Content-Type"])
    }

    @Test
    fun `app-uuid 是固定值而不是每次随机`() {
        assertEquals("defaultUuid", h()["app-uuid"])
        assertEquals(h()["app-uuid"], h()["app-uuid"])
    }

    @Test
    fun `app-build-version 对齐完整客户端`() {
        assertEquals("45", h()["app-build-version"])
    }
}
