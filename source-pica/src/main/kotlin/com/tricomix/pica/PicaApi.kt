package com.tricomix.pica

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * PicACG 的端点路径与响应模型。
 *
 * 依据：公开参照实现的 `PicaBookApi` / `PicaUserApi` / `PicaBookResult`（结构阅读，不复制代码）。
 * **未验证**：从未对真实服务发过请求。
 *
 * 需要留意的两处：详情响应在参照实现里读的是 `data.comics`，而其列表响应也是 `data.comics`
 * （但后者是分页对象、前者应是单个对象）—— 两者形状冲突，所以 [PicaJson.detail] 对
 * `comic` 与 `comics` 两种键都做兼容，而不是赌一个。
 */
object PicaApi {

    const val BASE_URL = "https://picaapi.picacomic.com/"

    fun signIn(): String = "auth/sign-in"
    fun comic(id: String): String = "comics/$id"
    fun episodes(id: String, page: Int): String = "comics/$id/eps?page=$page"
    fun pages(id: String, order: Int, page: Int): String = "comics/$id/order/$order/pages?page=$page"
    fun search(query: String, page: Int): String =
        "comics/search?page=$page&q=" + java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
    fun favourites(page: Int): String = "users/favourite?page=$page"
    fun profile(): String = "users/profile"
    fun punchIn(): String = "users/punch-in"
}

/** 统一响应外壳：`{code, message, data}`。 */
@Serializable
data class PicaEnvelope<T>(val data: T, val code: Int = 200, val message: String? = null)

/** 分页负载：`{docs, total, limit, page, pages}`。 */
@Serializable
data class PicaPage<T>(
    val docs: List<T> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val page: Int = 0,
    val pages: Int = 0,
)

/** 图片/封面描述（列表与页图共用）。 */
@Serializable
data class PicaMedia(
    @SerialName("fileServer") val fileServer: String? = null,
    val path: String? = null,
    @SerialName("originalName") val originalName: String? = null,
) {
    /** 拼完整地址；缺任一字段则为 null（不做无根据的拼接）。 */
    fun url(): String? {
        val base = fileServer?.takeIf { it.isNotBlank() } ?: return null
        val p = path?.takeIf { it.isNotBlank() } ?: return null
        return base.trimEnd('/') + "/static/" + p.trimStart('/')
    }
}

/** 作品（列表项与详情共用；详情会多带 description/tags）。 */
@Serializable
data class PicaComic(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val author: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val thumb: PicaMedia? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** 章节（PicACG 叫 episode/eps）。 */
@Serializable
data class PicaEpisode(
    @SerialName("_id") val id: String = "",
    val title: String = "",
    val order: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
)

/** 页图：`docs[]` 里每项带 `_id` 与 `media`。 */
@Serializable
data class PicaPageDoc(
    @SerialName("_id") val id: String = "",
    val media: PicaMedia? = null,
)

/** 列表外壳：`data.comics` / `data.eps` / `data.pages` 都是分页对象。 */
@Serializable
data class PicaListData(
    val comics: PicaPage<PicaComic>? = null,
    val eps: PicaPage<PicaEpisode>? = null,
    val pages: PicaPage<PicaPageDoc>? = null,
)

/** 解析工具：把「键名可能有两种」的细节集中在一处，便于测试与将来修正。 */
object PicaJson {

    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

    /**
     * 从详情响应里取作品。参照实现的列表与详情都读 `comics`，但详情应当是单个对象，
     * 因此这里两种形态都接受：优先 `comic`（单个），其次 `comics`（且必须是对象、不是分页数组）。
     */
    fun detail(root: JsonElement): PicaComic {
        val data = root.jsonObject["data"]?.jsonObject
            ?: throw IllegalStateException("响应缺少 data")
        val single = data["comic"] ?: data["comics"]
        val obj = single as? JsonObject
            ?: throw IllegalStateException("详情响应的 data.comic(s) 不是对象")
        val comic = json.decodeFromJsonElement(PicaComic.serializer(), obj)
        // 关键：不做"宽容到接受垃圾形状"。分页形态 {"docs":[]} 在全默认值下也能解码成功，
        // 那会静默返回一个空作品；所以这里要求必须真的带 _id。
        if (comic.id.isBlank()) throw IllegalStateException("详情响应不像作品（缺 _id）")
        return comic
    }

    /** 取分页对象里某个键（不存在则为 null）。 */
    fun pageOf(root: JsonElement, key: String): JsonObject? =
        root.jsonObject["data"]?.jsonObject?.get(key) as? JsonObject

    /** 外壳里的状态码；**缺失时返回 null**（不默认成成功）。 */
    fun code(root: JsonElement): Int? =
        runCatching { root.jsonObject["code"]?.jsonPrimitive?.content?.toInt() }.getOrNull()
}
