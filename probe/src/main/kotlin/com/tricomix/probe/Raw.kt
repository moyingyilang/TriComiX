package com.tricomix.probe

import com.tricomix.pica.PicaClient
import com.tricomix.pica.PicaJson
import kotlinx.coroutines.runBlocking

/**
 * `--raw <comicId>`：把若干端点的**原始响应**打出来，用于核对真实键名。
 *
 * 只用于开发期核对（例如章节为空时看 eps 到底怎么回），不属于产品路径。
 */
object Raw {
    fun dump(comicId: String) = runBlocking {
        val email = System.getenv("TRICOMIX_USER").orEmpty()
        val pass = System.getenv("TRICOMIX_PASS").orEmpty()
        val client = PicaClient()
        if (email.isNotEmpty()) client.signIn(email, pass)
        for ((label, call) in listOf<Pair<String, suspend () -> String>>(
            "comics/{id}" to { client.comic(comicId).toString() },
            "comics/{id}/eps" to { client.episodes(comicId).toString() },
        )) {
            println("=== $label ===")
            val text = runCatching { call() }.getOrElse { "调用失败：${it.message}" }
            println(text.take(700))
            println()
        }
        // 顺带把 eps 解析出的分页对象键名列出
        runCatching {
            val root = client.episodes(comicId)
            val obj = PicaJson.pageOf(root, "eps")
            println("=== data.eps 是否存在: ${obj != null}；其键: ${obj?.keys ?: root.toString().take(200)} ===")
        }
    }
}
