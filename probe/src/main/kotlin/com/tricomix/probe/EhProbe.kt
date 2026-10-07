package com.tricomix.probe

import com.tricomix.eh.EhClient
import com.tricomix.eh.EhHost
import com.tricomix.eh.EhSource
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * 定向 EH 探针：`gradle :probe:ehProbe -PehArgs="<gid> <token>"`。
 *
 * 只打在一部**指定作品**上，验证长作品的分批取页：
 * - 首批 20 页来自详情页，其余靠翻缩略图页（`?p=N`）补齐；
 * - 这里直接调源的 `pages()`，看最终有多少页带有 imgkey（缺 imgkey 的页无法取图）。
 *
 * 为什么需要它：普通探针只跑搜索的第一条，命中长作品的概率很低，
 * 而"分批取页"这条路径只有长作品才会走到。
 */
fun main(args: Array<String>) {
    val gid = args.getOrNull(0)?.toLongOrNull() ?: run {
        println("用法：-PehArgs=\"<gid> <token>\"（例如 4233882 4eeb0b0f38）")
        exitProcess(2)
    }
    val token = args.getOrNull(1) ?: run {
        println("缺少 token 参数")
        exitProcess(2)
    }
    val host = EhHost.E_HENTAI
    runBlocking {
        val source = EhSource(EhClient(), host)
        println("=== 源接口链路（gid=$gid token=$token）===")
        val detail = source.detail("$gid|$token").getOrElse {
            println("详情失败：${it.message}"); return@runBlocking
        }
        println("  标题: ${detail.comic.title}")
        println("  章节数: ${detail.chapters.size}")
        val chapter = detail.chapters.firstOrNull() ?: run { println("无章节"); return@runBlocking }

        val pages = source.pages(chapter.id).getOrElse {
            println("页列表失败：${it.message}"); return@runBlocking
        }
        val withKey = pages.count { it.extra["imgkey"] != null }
        println("  页数: ${pages.size}   带 imgkey 的页数: $withKey")
        if (withKey < pages.size) {
            println("  ** 仍有 ${pages.size - withKey} 页缺 imgkey（那些页取不到图）**")
        } else {
            println("  → 全部页都拿到了 imgkey，分批取页成功")
        }
        // 抽查最后一页能否取到图片地址
        pages.lastOrNull()?.let { last ->
            source.imageRequest(last, com.tricomix.core.model.ImageQuality.ORIGINAL).fold(
                onSuccess = { println("  最后一页图片地址: ${it.url.take(120)}") },
                onFailure = { println("  最后一页取图失败：${it.message}") },
            )
        }
    }
}
