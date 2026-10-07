package com.tricomix.eh

/**
 * 图片页（`/s/<imgkey>/<gid>-<page>`）解析。
 *
 * **依据是抓真实页面得到的**：新版站点**不再有 `showkey`**（旧参照实现的 `showpage` API 流程已过时），
 * 正确做法是直接打开图片页。真实页面的坑：
 * - 页面顶部若干 `<img>` 是**界面图标**（`ehgt.org/g/f.png` 等），直接取"第一个 img"必然取错；
 * - 真正的图片地址出现在两处：内容图 `<img … src="…" style="…">`（**只有它带 `style`**），
 *   以及复制按钮的 `prompt('Copy the URL below.', '<地址>')`；
 * - 原图另有 `<a href="…/fullimg/…">Download original</a>`。
 *
 * 因此本解析器**不使用**"任意 img"的宽松回退，宁可为空也不返回图标地址。
 */
object GalleryPageParser {

    private val IMG_WITH_STYLE = Regex("<img[^>]*src=\"([^\"]+)\"[^>]*style")
    private val COPY_PROMPT = Regex("prompt\\('Copy the URL below\\.',\\s*'([^']+)'\\)")
    private val ORIGIN_HREF = Regex("<a href=\"([^\"]+?)\"[^>]*>Download original")
    private val ORIGIN_FULLIMG = Regex("href=\"([^\"]*fullimg[^\"]*)\"")
    private val HATH = Regex("[?&]hath=([^&\"'<]+)")

    fun parse(html: String): EhPageImage {
        val imageUrl = (IMG_WITH_STYLE.find(html) ?: COPY_PROMPT.find(html))?.groupValues?.get(1)
            ?: throw IllegalStateException("图片页里找不到图片地址（页面结构可能已变）")
        val origin = (ORIGIN_HREF.find(html) ?: ORIGIN_FULLIMG.find(html))?.groupValues?.get(1)
        return EhPageImage(
            imageUrl = imageUrl,
            originUrl = origin,
            hath = HATH.find(imageUrl)?.groupValues?.get(1),
        )
    }
}
