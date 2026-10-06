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
 * E-Hentai 源。
 *
 * 现状（**如实**）：
 * - 已实现：`home` / `search`（列表页解析），走 [EhClient] + [GalleryListParser]；
 * - 未实现：`login`（表单登录，见 [SignInParser] 计划）、`detail` / `chapters` / `pages` /
 *   `imageRequest`（详情页与图片列表解析）、`favorites` / `history`；
 * - 未实现的一律返回 [SourceError.Unsupported]，**不静默返回空数据**。
 *
 * **未验证**：从未对真实站点发过请求。站点对自动化访问有限制，真实页面结构必须由使用者
 * 在自己的网络环境下验证；选择器一旦与真实页面不符，本源的解析会返回空列表。
 */
class EhSource(
    private val client: EhClient = EhClient(),
    private val host: EhHost = EhHost.E_HENTAI,
) : ComicSource {

    override val id: String = "eh"
    override val displayName: String = "E-Hentai"
    override val capabilities: Set<Capability> = setOf(Capability.SEARCH, Capability.HOME)

    private val url = EhUrl(host)

    override suspend fun login(credential: SourceCredential): Result<Session> =
        Result.failure(SourceError.Unsupported("EH 登录尚未接入（表单登录与 cookie 已在 EhClient/EhCookieJar 备好）"))

    override suspend fun logout() {
        client.cookieJar.clear()
    }

    override suspend fun home(): Result<List<Section>> = src {
        val html = client.get(url.home())
        val items = GalleryListParser.parse(html, host.baseUrl)
        listOf(Section(title = "首页", items = items.map { it.toComic() }))
    }

    override suspend fun search(query: String, page: Int): Result<Paged<Comic>> = src {
        val html = client.get(url.search(query, page))
        val items = GalleryListParser.parse(html, host.baseUrl)
        Paged(items = items.map { it.toComic() }, page = page, hasMore = items.isNotEmpty())
    }

    override suspend fun detail(comicId: String): Result<ComicDetail> =
        Result.failure(SourceError.Unsupported("EH 详情页解析尚未接入"))

    override suspend fun chapters(comicId: String): Result<List<Chapter>> =
        Result.failure(SourceError.Unsupported("EH 的\"章节\"就是作品内的图片分页，见 pages()"))

    override suspend fun pages(chapterId: String): Result<List<PageRef>> =
        Result.failure(SourceError.Unsupported("EH 图片列表解析尚未接入（含 hath）"))

    override suspend fun imageRequest(page: PageRef, quality: ImageQuality): Result<ImageRequest> =
        Result.failure(SourceError.Unsupported("EH 取图尚未接入（含 hath）"))

    override suspend fun favorites(page: Int): Result<Paged<Comic>> =
        Result.failure(SourceError.Unsupported("EH 收藏尚未接入"))

    override suspend fun history(page: Int): Result<Paged<Comic>> =
        Result.failure(SourceError.Unsupported("EH 历史尚未接入"))


    private suspend inline fun <T> src(crossinline block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(if (e is SourceError) e else SourceError.Unknown(e.message ?: e::class.simpleName ?: "未知错误"))
    }
}
