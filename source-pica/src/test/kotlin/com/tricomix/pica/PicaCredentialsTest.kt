package com.tricomix.pica

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 这些断言的目的很具体：**兜住字符串转义写错**。
 * 密钥原文含 `$` 与反斜杠，写进 Kotlin 字符串字面量时若转义有误，长度或首尾字符就会不对。
 */
class PicaCredentialsTest {

    @Test
    fun `签名密钥是 63 字节`() {
        assertEquals(63, PicaCredentials.signingKeyBytes.size)
    }

    @Test
    fun `签名密钥首尾字符与原文一致`() {
        val k = PicaCredentials.SIGNING_KEY
        assertEquals('~', k.first())
        assertEquals('n', k.last())
    }

    @Test
    fun `签名密钥含原文中的两个美元符与一个反斜杠`() {
        val k = PicaCredentials.SIGNING_KEY
        assertEquals(2, k.count { it == '$' })
        assertEquals(1, k.count { it == '\\' })
    }

    @Test
    fun `api-key 是 29 个字符`() {
        assertEquals(29, PicaCredentials.API_KEY.length)
    }
}
