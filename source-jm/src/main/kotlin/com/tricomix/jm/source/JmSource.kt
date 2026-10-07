package com.tricomix.jm.source

import com.tricomix.core.model.Chapter
import com.tricomix.core.model.Comic
import com.tricomix.core.model.ComicDetail
import com.tricomix.core.model.ImageQuality
import com.tricomix.core.model.ImageRequest
import com.tricomix.core.model.PageRef
import com.tricomix.core.model.Paged
import com.tricomix.core.model.Section
import com.tricomix.core.model.UnscrambleSpec
import com.tricomix.core.source.Capability
import com.tricomix.core.source.ComicSource
import com.tricomix.core.source.Session
import com.tricomix.core.source.SourceCredential
import com.tricomix.core.source.SourceError
import com.tricomix.jm.data.JmRepository
import com.tricomix.jm.data.remote.dto.ListItem

/**
 * 把现有的 JM 实现（[JmRepository]）**适配**成统一的 [ComicSource]。
 *
 * 设计取舍：
 * - 这是**适配**而不是重写：`JmRepository` 的行为一律不动，只在外面套一层映射；
 * - 映射用的字段名都来自实际 DTO（不是猜的）：`PagedList.items`、`ListItem.id/name/author/image`、
 *   `AlbumDetail.series`（章节）、`ReadPayload.images`（页）、`ReadPayload.id`（反切片算法要的 aid）；
 * - `favorites` / `history` 的 DTO 不在当前已见的文件里，**第一版不实现**，并且 [capabilities]
 *   如实声明为不支持 —— 不假装支持、也不静默返回空数据。
 */
class JmSource(private val repo: JmRepository) : ComicSource {

    override val id: String = "jm"
    override val displayName: String = "JMComic"
    override val capabilities: Set<Capability> =
        setOf(Capability.LOGIN, Capability.SEARCH, Capability.HOME, Capability.FAVORITE_WRITE)

    override suspend fun login(credential: SourceCredential): Result<Session> = src {
        val u = credential.fields["username"] ?: throw SourceError.Auth("缺少 username")
        val p = credential.fields["password"] ?: throw SourceError.Auth("缺少 password")
        repo.login(u, p)
        // JM 的会话由仓库内部的 AuthStore 维护，这里不需要额外持有状态
        Session(emptyMap())
    }

    override suspend fun logout() {
        runCatching { repo.logout() }
    }

    override suspend fun home(): Result<List<Section>> = src {
        val latest = repo.latest(1)
        listOf(Section(title = "最新", items = latest.items.map { it.toComic(repo) }))
    }

    override suspend fun search(query: String, page: Int): Result<Paged<Comic>> = src {
        val result = repo.search(query = query, page = page)
        val pl = result.page
        Paged(items = pl.items.map { it.toComic(repo) }, page = page, hasMore = pl.items.isNotEmpty())
    }

    override suspend fun detail(comicId: String): Result<ComicDetail> = src {
        val a = repo.album(comicId)
        ComicDetail(
            comic = Comic(
                sourceId = id,
                id = a.id,
                title = a.name.orEmpty(),
                coverUrl = repo.coverUrl(a.id),
                author = a.author.joinToString(", ").ifEmpty { null },
                tags = a.tags,
                description = a.description,
            ),
            chapters = a.series.mapIndexed { i, s ->
                Chapter(id = s.id, title = s.name ?: "第 ${i + 1} 话", order = i)
            },
        )
    }

    override suspend fun chapters(comicId: String): Result<List<Chapter>> =
        detail(comicId).map { it.chapters }

    override suspend fun pages(chapterId: String): Result<List<PageRef>> = src {
        val payload = repo.read(chapterId)
        // 反切片需要 aid(= ReadPayload.id) 与 scrambleId，这里随 PageRef 一起带出去，
        // 这样界面层不必知道 JM 的算法细节（见 core 的 UnscrambleSpec）。
        payload.images.mapIndexed { i, img ->
            PageRef(
                chapterId = chapterId,
                index = i,
                extra = mapOf(
                    "url" to img.image,
                    "aid" to payload.id.toString(),
                    "scrambleId" to payload.scrambleId.toString(),
                ),
            )
        }
    }

    override suspend fun imageRequest(page: PageRef, quality: ImageQuality): Result<ImageRequest> = src {
        val url = page.extra["url"] ?: throw SourceError.Parse("PageRef 缺少 url（是否用了别的源产生的 PageRef？）")
        val aid = page.extra["aid"]?.toIntOrNull()
        val scrambleId = page.extra["scrambleId"]?.toIntOrNull()
        val unscramble = if (aid != null && scrambleId != null && repo.needsUnscramble(url, aid, scrambleId)) {
            // JMNeXt 传的是 image.fileNameStem（文件名去扩展名，如 "00001"）：
                // 每一页的切片方式由 md5(aid + 该字符串) 决定，用 aid 当 seed 会还原失败。
                UnscrambleSpec(seed = url.substringAfterLast('/').substringBeforeLast('.'), index = page.index)
        } else {
            null
        }
        ImageRequest(url = url, unscramble = unscramble)
    }

    override suspend fun favorites(page: Int): Result<Paged<Comic>> =
        Result.failure(SourceError.Unsupported("JM 源的收藏列表尚未接入（写入已支持，见 toggleFavorite）"))

    /**
     * 切换收藏。底层 [com.tricomix.jm.data.JmRepository.toggleFavorite] 本身就是"切换"语义，
     * 这里按 [com.tricomix.jm.data.remote.dto.ActionResult.isOk] 判断是否真的成功 ——
     * 业务失败（封套 200 但 `status != "ok"`）必须报错，不能当成成功。
     */
    override suspend fun toggleFavorite(comicId: String): Result<Unit> = src {
        val r = repo.toggleFavorite(comicId)
        if (!r.isOk) throw SourceError.Unknown(r.msg ?: "JM 收藏操作未成功（status=${r.status}）")
    }

    override suspend fun history(page: Int): Result<Paged<Comic>> =
        Result.failure(SourceError.Unsupported("JM 源的历史尚未接入（见 JmSource 注释）"))

    /**
     * 主机发现是否已完成。
     *
     * JM 的实现要求先做 `bootstrap()`（API 主机发现），否则任何请求都会以
     * "API 主机尚未初始化：请先执行 JmHostDiscovery" 失败。主项目里这是由应用层做的，
     * 迁移过来时漏了 —— 现改为**首次用到时自动完成**，调用方不必知道这件事。
     */
    
    private var hostsReady: Boolean = false

    private suspend fun ensureReady() {
        if (hostsReady) return
        if (repo.bootstrap()) hostsReady = true
    }

    /** 统一的错误包装：源实现不向界面层抛异常；并在首次使用时完成主机发现。 */
    private suspend inline fun <T> src(crossinline block: suspend () -> T): Result<T> = try {
        ensureReady()
        Result.success(block())
    } catch (e: Throwable) {
        Result.failure(if (e is SourceError) e else SourceError.Unknown(e.message ?: e::class.simpleName ?: "未知错误"))
    }
}

private fun ListItem.toComic(repo: JmRepository): Comic = Comic(
    sourceId = "jm",
    id = id,
    title = name.orEmpty(),
    coverUrl = repo.coverUrl(this),
    author = author,
    description = description,
)

