package com.tricomix.pica

/**
 * PicACG 的请求头构造（**纯函数，便于单测**）。
 *
 * 依据：本项目存档的协议文档 + 对公开参照实现的**结构阅读**（`PicaHeader` / `PicaLogin`）。
 *
 * 两处是实测/阅读后修正的，记在这里避免再犯：
 * 1. **`app-nonce` 必须发送** —— 参照实现里是硬编码常量且所有请求都带。
 *    本实现最初刻意漏发（当时未确证其必要性），实测登录直接返回 401 `unauthorized`，补上后重试。
 * 2. **登录必须带 Content-Type** —— 参照实现的原话是"登录不加会报错"，值为
 *    `application/json; charset=UTF-8`（由 [signInBody] 提供）。
 */
object PicaHeaders {

    const val APP_VERSION = "2.2.1.3.3.4"
    const val APP_BUILD_VERSION = "44"
    const val APP_PLATFORM = "android"
    const val APP_CHANNEL = "1"
    const val ACCEPT = "application/vnd.picacomic.com.v1+json"

    /** `app-nonce`：参照实现里的硬编码常量，所有请求都带。 */
    const val APP_NONCE = "1b509109-476d-3790-a5f0-0acfeace9a5b"

    /** 登录请求体的 Content-Type（参照实现注明"不加会报错"）。 */
    const val JSON_CONTENT_TYPE = "application/json; charset=UTF-8"

    fun build(
        path: String,
        method: String,
        time: Long,
        nonce: String,
        appUuid: String,
        token: String? = null,
        imageQuality: String = PicaImageQuality.NORMAL.value,
        signingKey: ByteArray = PicaCredentials.signingKeyBytes,
    ): Map<String, String> {
        val signature = PicaSigning.sign(
            signingKey = signingKey,
            path = path,
            time = time,
            nonce = nonce,
            httpMethod = method,
        )
        return buildMap {
            put("api-key", PicaCredentials.API_KEY)
            put("accept", ACCEPT)
            put("app-platform", APP_PLATFORM)
            put("app-version", APP_VERSION)
            put("app-build-version", APP_BUILD_VERSION)
            put("app-uuid", appUuid)
            put("app-channel", APP_CHANNEL)
            put("app-nonce", APP_NONCE)
            put("image-quality", imageQuality)
            put("time", time.toString())
            put("nonce", nonce)
            put("signature", signature)
            if (!token.isNullOrEmpty()) put("authorization", token)
        }
    }

    /** 登录请求体。字段名经参照实现核对（email + password）。 */
    fun signInBody(email: String, password: String): String =
        "{\"email\":${quote(email)},\"password\":${quote(password)}}"

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
}

/** 图片质量档位（服务端接受的字面量）。 */
enum class PicaImageQuality(val value: String) {
    ORIGINAL("original"),
    NORMAL("normal"),
    LOW("low"),
}
