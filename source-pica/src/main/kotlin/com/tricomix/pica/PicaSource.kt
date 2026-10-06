package com.tricomix.pica

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
import kotlinx.serialization.json.JsonElement

/**
 * 把 [PicaClient] 适配成统一的 [ComicSource]。
 *
 * **未验证**：从未对真实服务发过请求；端点为静态阅读所得（见 [PicaApi]）。
 *
 * 刻意不实现的两项，是因为**没有确认的端点**，不凭空造：
 * - `home()`：参照实现里没有"首页/推荐"端点，标为未实现；
 * - `history()`：同上。
 */
class PicaSource(private val client: PicaClient = PicaClient()) : ComicSource {

    override val id: String = "pica"
    override val displayName: String = "PicACG"
    override val capabilities: Set<Capability> =
        setOf(Capability.LOGIN, Capability.SEARCH, Capability.FAVORITES)

    override suspend fun login(credential: SourceCredential): Result<Session> = src {
        val email = credential.fields["email"] ?: credential.fields["username"]
            ?: throw SourceError.Auth("缺少 email/username")
        val password = credential.fields["password"] ?: throw SourceError.Auth("缺少 password")
        client.signIn(email, password)
        Session(mapOf("token" to (client.token ?: "")))
    }

    override suspend fun logout() {
        client.signOut()
    }

    override suspend fun home(): Result<List<Section>> =
        Result.failure(SourceError.Unsupported("PicACG 的首页/推荐端点未确认，见 PicaSource 注释"))

    override suspend fun search(query: String, page: Int): Result<Paged<Comic>> = src {
        val root = client.search(query, page)
        val pageObj = PicaJson.pageOf(root, "comics")
            ?: throw SourceError.Parse("搜索响应缺少 data.comics")
        toPaged(pageObj, page)
    }

    override suspend fun detail(comicId: String): Result<ComicDetail> = src {
        val comic = PicaJson.detail(client.comic(comicId)).toComic()
        ComicDetail(comic = comic, chapters = emptyList())
    }

    /**
     * 章节列表。PicACG 的图片列表端点吃的是**章节序号 order**（不是章节 id），
     * 而接口只给出 chapterId，所以这里把两者编码进 Chapter.id：`"<comicId>|<order>"`，
     * 由 [pages] 解回。这样接口形状不必为某个源破例。
     */
    override suspend fun chapters(comicId: String): Result<List<Chapter>> = src {
        val root = client.episodes(comicId)
        val pageObj = PicaJson.pageOf(root, "eps")
            ?: throw SourceError.Parse("章节响应缺少 data.eps")
        val page = PicaJson.json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(PicaEpisode.serializer()),
            pageObj["docs"] ?: kotlinx.serialization.json.JsonArray(emptyList()),
        )
        page.map { Chapter(id = "$comicId|${it.order}", title = it.title, order = it.order) }
    }

    override suspend fun pages(chapterId: String): Result<List<PageRef>> = src {
        val (comicId, order) = parseChapterId(chapterId)
        val root = client.pages(comicId, order)
        val pageObj = PicaJson.pageOf(root, "pages")
            ?: throw SourceError.Parse("图片列表响应缺少 data.pages")
        val docs = PicaJson.json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(PicaPageDoc.serializer()),
            pageObj["docs"] ?: kotlinx.serialization.json.JsonArray(emptyList()),
        )
        docs.mapIndexed { i, doc ->
            PageRef(
                chapterId = chapterId,
                index = i,
                extra = buildMap {
                    doc.media?.url()?.let { put("url", it) }
                },
            )
        }
    }

    override suspend fun imageRequest(page: PageRef, quality: ImageQuality): Result<ImageRequest> = src {
        val url = page.extra["url"] ?: throw SourceError.Parse("PageRef 缺少 url")
        // 质量档位在 PicACG 是通过 `image-quality` 请求头生效的，属于请求层而非 URL 层；
        // 这里如实不吞掉这个信息，但统一模型目前只承载 URL。
        ImageRequest(url = url)
    }

    override suspend fun favorites(page: Int): Result<Paged<Comic>> = src {
        val root = client.favourites(page)
        val pageObj = PicaJson.pageOf(root, "comics")
            ?: throw SourceError.Parse("收藏响应缺少 data.comics")
        toPaged(pageObj, page)
    }

    override suspend fun history(page: Int): Result<Paged<Comic>> =
        Result.failure(SourceError.Unsupported("PicACG 的历史端点未确认，见 PicaSource 注释"))

    private fun toPaged(pageObj: kotlinx.serialization.json.JsonObject, page: Int): Paged<Comic> {
        val docs = pageObj["docs"] ?: kotlinx.serialization.json.JsonArray(emptyList())
        val comics = PicaJson.json.decodeFromJsonElement(
            kotlinx.serialization.builtins.ListSerializer(PicaComic.serializer()),
            docs,
        )
        val pages = (pageObj["pages"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
        return Paged(items = comics.map { it.toComic() }, page = page, hasMore = page < pages)
    }

    private fun parseChapterId(chapterId: String): Pair<String, Int> {
        val parts = chapterId.split('|')
        if (parts.size != 2) throw SourceError.Parse("章节 id 形状应为 <comicId>|<order>：$chapterId")
        val order = parts[1].toIntOrNull() ?: throw SourceError.Parse("章节顺序不是整数：$chapterId")
        return parts[0] to order
    }

    private suspend inline fun <T> src(crossinline block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(if (e is SourceError) e else SourceError.Unknown(e.message ?: e::class.simpleName ?: "未知错误"))
    }

    private fun PicaComic.toComic(): Comic = Comic(
        sourceId = "pica",
        id = id,
        title = title,
        coverUrl = thumb?.url(),
        author = author,
        tags = tags,
        description = description,
    )

}

