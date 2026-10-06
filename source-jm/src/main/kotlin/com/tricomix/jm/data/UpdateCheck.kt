package com.tricomix.jm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 检查更新（1.8.0 起；2.0.0 把取 tag 的网络调用也收到这里）。
 *
 * 数据来自 GitHub Releases（`releases/latest` 的 `tag_name`），比较逻辑是**纯函数**，
 * 因为这里有一个不显眼但一定会踩的坑：
 *
 * **Android 端的 versionName 带变体后缀**（`1.8.0.lite` / `1.8.0.debug` / `1.8.0.litedebug`），
 * 而 GitHub 的 tag 是干净的（`v1.8.0`）。直接字符串比较永远得出"有新版本"，
 * 直接按数字段比较又会因为多出来的段而判断错。
 *
 * 所以先**归一化**：去掉 `v` 前缀、只取前三段数字、忽略其余后缀。
 */
object UpdateCheck {

    /**
     * 把版本串归一化成可比较的数字段；解析不出来返回 null。
     *
     * 例：`v1.8.0` → `[1, 8, 0]`；`1.8.0.lite` → `[1, 8, 0]`；`1.8.0.litedebug` → `[1, 8, 0]`。
     */
    fun parts(raw: String?): List<Int>? {
        val cleaned = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        if (cleaned.isEmpty()) return null
        val numbers = cleaned.split('.', '-', '+')
            .takeWhile { it.isNotEmpty() && it.all(Char::isDigit) }
            .mapNotNull { it.toIntOrNull() }
        return numbers.takeIf { it.isNotEmpty() }
    }

    /**
     * 远端版本是否比当前版本新。
     *
     * 逐段比较，缺的段按 0 处理（`1.8` 与 `1.8.0` 视为相同）；任一侧解析不出来时返回 false ——
     * **宁可不说"有更新"**：误报会让用户去装一个并不存在的新版本，比漏报更糟。
     */
    fun isNewer(latest: String?, current: String?): Boolean {
        val remote = parts(latest) ?: return false
        val local = parts(current) ?: return false
        val size = maxOf(remote.size, local.size)
        for (i in 0 until size) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r != l) return r > l
        }
        return false
    }

    /** 该把用户带到哪个页面：优先完整的 URL，其次按 tag 拼发布页。 */
    fun releaseUrl(tagOrUrl: String?, repo: String = REPO): String {
        val t = tagOrUrl?.trim().orEmpty()
        if (t.startsWith("http")) return t
        return if (t.isEmpty()) "https://github.com/$repo/releases" else "https://github.com/$repo/releases/tag/$t"
    }

    /**
     * 取 GitHub 最新发布的 tag；失败返回 null。
     *
     * 放在共享层而不是各端页面里：Android 与桌面都要做同一件事，
     * 各写一份的话，改 UA、改超时、改错误处理都得记住改两处。
     */
    suspend fun fetchLatestTag(client: OkHttpClient): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .build()
        runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body.string()
                Json.parseToJsonElement(body).jsonObject["tag_name"]?.jsonPrimitive?.content
            }
        }.getOrNull()
    }

    /**
     * 版本号归一化成纯数字段（去掉 v 前缀与变体后缀），用于拼附件名。
     * 例：`v2.1.7` → `2.1.7`；`2.1.7.lite` → `2.1.7`。
     */
    fun cleanVersion(raw: String?): String =
        parts(raw)?.joinToString(".") ?: raw?.trim()?.removePrefix("v").orEmpty()

    /** 发布 tag 的规范形式（带 v 前缀）。附件直链里必须用它。 */
    fun tagOf(raw: String?): String {
        val t = raw?.trim().orEmpty()
        return if (t.startsWith("v") || t.startsWith("V")) t else "v" + cleanVersion(t)
    }

    /**
     * Android 该下哪个包 —— 与发布脚本的命名严格对应。
     *
     * 这里正是 issue #5 的症结：发布页的附件名是 `Android-full-<版本>.apk`，
     * **名字里没有架构**（一个包同时含 arm64 与 x86_64 的 native 库）。
     * 用户按"arm64 版"去找，自然找不到。
     */
    fun androidAssetName(version: String?, lite: Boolean): String =
        "Android-" + (if (lite) "lite" else "full") + "-" + cleanVersion(version) + ".apk"

    /**
     * 桌面端按系统与架构给出候选附件名，**第一个是最推荐的**（统一包，内含两套运行时，不用分辨架构）。
     */
    fun desktopAssetNames(version: String?, os: String?, arch: String?): List<String> {
        val v = cleanVersion(version)
        val o = os.orEmpty().lowercase()
        val a = arch.orEmpty().lowercase()
        val isArm = a.contains("arm") || a.contains("aarch64")
        val isWin = o.contains("win")
        return if (isWin) {
            listOf("Windows-universal-$v.exe", "Windows-" + (if (isArm) "arm64" else "x64") + "-$v.zip")
        } else {
            listOf("Linux-universal-$v.tar.gz", "Linux-" + (if (isArm) "aarch64" else "x86_64") + "-$v.tar.gz")
        }
    }

    /** release 里某个附件的直链。 */
    fun assetUrl(tag: String?, assetName: String, repo: String = REPO): String =
        "https://github.com/" + repo + "/releases/download/" + tagOf(tag) + "/" + assetName

    const val REPO = "moyingyilang/JMNeXt"

    /** GitHub 的"最新发布"接口。 */
    const val LATEST_RELEASE_API = "https://api.github.com/repos/$REPO/releases/latest"
}
