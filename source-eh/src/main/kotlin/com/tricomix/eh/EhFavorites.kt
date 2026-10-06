package com.tricomix.eh

/**
 * EH 收藏页。
 *
 * 收藏页（`/favorites.php`）与搜索/首页**共用同一套列表结构**（`table.itg` + `glname` 等），
 * 所以解析直接复用 [GalleryListParser]，不另写一套（少一份会各自漂移的选择器）。
 *
 * 关键判断：**未登录时站点会重定向到登录页**，那里没有 `itg` 表格。此时必须明确报
 * "需要登录"，而不是把"没登录"伪装成"收藏是空的"。
 *
 * **未验证**：从未对真实站点发过请求；"收藏页与列表同构"这一点来自静态阅读。
 */
object EhFavorites {

    /** 页面是否呈现为列表视图（含 `itg` 表格）。 */
    fun isListView(html: String): Boolean = html.contains("itg")

    fun parse(html: String, baseUrl: String): List<EhGalleryItem> =
        GalleryListParser.parse(html, baseUrl)
}
