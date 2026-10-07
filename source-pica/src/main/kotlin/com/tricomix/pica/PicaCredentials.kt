package com.tricomix.pica

import java.io.File
import java.util.Properties

/**
 * PicACG 的凭据 —— **本仓库不包含任何密钥**。
 *
 * 背景：早先按"内置密钥"的方案实现过，但项目开源时必须脱敏，因此改为运行时提供：
 * - 环境变量：`TRICOMIX_PICA_APIKEY` / `TRICOMIX_PICA_SIGNINGKEY`
 * - 或本地文件：`~/.tricomix/pica-keys.properties`（`apiKey=…` / `signingKey=…`，该路径已加入 .gitignore）
 *
 * 缺失时**以明确错误失败**（提示该设哪个变量），而不是静默用一个错值——
 * 后者会表现为"签名一直被拒"，极难排查。
 */
object PicaCredentials {

    private val local: Properties by lazy { loadLocal() }

    /** `api-key` 请求头常量。 */
    val API_KEY: String get() = fromEnvOrFile("TRICOMIX_PICA_APIKEY", "apiKey")

    /** HMAC-SHA256 密钥（63 字节 ASCII）。 */
    val SIGNING_KEY: String get() = fromEnvOrFile("TRICOMIX_PICA_SIGNINGKEY", "signingKey")

    /** 便于调用方直接取字节。 */
    val signingKeyBytes: ByteArray get() = SIGNING_KEY.toByteArray(Charsets.UTF_8)

    private fun fromEnvOrFile(envName: String, propName: String): String =
        System.getenv(envName)?.takeIf { it.isNotBlank() }
            ?: local.getProperty(propName)?.takeIf { it.isNotBlank() }
            ?: error(
                "缺少 PicACG 凭据：请设置环境变量 $envName，" +
                    "或在 ~/.tricomix/pica-keys.properties 中提供 $propName" +
                    "（本仓库不含任何密钥，见 docs/pica-protocol.md）"
            )

    private fun loadLocal(): Properties {
        val props = Properties()
        val file = File(System.getProperty("user.home") ?: ".", ".tricomix/pica-keys.properties")
        if (file.isFile) runCatching { file.inputStream().use { props.load(it) } }
        return props
    }
}
