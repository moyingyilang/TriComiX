package com.tricomix.eh

/**
 * E-Hentai 的 `api.php` 请求体构造（**纯函数，便于单测**）。
 *
 * 依据：对参照实现 `EhEngine` 的结构阅读 —— 它向 `/api.php` POST JSON：
 * - `gdata`：`{"method":"gdata","gidlist":[[gid,token]],"namespace":1}`（取元数据与页数）
 * - `showpage`：`{"method":"showpage","gid":..,"page":<n+1>,"imgkey":..,"showkey":..}`（取单页图片）
 * - `gtoken`：`{"method":"gtoken","pagelist":[[gid,token,page]]}`（批量取 token）
 *
 * **未验证**：从未对真实站点发过请求。`showpage` 需要的 `imgkey`/`showkey` 是**逐页**的值，
 * 其来源尚未落实（见 [EhSource.pages] 的说明）。
 */
object EhApi {

    fun endpoint(host: EhHost): String = "${host.baseUrl}/api.php"

    fun gdata(gid: Long, token: String, namespace: Int = 1): String =
        "{\"method\":\"gdata\",\"gidlist\":[[$gid,\"$token\"]],\"namespace\":$namespace}"

    /** `page` 对外 0 起，API 用 1 起（参照实现里是 `i + 1`）。 */
    fun showPage(gid: Long, pageIndex: Int, imgKey: String, showKey: String): String =
        "{\"method\":\"showpage\",\"gid\":$gid,\"page\":${pageIndex + 1}," +
            "\"imgkey\":\"$imgKey\",\"showkey\":\"$showKey\"}"

    fun gtoken(pages: List<Triple<Long, String, Int>>): String {
        val list = pages.joinToString(",") { (gid, token, page) -> "[$gid,\"$token\",$page]" }
        return "{\"method\":\"gtoken\",\"pagelist\":[$list]}"
    }
}

/** `showpage` 的解析结果。 */
data class EhPageImage(
    /** 页面上显示的图片地址。 */
    val imageUrl: String,
    /** 原图地址（若响应里给了）。 */
    val originUrl: String?,
    /** 从地址里提取到的 `hath` 键（没有则为 null）。 */
    val hath: String?,
)

/**
 * `showpage` 响应的解析。
 *
 * 依据：参照实现 `GalleryPageApiParser` 的三条正则的形状 ——
 * `i3` 里是 `<img ... src="URL" style`；`i7`/`i6` 里的原图是
 * `<a href="URLfullimg...">` 或新版的 `onclick="prompt('Copy the URL below.', 'URL')"`。
 * 正则是我自己写的（用转义字符串写，避免原始字符串的引号歧义）。
 *
 * **未验证**：从未对真实站点发过请求。
 */
object GalleryPageApiParser {

    private val IMG_SRC = Regex("<img[^>]*src=\"([^\"]+)\"\\s+style")
    private val IMG_SRC_LOOSE = Regex("<img[^>]*src=\"([^\"]+)\"")
    private val ORIGIN_PROMPT = Regex("prompt\\('Copy the URL below\\.',\\s*'([^']+)'\\)")
    private val ORIGIN_HREF = Regex("<a href=\"([^\"]+?)fullimg[^\"]*\"")
    private val HATH = Regex("[?&]hath=([^&\"'<]+)")

    fun parse(responseBody: String): EhPageImage {
        val fields = FlatJson.fields(responseBody)
        if (fields.containsKey("error")) {
            throw IllegalStateException("服务端返回 error：${fields["error"]}")
        }
        val i3 = fields["i3"] ?: throw IllegalStateException("响应缺少 i3")
        val imageUrl = (IMG_SRC.find(i3) ?: IMG_SRC_LOOSE.find(i3))?.groupValues?.get(1)
            ?: throw IllegalStateException("无法从 i3 中提取图片地址")
        val origin = sequenceOf(fields["i7"].orEmpty(), fields["i6"].orEmpty())
            .mapNotNull { text ->
                (ORIGIN_PROMPT.find(text) ?: ORIGIN_HREF.find(text))?.groupValues?.get(1)
            }
            .firstOrNull()
        return EhPageImage(
            imageUrl = imageUrl,
            originUrl = origin,
            hath = HATH.find(imageUrl)?.groupValues?.get(1),
        )
    }
}

/**
 * 只为解析 `showpage` 这种**顶层字符串字段**的 JSON 而写的小工具。
 *
 * 为什么不用序列化框架：该响应里 `i3`/`i6`/`i7` 的值是 **HTML 片段**，套模型反而绕；
 * 这里只需要"把顶层字段取成字符串"，自己写一个更直接，也更好测。
 */
internal object FlatJson {

    private val STRING_FIELD =
        Regex("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

    fun fields(text: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        for (m in STRING_FIELD.findAll(text)) out[m.groupValues[1]] = unescape(m.groupValues[2])
        // error 可能不是字符串（例如 null 或对象），只要出现过就标记，避免把失败当成功
        if (Regex("\"error\"").containsMatchIn(text) && !out.containsKey("error")) {
            out["error"] = "unknown-error"
        }
        return out
    }

    private fun unescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    'r' -> append('\r')
                    '"' -> append('"')
                    '\\' -> append('\\')
                    '/' -> append('/')
                    'u' -> {
                        if (i + 5 < s.length) {
                            s.substring(i + 2, i + 6).toIntOrNull(16)?.let { append(it.toChar()) }
                            i += 4
                        }
                    }
                    else -> append(n)
                }
                i += 2
            } else {
                append(c); i++
            }
        }
    }
}
