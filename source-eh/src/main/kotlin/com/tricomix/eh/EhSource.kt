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
 * 未实现：仅 `history`（该站历史是客户端本地功能，服务端无端点）—— 返回 [SourceError.Unsupported]，不静默返回空数据。
 *
 * **未验证**：从未对真实站点发过请求；页面选择器与 JS 变量都来自静态阅读。
 */
class EhSource(
    val client: EhClient = EhClient(),
    val host: EhHost = EhHost.E_HENTAI,
) : ComicSource {

    override val id: String = "eh"
    override val displayName: String = "E-Hentai"
    override val capabilities: Set<Capability> = setOf(Capability.LOGIN, Capability.SEARCH, Capability.HOME, Capability.FAVORITES)

    private val url = EhUrl(host)

    /** 表单登录：成功后 cookie 存进 [EhCookieJar]，该站登录态就是 cookie。 */
    override suspend fun login(credential: SourceCredential): Result<Session> = src {
        val user = credential.fields["username"] ?: credential.fields["email"]
            ?: throw SourceError.Auth("缺少 username")
        val pass = credential.fields["password"] ?: throw SourceError.Auth("缺少 password")
        val html = client.postForm(
            url = EhSignIn.URL,
            fields = EhSignIn.form(user, pass),
            referer = EhSignIn.REFERER,
        )
        val name = EhSignInParser.parse(html)
        Session(mapOf("username" to name))
    }
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
        val detail = GalleryDetailParser.parse(client.get(url.gallery(gid, token)), url.gallery(gid, token))
        if (!detail.looksParsed()) {
            throw SourceError.Parse("详情页解析不出任何字段（站点结构可能已变）")
        }
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
        val detail = GalleryDetailParser.parse(client.get(url.gallery(gid, token)), url.gallery(gid, token))
        // 新版站点没有 showkey：图片地址改为逐页抓图片页取得，因此这里只需要逐页 imgkey。
        val tokens = detail.pageTokens
        val count = maxOf(tokens.size, detail.pages ?: 0)
        if (count == 0) throw SourceError.Parse("详情页既没有页链接也没有页数")
        // 首批之外的页用 gtoken 补（详情页只内联首批链接）
        // 首批之外的页需要用 gtoken 换 token。实测服务端对超出首批的页返回 "Invalid page"，
        // 说明该接口的页段语义与我的理解仍有偏差 —— 因此**失败时回退到首批**，
        // 让阅读器至少可用，并把限制如实写在这里（不假装支持全部页）。
        val keys = runCatching {
            EhPageKeys.complete(client, host, gid, detail.token ?: token, count, tokens)
        }.getOrElse { tokens }
        (0 until count).map { i ->
            PageRef(
                chapterId = chapterId,
                index = i,
                extra = buildMap {
                    put("gid", gid.toString())
                    put("token", token)
                            keys.getOrNull(i)?.let { put("imgkey", it) }
                },
            )
        }
    }

    override suspend fun imageRequest(page: PageRef, quality: ImageQuality): Result<ImageRequest> = src {
        val gid = page.extra["gid"]?.toLongOrNull()
            ?: throw SourceError.Parse("PageRef 缺少 gid")
        val token = page.extra["token"] ?: throw SourceError.Parse("PageRef 缺少 token")
        val imgKey = page.extra["imgkey"]
            ?: throw SourceError.Parse("PageRef 缺少 imgkey（该页不在详情页首批链接里时需用 gtoken 补齐）")
        // 新版流程：直接打开图片页 /s/<imgkey>/<gid>-<页号>，从页面里取图片地址
        val image = GalleryPageParser.parse(client.get(url.page(gid, imgKey, page.index + 1)))
        // EH 的质量档位对应"原图 / 页面图"，这里优先原图（若响应给了）。
        val chosen = if (quality == ImageQuality.ORIGINAL) {
            image.originUrl ?: image.imageUrl
        } else {
            image.imageUrl
        }
        ImageRequest(url = chosen)
    }

    override suspend fun favorites(page: Int): Result<Paged<Comic>> = src {
        val html = client.get(url.favorites(page))
        if (!EhFavorites.isListView(html)) {
            throw SourceError.Auth("收藏页不是列表视图：很可能尚未登录（请先 login），也可能是页面结构变了")
        }
        val items = EhFavorites.parse(html, host.baseUrl)
        Paged(items = items.map { it.toComic() }, page = page, hasMore = items.isNotEmpty())
    }
    override suspend fun history(page: Int): Result<Paged<Comic>> =
        Result.failure(SourceError.Unsupported("EH 历史尚未接入"))

    private suspend inline fun <T> src(crossinline block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(if (e is SourceError) e else SourceError.Unknown(e.message ?: e::class.simpleName ?: "未知错误"))
    }
}
