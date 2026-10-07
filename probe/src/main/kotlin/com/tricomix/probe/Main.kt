package com.tricomix.probe

import com.tricomix.core.model.Comic
import com.tricomix.core.model.ImageQuality
import com.tricomix.core.source.ComicSource
import com.tricomix.core.source.SourceCredential
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
 * 用法：
 * ```
 * probe.sh eh "关键词" 1 dry                     # 只打印将要访问的地址，不联网
 * probe.sh eh "关键词" 1                         # 列表（首页/搜索）
 * probe.sh eh "关键词" 1 full                     # 完整链路：列表 -> 详情 -> 页列表 -> 取图
 * probe.sh pica "关键词" 1 full user@x.com 密码   # 带登录（Pica 多数端点需要令牌）
 * probe.sh pica "" 1 fav user@x.com 密码          # 收藏
 * ```
 *
 * 真实请求会访问对方服务：**是否访问、用什么网络环境由使用者决定**。
 */
fun main(args: Array<String>) = runBlocking {
    val opts = parse(args)
    val sourceName = (opts["source"] ?: "eh").lowercase()
    val dry = opts.containsKey("dry")
    val full = opts.containsKey("full")
    val fav = opts.containsKey("fav")
    val query = opts["query"]
    val page = opts["page"]?.toIntOrNull() ?: 1
    val user = opts["user"] ?: System.getenv("TRICOMIX_USER")
    // 密码优先取环境变量：放在命令行参数里会出现在本机进程列表（ps）中
    val pass = opts["pass"] ?: System.getenv("TRICOMIX_PASS")

    if (opts.containsKey("selfcheck")) {
        kotlin.system.exitProcess(SelfCheck.run())
    }

    println("=== TriComiX 探针 ===")
    println("源=$sourceName  页=$page  查询=${query ?: "(无，走首页)"}  dryRun=$dry  full=$full  fav=$fav  登录=${if (user != null) "是" else "否"}")

    when (sourceName) {
        "eh" -> {
            val host = EhHost.E_HENTAI
            val url = EhUrl(host)
            if (dry) {
                printDry(listOf(url.home()) + listOfNotNull(query?.let { url.search(it, page) }) + listOf(url.favorites(page)))
                return@runBlocking
            }
            val source = EhSource(EhClient(), host)
            if (user != null) doLogin(source, user, pass.orEmpty()) { println("EH 登录成功：$it") }
            drive(source, query, page, "EH", full, fav)
        }

        "pica" -> {
            if (dry) {
                printDry(listOf("https://picaapi.picacomic.com/${PicaApi.search(query.orEmpty(), page)}"))
                return@runBlocking
            }
            val source = PicaSource()
            if (user != null) doLogin(source, user, pass.orEmpty()) { println("Pica 登录成功（已取得令牌）") }
            drive(source, query, page, "Pica", full, fav)
        }

        "jm" -> println(
            "JM 源需要 JMNeXt 的本地存储与鉴权组件（KeyValueStore / SecretKeyProvider / AuthStore），" +
                "探针未装配。要测 JM 源请直接跑 JMNeXt 桌面端（同一份实现）。"
        )

        else -> println("未知源：$sourceName（可选 eh / pica / jm）")
    }
}

/** 登录字段名两家不同（EH 用 username，Pica 用 email），两个键都填，由各源自己取。 */
private suspend fun doLogin(source: ComicSource, user: String, pass: String, onOk: (String) -> Unit) {
    val credential = SourceCredential(mapOf("username" to user, "email" to user, "password" to pass))
    source.login(credential).fold(
        onSuccess = { onOk(it.fields["username"] ?: "ok") },
        onFailure = { println("登录失败：${it::class.simpleName}: ${it.message}") },
    )
}

private fun printDry(urls: List<String>) {
    println("将要访问：")
    urls.forEach { println("  $it") }
    println("（dryRun：未发出任何请求）")
}

private suspend fun drive(source: ComicSource, query: String?, page: Int, label: String, full: Boolean, fav: Boolean) {
    println("能力: ${source.capabilities}")

    if (fav) {
        source.favorites(page).fold(
            onSuccess = { printItems(it.items, "$label 收藏") },
            onFailure = { printFailure("$label 收藏", it) },
        )
        return
    }

    val comics: List<Comic> = if (query.isNullOrBlank()) {
        source.home().fold(
            onSuccess = { it.flatMap { s -> s.items } },
            onFailure = { printFailure("$label 首页", it); return },
        )
    } else {
        source.search(query, page).fold(
            onSuccess = { it.items },
            onFailure = { printFailure("$label 搜索", it); return },
        )
    }
    printItems(comics, label)

    if (!full || comics.isEmpty()) return
    val first = comics.first()
    println("--- 完整链路：${first.title} ---")

    val detail = source.detail(first.id).fold(
        onSuccess = { it },
        onFailure = { printFailure("详情", it); return },
    )
    println("详情：${detail.comic.title}")
    println("  作者: ${detail.comic.author ?: "(无)"}   标签: ${detail.comic.tags.take(5)}")
    println("  章节数: ${detail.chapters.size}")

    val chapter = detail.chapters.firstOrNull() ?: run { println("  （没有章节，链路到此为止）"); return }
    val pages = source.pages(chapter.id).fold(
        onSuccess = { it },
        onFailure = { printFailure("页列表", it); return },
    )
    println("页列表：${pages.size} 页   首几页携带的键: ${pages.take(2).map { it.extra.keys }}")

    val firstPage = pages.firstOrNull() ?: run { println("  （没有页，链路到此为止）"); return }
    source.imageRequest(firstPage, ImageQuality.ORIGINAL).fold(
        onSuccess = { req ->
            println("第 1 页图片地址：${req.url}")
            println("  含 hath: ${req.url.contains("hath=")}   反切片: ${req.unscramble != null}")
        },
        onFailure = { printFailure("取图", it) },
    )
}

private fun printItems(items: List<Comic>, label: String) {
    println("$label 取到 ${items.size} 条")
    if (items.isEmpty()) println("  （空列表：可能选择器与真实页面不符，或站点返回了验证/登录页）")
    items.take(10).forEach { c ->
        println("  [${c.id}] ${c.title}")
        println("      封面: ${c.coverUrl ?: "(无)"}   作者: ${c.author ?: "(无)"}")
    }
}

private fun printFailure(stage: String, e: Throwable) {
    println("$stage 失败：${e::class.simpleName}: ${e.message}")
}

/** `--key value` 与 `--flag` 两种形式；位置参数依次为 源 查询 页 [模式] [用户] [密码]。 */
private fun parse(args: Array<String>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    val positional = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        val a = args[i]
        if (a.startsWith("--")) {
            val key = a.removePrefix("--")
            val next = args.getOrNull(i + 1)
            if (next != null && !next.startsWith("--")) {
                out[key] = next; i += 2
            } else {
                out[key] = ""; i++
            }
        } else {
            positional.add(a); i++
        }
    }
    positional.getOrNull(0)?.let { if (!out.containsKey("source")) out["source"] = it }
    positional.getOrNull(1)?.let { if (!out.containsKey("query")) out["query"] = it }
    positional.getOrNull(2)?.let { if (!out.containsKey("page")) out["page"] = it }
    positional.getOrNull(3)?.let { if (!out.containsKey("mode")) out["mode"] = it }
    positional.getOrNull(4)?.let { if (!out.containsKey("user")) out["user"] = it }
    positional.getOrNull(5)?.let { if (!out.containsKey("pass")) out["pass"] = it }
    // 模式位：full / fav / dry
    when (out["mode"]) {
        "full" -> out["full"] = ""
        "fav" -> out["fav"] = ""
        "dry" -> out["dry"] = ""
    }
    return out
}

