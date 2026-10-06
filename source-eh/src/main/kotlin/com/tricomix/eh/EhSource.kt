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
 * 已实现：`home` / `search`（列表解析）、`detail` / `chapters`（详情页解析）、
 * `pages`（详情页里的逐页 `imgkey` + `showkey`）、`imageRequest`（`showpage` 换图片地址，含 `hath`）。
 * 未实现：`login`、`favorites`、`history` —— 一律返回 [SourceError.Unsupported]，不静默返回空数据。
 *
 * **未验证**：从未对真实站点发过请求；页面选择器与 JS 变量都来自静态阅读。
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
        val items = GalleryListParser.parse(client.get(url.home()), host.baseUrl)
        listOf(Section(title = "首页", items = items.map { it.toComic() }))
    }

    override suspend fun search(query: String, page: Int): Result<Paged<Comic>> = src {
        val items = GalleryListParser.parse(client.get(url.search(query, page)), host.baseUrl)
        Paged(items = items.map { it.toComic() }, page = page, hasMore = items.isNotEmpty())
    }

    override suspend fun detail(comicId: String): Result<ComicDetail> = src {
        val (gid, token) = EhDetailMapping.parseComicId(comicId)
        val detail = GalleryDetailParser.parse(client.get(url.gallery(gid, token)), host.baseUrl)
        EhDetailMapping.toComicDetail(comicId, detail, fallbackTitle = null)
    }

    /** EH 没有章节：整个作品就是一个"章节"（见 [EhDetailMapping] 的说明）。 */
    override suspend fun chapters(comicId: String): Result<List<Chapter>> =
        detail(comicId).map { it.chapters }

    /**
     * 页列表。EH 的图片地址是**逐页换**来的：先用详情页里的 `imgkey`（每页链接）
     * 与 `showkey`（JS 变量），再由 [imageRequest] 调 `showpage` 换真实地址。
     *
     * 已知限制：详情页只带首批页链接；超出部分需要用 `gtoken` 分批取（本实现暂未做）。
     */
    override suspend fun pages(chapterId: String): Result<List<PageRef>> = src {
        val (gid, token) = EhDetailMapping.parseComicId(chapterId)
        val detail = GalleryDetailParser.parse(client.get(url.gallery(gid, token)), host.baseUrl)
        val showKey = detail.showKey
            ?: throw SourceError.Parse("详情页没有 showkey（页面结构可能已变）")
        val tokens = detail.pageTokens
        val count = maxOf(tokens.size, detail.pages ?: 0)
        if (count == 0) throw SourceError.Parse("详情页既没有页链接也没有页数")
        (0 until count).map { i ->
            PageRef(
                chapterId = chapterId,
                index = i,
                extra = buildMap {
                    put("gid", gid.toString())
                    put("token", token)
                    put("showkey", showKey)
                    tokens.getOrNull(i)?.let { put("imgkey", it) }
                },
            )
        }
    }

    override suspend fun imageRequest(page: PageRef, quality: ImageQuality): Result<ImageRequest> = src {
        val gid = page.extra["gid"]?.toLongOrNull()
            ?: throw SourceError.Parse("PageRef 缺少 gid")
        val token = page.extra["token"] ?: throw SourceError.Parse("PageRef 缺少 token")
        val showKey = page.extra["showkey"] ?: throw SourceError.Parse("PageRef 缺少 showkey")
        val imgKey = page.extra["imgkey"]
            ?: throw SourceError.Parse("该页不在详情页的首批链接里，暂不支持（需要 gtoken 分批取）")
        val body = EhApi.showPage(gid, page.index, imgKey, showKey)
        val response = client.postJson(
            url = EhApi.endpoint(host),
            json = body,
            referer = url.gallery(gid, token),
        )
        val image = GalleryPageApiParser.parse(response)
        // EH 的质量档位对应"原图 / 页面图"，这里优先原图（若响应给了）。
        val chosen = if (quality == ImageQuality.ORIGINAL) {
            image.originUrl ?: image.imageUrl
        } else {
            image.imageUrl
        }
        ImageRequest(url = chosen)
    }

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
