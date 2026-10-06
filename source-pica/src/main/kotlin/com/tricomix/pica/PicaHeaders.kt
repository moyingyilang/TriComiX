package com.tricomix.pica

/**
 * PicACG 的请求头构造（**纯函数，便于单测**）。
 *
 * 对齐依据：完整客户端 `haka_comic` 的 `lib/network/utils.dart`（`defaultHeaders`）。
 * 实测差异逐条修正（每条都曾导致 401/1005）：
 * 1. **nonce 是固定常量**，不是每请求随机（见 [PicaClient]）；
 * 2. **必须发 `User-Agent`**（完整客户端用 `okhttp/3.8.1`）；
 * 3. `app-build-version` 应为 `45`（我原用存档里的 `44`）；
 * 4. **`app-uuid` 用固定值**（`defaultUuid`），不是每客户端随机；
 * 5. `Content-Type` **所有请求都带**（不只是登录）。
 * 另：`api-key` 与 HMAC 密钥已与完整客户端逐字核对一致。
 */
object PicaHeaders {

    const val APP_VERSION = "2.2.1.3.3.4"
    const val APP_BUILD_VERSION = "45"
    const val APP_PLATFORM = "android"
    const val APP_CHANNEL = "1"
    const val ACCEPT = "application/vnd.picacomic.com.v1+json"
    const val USER_AGENT = "okhttp/3.8.1"
    const val JSON_CONTENT_TYPE = "application/json; charset=UTF-8"

    /** 固定客户端标识；完整客户端用的是 `defaultUuid`。 */
    const val APP_UUID = "defaultUuid"

    /** `app-nonce`：另一个参照实现里的硬编码常量（完整客户端不发它，发了也无害）。 */
    const val APP_NONCE = "1b509109-476d-3790-a5f0-0acfeace9a5b"

    fun build(
        path: String,
        method: String,
        time: Long,
        nonce: String,
        appUuid: String = APP_UUID,
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
            put("User-Agent", USER_AGENT)
            put("Content-Type", JSON_CONTENT_TYPE)
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
