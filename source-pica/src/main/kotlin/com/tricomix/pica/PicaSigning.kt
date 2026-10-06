package com.tricomix.pica

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PicACG 请求签名（协议层）。
 *
 * ## 依据（重要：两份存档文档曾冲突，以原生层结论为准）
 *
 * - 存档 `docs/02-api-protocol.md` 曾写"8 段拼接，含 baseUrl/appVersion/buildVersion"；
 * - 存档 `docs/03-native-jni.md` 的原生层分析给出的是 **5 段**：
 *   `lower( path + time + nonce + httpMethod + apiKey )`，并明确注明**不含** baseUrl/appVersion/buildVersion。
 *
 * 原生层是从实际代码得出的，因此这里采用 5 段形式。（我最初按 API 文档实现成 8 段，是错的。）
 *
 * 密钥由调用方注入；若要使用内置常量（方案 A），用 [signWithEmbeddedKey]。
 */
object PicaSigning {

    /** 拼接签名原文（未转小写，便于单测逐段核对）。 */
    fun buildInput(
        path: String,
        time: Long,
        nonce: String,
        httpMethod: String,
        apiKey: String,
    ): String = buildString {
        append(path); append(time); append(nonce); append(httpMethod); append(apiKey)
    }

    /** 用调用方提供的密钥计算签名（小写十六进制，64 字符）。 */
    fun sign(
        signingKey: ByteArray,
        path: String,
        time: Long,
        nonce: String,
        httpMethod: String,
        apiKey: String = PicaCredentials.API_KEY,
    ): String = hmacSha256Hex(signingKey, buildInput(path, time, nonce, httpMethod, apiKey).lowercase())

    /** 方案 A：使用内置密钥与内置 api-key。 */
    fun signWithEmbeddedKey(
        path: String,
        time: Long,
        nonce: String,
        httpMethod: String,
    ): String = sign(PicaCredentials.signingKeyBytes, path, time, nonce, httpMethod)

    private fun hmacSha256Hex(key: ByteArray, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).toHexLower()
    }

    private fun ByteArray.toHexLower(): String {
        val table = "0123456789abcdef"
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(table[v ushr 4]).append(table[v and 0x0F])
        }
        return sb.toString()
    }
}

/**
 * 服务器时间同步：服务端在响应头返回 `Server-Time`，客户端把与本地时间的差值持久化，
 * 后续请求的 `time` 加上该偏移（容忍时钟漂移，避免签名因时间戳偏差失效）。
 */
class PicaTimeSync(initialOffsetSeconds: Long = 0L) {

    @Volatile
    var offsetSeconds: Long = initialOffsetSeconds
        private set

    fun updateFrom(serverTimeSeconds: Long, localTimeSeconds: Long) {
        offsetSeconds = serverTimeSeconds - localTimeSeconds
    }

    fun timestamp(localTimeSeconds: Long): Long = localTimeSeconds + offsetSeconds
}
