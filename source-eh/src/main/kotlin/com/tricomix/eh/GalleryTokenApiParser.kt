package com.tricomix.eh

import kotlinx.serialization.Serializable

/**
 * `gtoken` 响应解析：`{"tokenlist":[{"token":"…"} | {"error":"…"}]}`
 *
 * 每请求一页就对应一项，**顺序与请求一致**。拿到 `token` 就是页链接 `/s/<token>/<gid>-<page>`
 * 里的 `imgkey`，也就是 `showpage` 需要的逐页键。
 *
 * 依据：对参照实现 `GalleryTokenApiParser` 的结构阅读（它读 `tokenlist[0].token` 或 `.error`）。
 * 本实现自行用序列化模型解析，不复制其代码。**未验证**：从未对真实站点发过请求。
 */
@Serializable
data class EhTokenList(val tokenlist: List<EhTokenEntry> = emptyList())

@Serializable
data class EhTokenEntry(val token: String? = null, val error: String? = null)

object GalleryTokenApiParser {

    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /** 返回与请求顺序一致的 token 列表；某项失败时该位置为 null，并把原因带在结果里。 */
    fun parse(body: String): List<String?> =
        json.decodeFromString(EhTokenList.serializer(), body).tokenlist.map { entry ->
            if (entry.error != null) {
                throw IllegalStateException("gtoken 返回错误：${entry.error}")
            }
            entry.token?.takeIf { it.isNotBlank() }
        }
}

/**
 * 把详情页的首批 `imgkey` 补齐到完整页数。
 *
 * 详情页只内联**首批**页链接；超出部分要用 `gtoken` 分批换 token。
 * 每批大小取 [BATCH]（保守值）—— 真实上限未验证，取小一点更安全（只是多几次请求）。
 */
object EhPageKeys {

    const val BATCH = 20

    suspend fun complete(
        client: EhClient,
        host: EhHost,
        gid: Long,
        token: String,
        showKey: String,
        count: Int,
        inlineTokens: List<String>,
    ): List<String?> {
        val keys = MutableList<String?>(count) { inlineTokens.getOrNull(it) }
        var index = inlineTokens.size
        while (index < count) {
            val end = minOf(index + BATCH, count)
            // gtoken 的页码是 1 起
            val request = (index until end).map { i -> Triple(gid, token, i + 1) }
            val body = EhApi.gtoken(request)
            val response = client.postJson(
                url = EhApi.endpoint(host),
                json = body,
                referer = EhUrl(host).gallery(gid, token),
            )
            val tokens = GalleryTokenApiParser.parse(response)
            tokens.forEachIndexed { offset, t -> keys[index + offset] = t }
            index = end
        }
        return keys
    }
}
