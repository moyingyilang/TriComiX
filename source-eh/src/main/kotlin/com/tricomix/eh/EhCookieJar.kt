package com.tricomix.eh

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * E-Hentai 的 cookie 存储。
 *
 * 该站的**登录态就是 cookie**（没有 token 体系），所以这块是 `login` 的基础：
 * 登录成功后服务端下发 cookie，之后每个请求都要带上。
 *
 * 设计取舍：
 * - 只做存储与匹配，**不做持久化落盘** —— 持久化形态由上层决定（Android 用 SharedPreferences、
 *   桌面用文件），这里提供 [snapshot] / [restore] 让上层自己存，避免在这一层发明存储接口；
 * - 用 [ConcurrentHashMap] 按 host 分组，读写都可能来自不同线程。
 */
class EhCookieJar : CookieJar {

    private val byHost = ConcurrentHashMap<String, MutableList<Cookie>>()

    @Volatile
    var persistEnabled: Boolean = true

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!persistEnabled) return
        val list = byHost.getOrPut(url.host) { mutableListOf() }
        synchronized(list) {
            for (cookie in cookies) {
                list.removeAll { it.name == cookie.name && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) list.add(cookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<Cookie>()
        for ((host, list) in byHost) {
            if (!hostMatches(url.host, host)) continue
            synchronized(list) {
                list.removeAll { it.expiresAt <= now }
                out.addAll(list.filter { it.matches(url) })
            }
        }
        return out
    }

    /** 导出（供上层持久化）。 */
    fun snapshot(): List<String> {
        val out = mutableListOf<String>()
        for (list in byHost.values) synchronized(list) { list.forEach { out.add(it.toString()) } }
        return out
    }

    /** 从 [snapshot] 恢复；解析失败的条目被跳过而不是让整体失败。 */
    fun restore(lines: List<String>, url: HttpUrl) {
        for (line in lines) {
            val parsed = runCatching { Cookie.parse(url, line) }.getOrNull() ?: continue
            saveFromResponse(url, listOf(parsed))
        }
    }

    fun clear() = byHost.clear()

    /** 有多少个 cookie（测试与自检用）。 */
    fun size(): Int = byHost.values.sumOf { list -> synchronized(list) { list.size } }

    private fun hostMatches(host: String, stored: String): Boolean =
        host == stored || host.endsWith(".$stored") || stored.endsWith(".$host")
}
