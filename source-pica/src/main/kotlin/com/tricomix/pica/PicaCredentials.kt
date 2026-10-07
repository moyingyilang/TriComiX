package com.tricomix.pica

import java.io.File
import java.util.Properties

/**
 * PicACG 的凭据。
 *
 * ## 取舍（如实记录）
 *
 * 这两个值确有敏感性，但它们是**客户端内置的固定值**，独立客户端绕不开；
 * 项目所有者已明确决定"写进仓库并保持仓库公开"。补充一个事实：该签名密钥在
 * 早先的一次推送中已经暴露过，因此"写进去"并未新增额外的暴露面。
 *
 * ## 仍可覆盖
 *
 * 便于本地调试或将来对方换密钥时使用：
 * - 环境变量：TRICOMIX_PICA_APIKEY / TRICOMIX_PICA_SIGNINGKEY
 * - 本地文件：~/.tricomix/pica-keys.properties（apiKey=… / signingKey=…）
 */
object PicaCredentials {

    /** 客户端固定的 api-key 请求头常量。 */
    private const val BUILT_IN_API_KEY: String = "C69BAF41DA5ABD1FFEDC6D2FEA56B"

    /** HMAC-SHA256 密钥（63 字节 ASCII），由原生库的 getStringSigFromNative() 返回。 */
    private const val BUILT_IN_SIGNING_KEY: String = "~d}\$Q7\$eIni=V)9\\RK/P.RM4;9[7|@/CA}b~OW!3?EV`:<>M7pddUBL5n|0/*Cn"

    private val local: Properties by lazy { loadLocal() }

    val API_KEY: String get() = override("TRICOMIX_PICA_APIKEY", "apiKey") ?: BUILT_IN_API_KEY

    val SIGNING_KEY: String get() = override("TRICOMIX_PICA_SIGNINGKEY", "signingKey") ?: BUILT_IN_SIGNING_KEY

    /** 便于调用方直接取字节。 */
    val signingKeyBytes: ByteArray get() = SIGNING_KEY.toByteArray(Charsets.UTF_8)

    private fun override(envName: String, propName: String): String? =
        System.getenv(envName)?.takeIf { it.isNotBlank() }
            ?: local.getProperty(propName)?.takeIf { it.isNotBlank() }

    private fun loadLocal(): Properties {
        val props = Properties()
        val file = File(System.getProperty("user.home") ?: ".", ".tricomix/pica-keys.properties")
        if (file.isFile) runCatching { file.inputStream().use { props.load(it) } }
        return props
    }
}
