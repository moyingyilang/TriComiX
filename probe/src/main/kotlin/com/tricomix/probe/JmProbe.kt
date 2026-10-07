package com.tricomix.probe

import com.tricomix.core.source.ComicSource
import com.tricomix.jm.data.JmRepository
import com.tricomix.jm.data.auth.AuthStore
import com.tricomix.jm.data.auth.SecureStore
import com.tricomix.jm.data.auth.SecretKeyProvider
import com.tricomix.jm.data.prefs.BlockStore
import com.tricomix.jm.data.prefs.KeyValueStore
import com.tricomix.jm.source.JmSource
import kotlinx.coroutines.runBlocking
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * JM 源的独立探针：`gradle :probe:jmProbe -PjmArgs="<关键词>"`。
 *
 * 存在的理由：JM 源本身不依赖 Android，它只要求"存储 + 密钥"两个平台组件。
 * 这里用**内存实现**（AES 密钥由 JDK 现生成）把它跑起来，从而能在没有界面的情况下
 * 验证 JM 源的整条链路 —— 不必等真机、也不必装配 Android 的 Keystore。
 *
 * 说明：凭据不参与本探针（只用匿名可访问的接口）；若某接口需要登录，会如实报错。
 */
private class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String, def: String?): String? = map[key] as? String ?: def
    override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    override fun getBoolean(key: String, def: Boolean): Boolean = map[key] as? Boolean ?: def
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getInt(key: String, def: Int): Int = map[key] as? Int ?: def
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getLong(key: String, def: Long): Long = map[key] as? Long ?: def
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun clear() = map.clear()
}

private class MemoryKeys : SecretKeyProvider {
    private val key: SecretKey by lazy {
        runCatching {
            val gen = KeyGenerator.getInstance("AES")
            gen.init(256)
            gen.generateKey()
        }.getOrElse { SecretKeySpec(ByteArray(32), "AES") }
    }
    override fun aesKey(alias: String): SecretKey = key
}

private fun createJmSource(): ComicSource {
    val secure = SecureStore(prefs = MemoryStore(), keys = MemoryKeys())
    val repository = JmRepository.create(
        authStore = AuthStore(secure),
        blockStore = BlockStore(MemoryStore()),
        debug = false,
    )
    return JmSource(repository)
}

fun main(args: Array<String>) = runBlocking {
    val query = args.firstOrNull()?.takeIf { it.isNotBlank() } ?: "test"
    val source = createJmSource()
    println("=== JM 源探针（内存存储 + JDK 生成密钥，不依赖 Android）===")
    println("关键词: $query   能力: ${source.capabilities}")

    val comics = source.search(query, 1).fold(
        onSuccess = { it.items },
        onFailure = { println("搜索失败：${it::class.simpleName}: ${it.message}"); return@runBlocking },
    )
    println("搜索取到 ${comics.size} 条")
    comics.take(5).forEach { println("  [${it.id}] ${it.title}  作者=${it.author ?: "(无)"}") }
    val first = comics.firstOrNull() ?: return@runBlocking

    val detail = source.detail(first.id).fold(
        onSuccess = { it },
        onFailure = { println("详情失败：${it.message}"); return@runBlocking },
    )
    println("详情：${detail.comic.title}   章节数=${detail.chapters.size}   标签=${detail.comic.tags.take(4)}")

    val chapter = detail.chapters.firstOrNull() ?: run { println("（无章节）"); return@runBlocking }
    val pages = source.pages(chapter.id).fold(
        onSuccess = { it },
        onFailure = { println("页列表失败：${it.message}"); return@runBlocking },
    )
    println("页列表：${pages.size} 页   首项键=${pages.firstOrNull()?.extra?.keys}")

    val page = pages.firstOrNull() ?: return@runBlocking
    source.imageRequest(page, com.tricomix.core.model.ImageQuality.ORIGINAL).fold(
        onSuccess = { println("第 1 页：${it.url}   反切片=${it.unscramble != null}") },
        onFailure = { println("取图失败：${it.message}") },
    )
}
