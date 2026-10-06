package com.tricomix.eh

import com.tricomix.core.model.Chapter
import com.tricomix.core.model.Comic
import com.tricomix.core.model.ComicDetail
import com.tricomix.core.model.ImageQuality
import com.tricomix.core.model.ImageRequest
import com.tricomix.core.model.PageRef
import com.tricomix.core.model.Paged
import com.tricomix.core.model.Section
import com.tricomix.core.source.Capability
import com.tricomix.core.source.ComicSource
import com.tricomix.core.source.Session
import com.tricomix.core.source.SourceCredential
import com.tricomix.core.source.SourceError

/**
 * E-Hentai 源 —— **尚未实现**。
 *
 * 这里刻意只保留可编译的骨架：在动手前必须先解决两件事（都不是代码问题）：
 * 1. 该站是网页站（需要 cookie 登录、DOM 解析、图片带会过期的 hath 键）；
 * 2. 它对自动化访问有明确限制，长期稳定性与封禁风险需要先接受。
 *
 * 因此本类所有方法一律返回 [SourceError.Unsupported]，不会静默返回空数据 —— 让调用方明确知道"没实现"，
 * 而不是把"没实现"伪装成"没有结果"。
 */
class EhSource : ComicSource {

    override val id: String = "eh"
    override val displayName: String = "E-Hentai"
    override val capabilities: Set<Capability> = emptySet()

    private fun notImplemented(): Result<Nothing> =
        Result.failure(SourceError.Unsupported("EH 源尚未实现（见 README 与 docs/design.md 的前置条件）"))

    override suspend fun login(credential: SourceCredential): Result<Session> = notImplemented()
    override suspend fun logout() = Unit
    override suspend fun home(): Result<List<Section>> = notImplemented()
    override suspend fun search(query: String, page: Int): Result<Paged<Comic>> = notImplemented()
    override suspend fun detail(comicId: String): Result<ComicDetail> = notImplemented()
    override suspend fun chapters(comicId: String): Result<List<Chapter>> = notImplemented()
    override suspend fun pages(chapterId: String): Result<List<PageRef>> = notImplemented()
    override suspend fun imageRequest(page: PageRef, quality: ImageQuality): Result<ImageRequest> = notImplemented()
    override suspend fun favorites(page: Int): Result<Paged<Comic>> = notImplemented()
    override suspend fun history(page: Int): Result<Paged<Comic>> = notImplemented()
}
