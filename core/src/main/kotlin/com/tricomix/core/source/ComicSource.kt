package com.tricomix.core.source

import com.tricomix.core.model.Chapter
import com.tricomix.core.model.Comic
import com.tricomix.core.model.ComicDetail
import com.tricomix.core.model.ImageQuality
import com.tricomix.core.model.ImageRequest
import com.tricomix.core.model.PageRef
import com.tricomix.core.model.Paged
import com.tricomix.core.model.Section

/** 源支持的能力。界面按能力决定显示哪些入口，而不是按源名做特判。 */
enum class Capability {
    LOGIN, SEARCH, FAVORITES, HISTORY, HOME, DOWNLOAD,
}

/** 登录凭据。具体字段由各源解释（账号 / cookie 等）。 */
data class SourceCredential(val fields: Map<String, String>)

/** 一次会话（例如 token 或 cookie 集合）。 */
data class Session(val fields: Map<String, String>)

/**
 * 源错误。
 *
 * 约定：源的实现**不向界面层抛异常**，一律返回 [Result.failure] 且异常类型为本类型。
 */
sealed class SourceError(message: String) : Exception(message) {
    class Network(message: String) : SourceError(message)
    class Auth(message: String) : SourceError(message)
    class NotFound(message: String) : SourceError(message)
    class Parse(message: String) : SourceError(message)
    class Unsupported(message: String) : SourceError(message)
    class Unknown(message: String) : SourceError(message)
}

/**
 * 内容源接口。
 *
 * 实现约定：
 * - 全部为 `suspend`，IO 自行切到合适的调度器；
 * - 失败以 [Result.failure] + [SourceError] 返回；
 * - 不缓存"界面状态"，只负责协议。
 */
interface ComicSource {
    val id: String
    val displayName: String
    val capabilities: Set<Capability>

    suspend fun login(credential: SourceCredential): Result<Session>
    suspend fun logout()

    suspend fun home(): Result<List<Section>>
    suspend fun search(query: String, page: Int): Result<Paged<Comic>>
    suspend fun detail(comicId: String): Result<ComicDetail>
    suspend fun chapters(comicId: String): Result<List<Chapter>>
    suspend fun pages(chapterId: String): Result<List<PageRef>>
    suspend fun imageRequest(page: PageRef, quality: ImageQuality): Result<ImageRequest>

    suspend fun favorites(page: Int): Result<Paged<Comic>>
    suspend fun history(page: Int): Result<Paged<Comic>>
}
