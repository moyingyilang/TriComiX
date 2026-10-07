package com.tricomix.eh

/**
 * 把详情页的首批 `imgkey` 补齐到完整页数。
 *
 * ## 走了弯路，记在这里（避免将来又回退到旧方案）
 *
 * 旧参照实现用的是 `api.php` 的 `gtoken` 方法换 token。**该方法在新版站点已失效**：
 * 实测对同一部作品请求第 1-3 页与第 21-23 页，均返回
 * `{"tokenlist":[{"gid":…,"error":"Invalid page."}]}` —— **连第一页也失败**，
 * 说明不是页段问题，而是方法本身过时（与已消失的 `showkey` 同一类变化）。
 *
 * ## 现行做法：翻缩略图页（实测验证）
 *
 * 详情页 `?p=0` 给前 20 页的 imgkey，`?p=1` 给 21-40，`?p=2` 给 41-60 ……：
 * ```
 * p=0 → /s/<imgkey>/<gid>-1  … -20
 * p=1 → /s/<imgkey>/<gid>-21 … -40
 * ```
 * 因此这里改为逐页抓取 `?p=N` 并解析其中的 imgkey，直到凑满页数。
 */
object EhPageKeys {

    /** 单部作品最多翻多少页（安全上限，避免页数异常时无限循环）。 */
    const val MAX_PAGES = 100

    /** 翻页之间的间隔（毫秒）。实测密集请求会被源拒绝，所以宁可慢一点。 */
    const val PAGE_DELAY_MS = 700L

    suspend fun complete(
        client: EhClient,
        host: EhHost,
        gid: Long,
        token: String,
        count: Int,
        inlineTokens: List<String>,
    ): List<String?> {
        val keys = MutableList<String?>(count) { inlineTokens.getOrNull(it) }
        if (inlineTokens.size >= count) return keys

        val url = EhUrl(host)
        val galleryUrl = url.gallery(gid, token)
        var produced = inlineTokens.size
        var pageIndex = 1
        while (produced < count && pageIndex <= MAX_PAGES) {
            // 节流：EH 对短时间内的密集请求会拒绝（表现为取不到图/页）。
            kotlinx.coroutines.delay(PAGE_DELAY_MS)
            val html = runCatching { client.get(url.galleryPage(gid, token, pageIndex)) }.getOrNull() ?: break
            val tokens = GalleryDetailParser.parse(html, galleryUrl).pageTokens
            if (tokens.isEmpty()) break
            for ((i, key) in tokens.withIndex()) {
                val idx = produced + i
                if (idx < count) keys[idx] = key
            }
            produced += tokens.size
            pageIndex++
        }
        return keys
    }
}
