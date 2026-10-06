package com.tricomix.pica

/**
 * PicACG 的请求头构造（**纯函数，便于单测**）。
 *
 * 依据：本项目存档的协议文档 + 对公开参照实现的**结构阅读**（`PicaHeader`），
 * 两边一致：`api-key`、`accept`、`app-platform`、`app-version`、`app-build-version`、
 * `app-uuid`、`app-channel`、`time`、`nonce`、`signature`，登录后另加 `authorization`。
 * 参照实现还有一个 `app-nonce`，本实现**先不发**（未确证其参与签名），需要在真机验证时再补。
 *
 * **未验证**：从未对真实服务发过请求；头名来自静态阅读。
 */
object PicaHeaders {

    /** 客户端自称的版本/渠道，服务端会校验（值来自参照实现，属互操作性所必需）。 */
    const val APP_VERSION = "2.2.1.3.3.4"
    const val APP_BUILD_VERSION = "44"
    const val APP_PLATFORM = "android"
    const val APP_CHANNEL = "1"
    const val ACCEPT = "application/vnd.picacomic.com.v1+json"

    /**
     * 构造一次请求的全部头。
     *
     * @param path 端点路径（如 `comics/123`，**不含** baseUrl 与查询串 —— 签名只吃 path）
     * @param method HTTP 方法（大写，参与签名）
     * @param time 已按服务端时间校正过的时间戳（见 [PicaTimeSync]）
     * @param nonce 随机串（参与签名，且必须与请求里发的一致）
     * @param appUuid 设备标识（参照实现里由 UUID 生成）
     * @param token 登录后拿到的令牌；为空则不带 `authorization` 头
     * @param imageQuality 图片质量档位
     * @param signingKey 默认用内置密钥（方案 A），测试可注入
     */
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
            put("image-quality", imageQuality)
            put("time", time.toString())
            put("nonce", nonce)
            put("signature", signature)
            if (!token.isNullOrEmpty()) put("authorization", token)
        }
    }
}

/** 图片质量档位（服务端接受的字面量）。 */
enum class PicaImageQuality(val value: String) {
    /** 原图。 */
    ORIGINAL("original"),
    /** 普通（默认）。 */
    NORMAL("normal"),
    /** 低流量。 */
    LOW("low"),
}
