package com.tricomix.pica

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 会话（令牌）可被应用读写的单测。
 *
 * 为什么需要它：令牌原本只活在内存里，应用重启就要重新登录。要持久化，
 * 应用必须能**读出**它（登录后存本机）与**写回**它（启动时恢复）。
 */
class PicaSessionTest {

    @Test
    fun `令牌可设置 可读取 可清除`() {
        val client = PicaClient()
        assertEquals(null, client.token, "初始不应有令牌")
        client.token = "tok-1"
        assertEquals("tok-1", client.token)
        client.signOut()
        assertEquals(null, client.token, "登出后必须清空")
    }

    @Test
    fun `源能读写自己客户端的令牌（供应用持久化）`() {
        val source = PicaSource()
        source.client.token = "tok-2"
        assertEquals("tok-2", source.client.token)

        // 模拟"重启后恢复"：新客户端载入此前保存的令牌
        val restoredClient = PicaClient()
        restoredClient.token = "tok-2"
        assertEquals("tok-2", PicaSource(restoredClient).client.token)
    }
}
