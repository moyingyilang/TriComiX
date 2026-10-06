package com.tricomix.eh

import com.tricomix.core.source.SourceError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 详情映射与 id 编码约定的单测（不联网）。 */
class EhDetailMappingTest {

    private val detail = EhGalleryDetail(
        gid = 1234567L,
        token = "abc",
        title = "作品标题",
        category = "Doujinshi",
        uploader = "alice",
        coverUrl = "https://ehgt.org/c.jpg",
        posted = "2024-01-02 03:04",
        pages = 12,
        rating = 8.5f,
        tags = listOf("artist/bob"),
        description = "简介",
    )

    @Test
    fun `id 编码解析回来`() {
        val (gid, token) = EhDetailMapping.parseComicId("1234567|abc")
        assertEquals(1234567L, gid)
        assertEquals("abc", token)
    }

    @Test
    fun `id 形状不对时明确报错`() {
        for (bad in listOf("1234567", "|abc", "abc|abc", "1234567|")) {
            var failed = false
            try { EhDetailMapping.parseComicId(bad) } catch (e: SourceError) { failed = true }
            assertTrue(failed, "应拒绝：$bad")
        }
    }

    @Test
    fun `详情映射为 Comic 与单个合成章节`() {
        val cd = EhDetailMapping.toComicDetail("1234567|abc", detail, fallbackTitle = "兜底")
        assertEquals("eh", cd.comic.sourceId)
        assertEquals("1234567|abc", cd.comic.id)
        assertEquals("作品标题", cd.comic.title)
        assertEquals("https://ehgt.org/c.jpg", cd.comic.coverUrl)
        assertEquals("alice", cd.comic.author)
        assertEquals(listOf("artist/bob"), cd.comic.tags)
        assertEquals(1, cd.chapters.size)
        assertEquals("1234567|abc", cd.chapters[0].id)
        assertEquals(0, cd.chapters[0].order)
    }

    @Test
    fun `标题缺失时用兜底值`() {
        val cd = EhDetailMapping.toComicDetail("1|t", detail.copy(title = null), fallbackTitle = "兜底")
        assertEquals("兜底", cd.comic.title)
    }
}

/** 追加：详情页解析结果"是否看起来有效"的判定。 */
class EhLooksParsedTest {

    @Test
    fun `全空视为解析失败`() {
        val empty = EhGalleryDetail(null, null, null, null, null, null, null, null, null, emptyList(), null)
        assertEquals(false, empty.looksParsed())
    }

    @Test
    fun `有标题或有页链接都算有效`() {
        val empty = EhGalleryDetail(null, null, null, null, null, null, null, null, null, emptyList(), null)
        assertEquals(true, empty.copy(title = "T").looksParsed())
        assertEquals(true, empty.copy(pageTokens = listOf("abc123")).looksParsed())
        assertEquals(true, empty.copy(pages = 3).looksParsed())
    }
}
