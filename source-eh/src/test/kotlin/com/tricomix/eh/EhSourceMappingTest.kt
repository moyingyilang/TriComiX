package com.tricomix.eh

import com.tricomix.core.source.SourceError
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `EhSource` 的**映射**测试（不联网）：验证列表项到统一模型的转换与 id 编码约定。
 * 网络与页面结构不在单测范围内 —— 那必须由使用者在真实环境验证。
 */
class EhSourceMappingTest {

    @Test
    fun `列表项映射为 Comic，id 编码 gid 与 token`() {
        val item = EhGalleryItem(
            gid = 1234567L,
            token = "abc123def",
            title = "标题",
            thumbUrl = "https://ehgt.org/t/x.jpg",
            uploader = "alice",
            tags = listOf("artist/bob"),
        )
        val comic = item.toComic()
        assertEquals("eh", comic.sourceId)
        assertEquals("1234567|abc123def", comic.id)
        assertEquals("标题", comic.title)
        assertEquals("https://ehgt.org/t/x.jpg", comic.coverUrl)
        assertEquals("alice", comic.author)
        assertEquals(listOf("artist/bob"), comic.tags)
    }

    @Test
    fun `缺缩略图与上传者时为 null，不编造`() {
        val comic = EhGalleryItem(gid = 1L, token = "t", title = "T").toComic()
        assertEquals(null, comic.coverUrl)
        assertEquals(null, comic.author)
    }

    /**
     * 未实现的能力必须**明确报 Unsupported**，而不是静默返回空数据。
     *
     * 注意这是个"移动靶"：一旦某个方法被实现，这里就要换一个仍未实现的方法
     * （已被它绊了四次：detail、pages、login 先后被实现）。目前仍未实现的是 favorites / history。
     * 这里选 favorites：它在第一行就返回，不触发任何网络请求。
     */
    @Test
    fun `未实现的能力返回 Unsupported 而不是空数据`() {
        val source = EhSource(host = EhHost.E_HENTAI)
        val result = runBlocking { source.favorites(1) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SourceError.Unsupported, "实际：${result.exceptionOrNull()}")
    }
}
