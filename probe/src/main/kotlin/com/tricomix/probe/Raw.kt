package com.tricomix.probe

import com.tricomix.pica.PicaClient
import com.tricomix.pica.PicaJson
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * 开发期原始响应转储：`gradle :probe:rawDump -PrawArgs="<comicId>"`。
 *
 * 为什么单独一个入口：核对真实键名时不想污染产品路径的 `Main`，也不想在探针里塞调试分支。
 * 凭据一律从环境变量 `TRICOMIX_USER` / `TRICOMIX_PASS` 读取，不落盘、不回显。
 */
fun main(args: Array<String>) {
    val comicId = args.firstOrNull() ?: run {
        println("用法：-PrawArgs=\"<comicId>\"（凭据走 TRICOMIX_USER / TRICOMIX_PASS）")
        exitProcess(2)
    }
    runBlocking {
        val email = System.getenv("TRICOMIX_USER").orEmpty()
        val pass = System.getenv("TRICOMIX_PASS").orEmpty()
        val client = PicaClient()
        if (email.isNotEmpty()) {
            val ok = runCatching { client.signIn(email, pass) }.isSuccess
            println("登录：${if (ok) "成功" else "失败"}")
        }

        suspend fun show(label: String, call: suspend () -> String) {
            println("=== $label ===")
            val text = runCatching { call() }.getOrElse { "调用失败：${it.message}" }
            println(text.take(600))
            println()
        }

        show("GET comics/$comicId（详情：看顶层键名）") { client.comic(comicId).toString() }
        show("GET comics/$comicId/eps（章节：看 data 下的键名）") { client.episodes(comicId).toString() }

        runCatching {
            val root = client.episodes(comicId)
            println("=== data 下的键名 ===")
            println(root.toString().let { if (it.length > 300) it.take(300) + "…" else it })
            println("  data.eps 存在=${PicaJson.pageOf(root, "eps") != null}")
        }
    }
}
