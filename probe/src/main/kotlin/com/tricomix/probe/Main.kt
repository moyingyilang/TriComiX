package com.tricomix.probe

import com.tricomix.core.model.Comic
import com.tricomix.core.source.ComicSource
import com.tricomix.eh.EhClient
import com.tricomix.eh.EhHost
import com.tricomix.eh.EhSource
import com.tricomix.eh.EhUrl
import com.tricomix.pica.PicaApi
import com.tricomix.pica.PicaSource
import kotlinx.coroutines.runBlocking

/**
 * 源测试探针（命令行）。
 *
 * 目的：在没有界面的情况下把 [ComicSource] 整条链路跑通并打印真实结果，
 * 让"统一接口能不能用"立刻可验证，而不必等桌面壳。
 *
 * 用法：
 * ```
 * gradle :probe:run --args="--source eh --dry"
 * gradle :probe:run --args="--source eh --query test --page 1"
 * ```
 *
 * EH 与 Pica 的真实请求会访问对方服务：**是否访问、用什么网络环境，由使用者决定**。
 * 本项目不对站点做自动化访问，所以默认 `--dry` 只打印将要访问的地址。
 */
fun main(args: Array<String>) = runBlocking {
    val opts = parse(args)
    val sourceName = (opts["source"] ?: "eh").lowercase()
    val dry = opts.containsKey("dry")
    val query = opts["query"]
    val page = opts["page"]?.toIntOrNull() ?: 1

    println("=== TriComiX 探针 ===")
    println("源=$sourceName  页=$page  查询=${query ?: "(无，走首页)"}  dryRun=$dry")

    when (sourceName) {
        "eh" -> {
            val host = EhHost.E_HENTAI
            val url = EhUrl(host)
            if (dry) {
                println("将要访问：")
                println("  首页 ${url.home()}")
                if (!query.isNullOrBlank()) println("  搜索 ${url.search(query, page)}")
                println("（dryRun：未发出任何请求）")
                return@runBlocking
            }
            run(source = EhSource(EhClient(), host), query = query, page = page, label = "EH")
        }

        "pica" -> {
            if (dry) {
                println("将要访问：")
                println("  搜索 https://picaapi.picacomic.com/${PicaApi.search(query ?: "", page)}")
                println("  提醒：Pica 多数端点需要登录令牌，未登录时服务端会拒绝。")
                return@runBlocking
            }
            run(source = PicaSource(), query = query, page = page, label = "Pica")
        }

        "jm" -> println(
            "JM 源需要 JMNeXt 的本地存储与鉴权组件（KeyValueStore / SecretKeyProvider / AuthStore），" +
                "探针未装配这套依赖。要测 JM 源请直接跑 JMNeXt 桌面端（同一份实现）。"
        )

        else -> println("未知源：$sourceName（可选 eh / pica / jm）")
    }
}

private suspend fun run(source: ComicSource, query: String?, page: Int, label: String) {
    println("能力: ${source.capabilities}")
    if (query.isNullOrBlank()) {
        source.home().fold(
            onSuccess = { sections -> printItems(sections.flatMap { it.items }, label, null) },
            onFailure = { printFailure(label, it) },
        )
    } else {
        source.search(query, page).fold(
            onSuccess = { paged -> printItems(paged.items, label, paged.hasMore) },
            onFailure = { printFailure(label, it) },
        )
    }
}

private fun printItems(items: List<Comic>, label: String, hasMore: Boolean?) {
    println("$label 取到 ${items.size} 条" + (hasMore?.let { if (it) "（还有下一页）" else "（已到末页）" } ?: ""))
    if (items.isEmpty()) {
        println("  （空列表：可能选择器与真实页面不符，或站点返回了验证/登录页）")
    }
    items.take(10).forEach { c ->
        println("  [${c.id}] ${c.title}")
        println("      封面: ${c.coverUrl ?: "(无)"}   作者: ${c.author ?: "(无)"}   标签: ${c.tags.take(3)}")
    }
}

private fun printFailure(label: String, e: Throwable) {
    println("$label 失败：${e::class.simpleName}: ${e.message}")
}

/** `--key value` 与 `--flag` 两种形式。 */
private fun parse(args: Array<String>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (!a.startsWith("--")) { i++; continue }
        val key = a.removePrefix("--")
        val next = args.getOrNull(i + 1)
        if (next != null && !next.startsWith("--")) {
            out[key] = next; i += 2
        } else {
            out[key] = ""; i++
        }
    }
    return out
}
