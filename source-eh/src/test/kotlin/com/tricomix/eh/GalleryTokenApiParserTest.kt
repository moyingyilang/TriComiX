package com.tricomix.eh

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `gtoken` 响应解析的单测。**样本是我们自己写的合成 JSON**（形状取自对参照实现的静态阅读）。
 * 真实响应必须由使用者在真实环境验证。
 */
class GalleryTokenApiParserTest {

    @Test
    fun `按顺序解析每页 token`() {
        val body = """{"tokenlist":[{"token":"aaa"},{"token":"bbb"},{"token":"ccc"}]}"""
        assertEquals(listOf("aaa", "bbb", "ccc"), GalleryTokenApiParser.parse(body))
    }

    @Test
    fun `某一项带 error 时明确抛错并带上原因`() {
        val body = """{"tokenlist":[{"token":"aaa"},{"error":"Invalid page"}]}"""
        var message: String? = null
        try { GalleryTokenApiParser.parse(body) } catch (e: IllegalStateException) { message = e.message }
        assertTrue(message?.contains("Invalid page") == true, "实际：$message")
    }

    @Test
    fun `空 tokenlist 返回空列表`() {
        assertEquals(emptyList(), GalleryTokenApiParser.parse("""{"tokenlist":[]}"""))
    }

    @Test
    fun `token 缺失或空白时为 null，而不是空串`() {
        assertEquals(listOf(null, null), GalleryTokenApiParser.parse("""{"tokenlist":[{},{"token":"  "}]}"""))
    }

    @Test
    fun `未知字段被忽略（站点加字段不会让我们崩）`() {
        val body = """{"tokenlist":[{"token":"a","future":"x"}],"extra":1}"""
        assertEquals(listOf("a"), GalleryTokenApiParser.parse(body))
    }

    @Test
    fun `批量大小是保守的正数`() {
        assertTrue(EhPageKeys.BATCH in 1..50, "实际：${EhPageKeys.BATCH}")
    }
}
