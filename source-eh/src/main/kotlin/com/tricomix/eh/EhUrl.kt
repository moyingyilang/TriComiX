package com.tricomix.eh

/**
 * E-Hentai 的 URL 构造（纯逻辑，不涉及网络）。
 *
 * 依据：`docs/eh-protocol.md` 记录的站点结构（来自对参照实现的静态阅读，**未对真实站点验证**）。
 * 这里只做字符串拼接，便于单测覆盖；网络与解析在 EhClient / 各 Parser 里。
 */
class EhUrl(private val host: EhHost = EhHost.E_HENTAI) {

    val hostName: String get() = host.baseUrl

    /** 首页。 */
    fun home(): String = "$hostName/"

    /** 搜索（`f_search` 为查询词，`page` 从 0 开始）。 */
    fun search(query: String, page: Int = 0): String = buildString {
        append(hostName).append("/?f_search=").append(urlEncode(query))
        if (page > 0) append("&page=").append(page)
    }

    /** 标签页。 */
    fun tag(tag: String, page: Int = 0): String = buildString {
        append(hostName).append("/tag/").append(urlEncode(tag))
        if (page > 0) append("?page=").append(page)
    }

    /** 作品详情页：`/g/<gid>/<token>/`。 */
    fun gallery(gid: Long, token: String): String = "$hostName/g/$gid/$token/"

    /** 作品的图片列表某一页：`/g/<gid>/<token>/?p=<page>`（page 从 0 开始）。 */
    fun galleryPage(gid: Long, token: String, page: Int): String =
        gallery(gid, token) + "?p=" + page

    /** 收藏操作（POST 目标）。 */
    fun addFavorite(gid: Long, token: String): String =
        "$hostName/gallerypopups.php?gid=$gid&t=$token&act=addfav"

    /** 归档下载页。 */
    fun archive(gid: Long, token: String, original: Boolean = false): String =
        "$hostName/archiver.php?gid=$gid&token=$token" + if (original) "&or=1" else ""

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
}

/** 站点选择。细节（可用性探测）在 EhHosts 的对应实现里。 */
enum class EhHost(val baseUrl: String) {
    E_HENTAI("https://e-hentai.org"),
    EXHENTAI("https://exhentai.org"),
}
