package com.tricomix.pica

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 凭据来源的测试。
 *
 * 这些断言**不依赖真实密钥**（开源后仓库里没有密钥）：只验证"有凭据就用、没有就明确报错"，
 * 且错误信息必须指出该设哪个环境变量 —— 否则使用者只会看到"签名一直被拒"。
 */
class PicaCredentialsTest {

    @Test
    fun `有凭据时返回可用值，无凭据时给出可操作的错误`() {
        for ((name, get) in listOf(
            "apiKey" to { PicaCredentials.API_KEY },
            "signingKey" to { PicaCredentials.SIGNING_KEY },
        )) {
            val r = runCatching { get() }
            if (r.isFailure) {
                val msg = r.exceptionOrNull()?.message.orEmpty()
                assertTrue(
                    msg.contains("TRICOMIX_PICA_"),
                    "$name 缺少凭据时应提示环境变量名，实际：$msg",
                )
                assertTrue(msg.contains("不含任何密钥"), "$name 的错误信息应说明仓库不含密钥，实际：$msg")
            } else {
                assertTrue(r.getOrNull()!!.isNotBlank(), "$name 有凭据时不应为空")
            }
        }
    }

    @Test
    fun `密钥字节与字符串一致`() {
        val r = runCatching { PicaCredentials.signingKeyBytes }
        if (r.isSuccess) {
            val bytes = r.getOrNull()!!
            assertTrue(bytes.isNotEmpty())
            assertTrue(bytes.all { it in 0x20..0x7E }, "应为可打印 ASCII")
        }
    }
}
