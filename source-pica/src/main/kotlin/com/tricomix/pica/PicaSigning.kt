package com.tricomix.pica

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PicACG 请求签名（协议层）。
 *
 * 依据：本项目私有研究存档 `02-api-protocol.md` 的静态分析结论（互操作性研究）。
 * **注意**：签名密钥来自对方客户端的原生库，本仓库**不包含任何密钥**；
 * 密钥由调用方在运行时注入（[PicaSigning] 只接受字节数组）。
 *
 * 签名原文按顺序拼接：[baseUrl, path, time, nonce, httpMethod, apiKey, appVersion, buildVersion]，
 * 整体转小写后做 HMAC-SHA256，输出小写十六进制。
 */
object PicaSigning {

    const val BASE_URL = "https://picaapi.picacomic.com/"

    /** 拼接签名原文（尚未转小写，便于单测逐段核对）。 */
    fun buildInput(
        baseUrl: String,
        path: String,
        time: Long,
        nonce: String,
        httpMethod: String,
        apiKey: String,
        appVersion: String,
        buildVersion: String,
    ): String = buildString {
        append(baseUrl); append(path); append(time); append(nonce)
        append(httpMethod); append(apiKey); append(appVersion); append(buildVersion)
    }

    /**
     * 计算签名。
     * @param signingKey 由调用方注入的 HMAC 密钥（本仓库不保存任何密钥）
     */
    fun sign(
        signingKey: ByteArray,
        baseUrl: String,
        path: String,
        time: Long,
        nonce: String,
        httpMethod: String,
        apiKey: String,
        appVersion: String,
        buildVersion: String,
    ): String {
        val input = buildInput(baseUrl, path, time, nonce, httpMethod, apiKey, appVersion, buildVersion)
            .lowercase()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingKey, "HmacSHA256"))
        return mac.doFinal(input.toByteArray(Charsets.UTF_8)).toHexLower()
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
 * 服务器时间同步：服务端在响应头返回 `Server-Time`，客户端把它与本地时间的差值持久化，
 * 后续请求的 `time` 字段加上该偏移（容忍客户端时钟漂移，避免签名因时间戳偏差失效）。
 */
class PicaTimeSync(initialOffsetSeconds: Long = 0L) {

    @Volatile
    var offsetSeconds: Long = initialOffsetSeconds
        private set

    /** 用一次响应头更新偏移。 */
    fun updateFrom(serverTimeSeconds: Long, localTimeSeconds: Long) {
        offsetSeconds = serverTimeSeconds - localTimeSeconds
    }

    /** 当前应使用的签名时间戳。 */
    fun timestamp(localTimeSeconds: Long): Long = localTimeSeconds + offsetSeconds
}
