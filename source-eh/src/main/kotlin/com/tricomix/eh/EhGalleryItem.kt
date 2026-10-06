package com.tricomix.eh

/**
 * 列表页里的一条作品（**EH 自己的中间模型**，不直接暴露给 core 的统一模型）。
 *
 * 为什么单独建一个模型：网页字段是"抓来的"，与统一模型并非一一对应（例如分类、发布时间、
 * 页数在统一模型里没有位置）。先如实承载，再由 [EhSource] 决定映射与丢弃。
 */
data class EhGalleryItem(
    val gid: Long,
    val token: String,
    val title: String,
    val category: String? = null,
    val thumbUrl: String? = null,
    val uploader: String? = null,
    val posted: String? = null,
    val pages: Int? = null,
    val tags: List<String> = emptyList(),
    val rating: Float? = null,
) {
    val url: String get() = "https://e-hentai.org/g/$gid/$token/"
}

/**
 * 列表项到统一模型的**唯一**映射（[EhSource] 与测试都用它，避免两份实现各说各话）。
 *
 * id 编码约定：EH 后续请求同时需要 `gid` 与 `token`，而接口只给一个字符串，
 * 因此编码为 `"<gid>|<token>"`。
 */
internal fun EhGalleryItem.toComic(): com.tricomix.core.model.Comic =
    com.tricomix.core.model.Comic(
        sourceId = "eh",
        id = "$gid|$token",
        title = title,
        coverUrl = thumbUrl,
        author = uploader,
        tags = tags,
        description = null,
    )
