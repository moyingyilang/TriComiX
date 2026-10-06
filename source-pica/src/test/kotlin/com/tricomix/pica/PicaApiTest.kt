package com.tricomix.pica

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 解析与 URL 构造的单测。
 *
 * **样本是我们自己写的合成 JSON**（形状取自对参照实现的静态阅读），
 * 真实服务返回什么，必须由使用者在自己的环境里验证。
 */
class PicaApiTest {

    private val json = PicaJson.json

    @Test
    fun `端点路径按参照实现构造`() {
        assertEquals("auth/sign-in", PicaApi.signIn())
        assertEquals("comics/abc", PicaApi.comic("abc"))
        assertEquals("comics/abc/eps?page=2", PicaApi.episodes("abc", 2))
        assertEquals("comics/abc/order/3/pages?page=1", PicaApi.pages("abc", 3, 1))
        assertEquals("comics/search?page=1&q=a+b", PicaApi.search("a b", 1))
        assertEquals("users/favourite?page=1", PicaApi.favourites(1))
    }

    @Test
    fun `分页信封解析 comics docs`() {
        val text = """
        {"code":200,"data":{"comics":{"docs":[
          {"_id":"c1","title":"标题一","author":"作者","tags":["tag"],"thumb":{"fileServer":"https://img.example/","path":"p.jpg"}}
        ],"total":1,"limit":20,"page":1,"pages":3}}}
        """.trimIndent()
        val root = json.parseToJsonElement(text)
        val page = PicaJson.pageOf(root, "comics")!!
        val comics = json.decodeFromJsonElement(
            ListSerializer(PicaComic.serializer()), page["docs"]!!
        )
        assertEquals(1, comics.size)
        assertEquals("c1", comics[0].id)
        assertEquals("https://img.example/static/p.jpg", comics[0].thumb?.url())
        assertEquals(200, PicaJson.code(root))
    }

    @Test
    fun `详情接受 comic 单个对象`() {
        val root = json.parseToJsonElement("""{"data":{"comic":{"_id":"x","title":"T"}}}""")
        assertEquals("T", PicaJson.detail(root).title)
    }

    @Test
    fun `详情也兼容 comics 是单个对象的形态`() {
        val root = json.parseToJsonElement("""{"data":{"comics":{"_id":"y","title":"U"}}}""")
        assertEquals("U", PicaJson.detail(root).title)
    }

    @Test
    fun `详情遇到分页形态会明确报错而不是猜`() {
        val root = json.parseToJsonElement("""{"data":{"comics":{"docs":[]}}}""")
        var failed = false
        try { PicaJson.detail(root) } catch (e: IllegalStateException) { failed = true }
        assertTrue(failed, "缺 _id/title 的形态不应被当成详情接受")
    }

    @Test
    fun `章节解析出 order 与标题`() {
        val root = json.parseToJsonElement(
            """{"data":{"eps":{"docs":[{"_id":"e1","title":"第1话","order":1},{"_id":"e2","title":"第2话","order":2}],"pages":1}}}"""
        )
        val page = PicaJson.pageOf(root, "eps")!!
        val eps = json.decodeFromJsonElement(ListSerializer(PicaEpisode.serializer()), page["docs"]!!)
        assertEquals(listOf(1, 2), eps.map { it.order })
        assertEquals("第2话", eps[1].title)
    }

    @Test
    fun `页图解析出 media 地址`() {
        val root = json.parseToJsonElement(
            """{"data":{"pages":{"docs":[{"_id":"p1","media":{"fileServer":"https://img.example","path":"a/1.jpg"}}],"pages":1}}}"""
        )
        val page = PicaJson.pageOf(root, "pages")!!
        val docs = json.decodeFromJsonElement(ListSerializer(PicaPageDoc.serializer()), page["docs"]!!)
        assertEquals("https://img.example/static/a/1.jpg", docs[0].media?.url())
    }

    @Test
    fun `media 缺字段时不做无根据拼接`() {
        assertNull(PicaMedia(fileServer = null, path = "a.jpg").url())
        assertNull(PicaMedia(fileServer = "https://x", path = null).url())
        assertNull(PicaMedia(fileServer = "", path = "a.jpg").url())
    }

    @Test
    fun `非 200 的 code 会被识别`() {
        val root = json.parseToJsonElement("""{"code":401,"message":"unauthorized","data":{}}""")
        assertEquals(401, PicaJson.code(root))
        assertTrue(root.jsonObject.containsKey("message"))
    }
}
