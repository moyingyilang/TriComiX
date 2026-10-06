package com.tricomix.eh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * E-Hentai 的 HTTP 客户端：带 cookie（登录态）、可配置 User-Agent。
 *
 * **未验证**：从未对真实站点发过请求。站点对自动化访问有限制，真实行为必须由使用者在
 * 自己的网络环境下验证 —— 这也是本项目里 EH 的最后一段路。
 *
 * User-Agent 默认给一个常见的桌面 Chrome 字样；**建议上层从配置覆盖**，
 * 因为我们没有对真实站点验证过哪个 UA 能被接受（这一点不猜）。
 */
class EhClient(
    val cookieJar: EhCookieJar = EhCookieJar(),
    private val userAgent: String = DEFAULT_UA,
    http: OkHttpClient? = null,
) {

    val http: OkHttpClient = http ?: OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun get(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).header("User-Agent", userAgent)
        if (referer != null) builder.header("Referer", referer)
        http.newCall(builder.get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}：$url")
            response.body?.string().orEmpty()
        }
    }

    /** `/api.php` 需要 JSON 请求体（不是表单）。 */
    suspend fun postJson(url: String, json: String, referer: String? = null): String =
        withContext(Dispatchers.IO) {
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val builder = Request.Builder().url(url).header("User-Agent", userAgent).post(body)
            if (referer != null) builder.header("Referer", referer)
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}：$url")
                response.body?.string().orEmpty()
            }
        }

    suspend fun postForm(url: String, fields: Map<String, String>, referer: String? = null): String =
        withContext(Dispatchers.IO) {
            val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
            val builder = Request.Builder().url(url).header("User-Agent", userAgent).post(form)
            if (referer != null) builder.header("Referer", referer)
            http.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}：$url")
                response.body?.string().orEmpty()
            }
        }

    companion object {
        /** 可被上层覆盖；不声称它一定被站点接受。 */
        const val DEFAULT_UA: String =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
