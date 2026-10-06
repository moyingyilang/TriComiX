package com.tricomix.pica

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * PicACG 的 HTTP 客户端：统一加签名头、按 `Server-Time` 校正时间、登录后携带令牌。
 *
 * **未验证**：从未对真实服务发过请求。所有端点与头名来自静态阅读（见 [PicaApi] / [PicaHeaders]）。
 *
 * 设计与 `source-jm` 一致：网络细节收在这里，上层 [PicaSource] 只做映射。
 */
class PicaClient(
    private val http: OkHttpClient = defaultHttp(),
    private val timeSync: PicaTimeSync = PicaTimeSync(),
    private val appUuid: String = UUID.randomUUID().toString(),
    private val signingKey: ByteArray = PicaCredentials.signingKeyBytes,
) {

    /** 清空令牌（登出）。 */
    fun signOut() { token = null }

    /**
     * 当前图片档位。协议里档位是通过 `image-quality` **请求头**下发的，
     * 因此它是客户端级设置（不是每个 URL 的参数）。
     */
    var imageQuality: PicaImageQuality = PicaImageQuality.NORMAL

    /** 登录后由 [signIn] 写入；后续请求带上 `authorization`。 */
    @Volatile
    var token: String? = null

    /** `/auth/sign-in`：成功后保存 token。 */
    suspend fun signIn(email: String, password: String): JsonElement {
        val body = JsonObject(
            mapOf(
                "email" to JsonPrimitive(email),
                "password" to JsonPrimitive(password),
            )
        ).toString().toRequestBody("application/json".toMediaType())
        val root = execute(PicaApi.signIn(), "POST", body)
        val token = extractToken(root)
            ?: throw IllegalStateException("登录响应里没有 token（响应形状可能已变），不当作登录成功")
        this.token = token
        return root
    }

    suspend fun comic(id: String): JsonElement = get(PicaApi.comic(id))
    suspend fun episodes(id: String, page: Int = 1): JsonElement = get(PicaApi.episodes(id, page))
    suspend fun pages(id: String, order: Int, page: Int = 1): JsonElement =
        get(PicaApi.pages(id, order, page))
    suspend fun search(query: String, page: Int = 1): JsonElement = get(PicaApi.search(query, page))
    suspend fun favourites(page: Int = 1): JsonElement = get(PicaApi.favourites(page))

    /** 把响应体解析成指定的数据对象（去掉 `data.*` 外壳由调用方负责）。 */
    fun <T> decode(root: JsonElement, serializer: KSerializer<T>): T =
        PicaJson.json.decodeFromJsonElement(serializer, root)

    private suspend fun get(path: String): JsonElement = execute(path, "GET", null)

    private suspend fun execute(
        path: String,
        method: String,
        body: okhttp3.RequestBody?,
    ): JsonElement = withContext(Dispatchers.IO) {
        val local = Instant.now().epochSecond
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val headers = PicaHeaders.build(
            path = path,
            method = method,
            time = timeSync.timestamp(local),
            nonce = nonce,
            appUuid = appUuid,
            token = token,
            imageQuality = imageQuality.value,
            signingKey = signingKey,
        )
        val builder = Request.Builder().url(PicaApi.BASE_URL + path)
        headers.forEach { (k, v) -> builder.header(k, v) }
        when (method) {
            "GET" -> builder.get()
            else -> builder.method(method, body ?: ByteArray(0).toRequestBody(null))
        }
        http.newCall(builder.build()).execute().use { response ->
            val serverTime = response.header("Server-Time")?.trim()?.toLongOrNull()
            if (serverTime != null) timeSync.updateFrom(serverTime, local)
            val text = response.body?.string().orEmpty()
            if (text.isEmpty()) throw IllegalStateException("空响应（HTTP ${response.code}）")
            val root = PicaJson.json.parseToJsonElement(text)
            val code = PicaJson.code(root)
            if (code != 200) {
                val message = root.jsonObject["message"]?.toString() ?: "未知错误"
                throw IllegalStateException("服务端返回 code=$code message=$message")
            }
            root
        }
    }

    private fun extractToken(root: JsonElement): String? = runCatching {
        val data = root.jsonObject["data"]?.jsonObject ?: return@runCatching null
        (data["token"] as? JsonPrimitive)?.content
    }.getOrNull()

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
