package com.tricomix.jm.wallpaper

/**
 * 壁纸的平台无关部分：模式、状态、地址改写与轮换规则。
 *
 * 放在 shared 是因为 Android 端与桌面端应当**共用同一套语义**（模式、缓存上下限、冷却、
 * 跨天重取、署名要求），各端只负责"怎么发请求、怎么把图显示出来"。
 * Android 端原本已有一份实现（app/.../data/wallpaper/WallpaperStore.kt），本文件是把它
 * 的平台无关部分抽出来，供两端共用；桌面端此前**完全没有**在线壁纸这一套（预设渐变 + 本地图片
 * + 模糊压暗是有的），所以这次是补缺口。
 */
enum class WallpaperMode(val label: String, val desc: String) {
    Off("纯渐变", "不加载任何图片，只用内置渐变底（不发任何请求）"),
    Bing("Bing 每日", "Bing 每日壁纸，来源 bing.biturl.top"),
    Anime("二次元随机", "t.alcy.cc / t.mwm.moe / loliapi 三个源依次尝试"),
    All("全部随机", "Bing 与二次元混合"),
    Custom("自定义地址", "自己填一个图片直链"),
}

/**
 * 壁纸状态。[url] 是当前正在显示的那张；[credit] 是它的署名
 * （Bing 会回摄影者与地点）—— 界面上必须显示：用了别人的图，署名是最低要求。
 */
data class WallpaperState(
    val mode: WallpaperMode = WallpaperMode.Off,
    val url: String? = null,
    val credit: String? = null,
    /** 图片高斯模糊 0..[MAX_BLUR]。 */
    val blur: Int = 0,
    /** 压暗遮罩 0..[MAX_DIM]，默认 0.12。 */
    val dim: Float = 0.12f,
    /** 自动更换间隔（分钟），0 = 只在手动换一张时换。 */
    val intervalMinutes: Int = 0,
    val customUrl: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** 该不该画图片层。纯渐变模式下永远是 false。 */
    val showsImage: Boolean get() = mode != WallpaperMode.Off && !url.isNullOrBlank()
}

object WallpaperSources {
    /** Bing 尺寸段形如 `_1920x1080.jpg`，只认这一种形状。 */
    private val BING_SIZE = Regex("_(\\d+)x(\\d+)(\\.jpg)", RegexOption.IGNORE_CASE)

    const val BING_API = "https://bing.biturl.top/?resolution=1920&format=json&index=random&mkt=zh-CN"

    /** 二次元源：依次尝试，成功时记下**重定向之后的最终地址**，之后直接用，省掉每次绘制的跳转。 */
    val ANIME_APIS: List<String> = listOf(
        "https://t.alcy.cc/ycy",
        "https://t.mwm.moe/pc",
        "https://www.loliapi.com/acg/",
    )

    const val ANIME_CREDIT = "二次元图源"

    const val MAX_BLUR = 24
    const val MAX_DIM = 0.6f

    /** 手动连点的冷却，避免把第三方接口当图床刷。 */
    const val COOLDOWN_MS = 800L

    /** 缓存攒到这么多张后就不再联网，只在本地轮换。 */
    const val CACHE_MIN = 4
    const val CACHE_MAX = 8

    /**
     * 把 Bing 返回地址里的 `_宽x高` 段改成我们要的尺寸。
     *
     * Bing 按尺寸段裁剪，实测改段是**真的换图**（请求 `_1080x1920.jpg` 拿回来就是 1080×1920，
     * 不是把横图硬拉长）。这一点很值得做：竖屏直接用返回的 `_1920x1080` 横图，
     * 铺满时会被裁掉两侧很大一块，构图经常就废了。
     *
     * 只在能匹配 `_数字x数字` 时改写，其余原样返回 —— 这是别人的地址，猜错了整张图就加载不出来。
     * 只改**第一段**尺寸：`Regex.replace` 会把所有匹配都改掉，查询串里另一个碰巧像尺寸的段也会被改写。
     */
    fun bingSizedUrl(url: String, portrait: Boolean): String {
        val target = if (portrait) "1080x1920" else "1920x1080"
        val m = BING_SIZE.find(url) ?: return url
        return url.substring(0, m.range.first) +
            "_${target}${m.groupValues[3]}" +
            url.substring(m.range.last + 1)
    }

    /**
     * 是否需要联网补图：缓存没攒够（或空的），或者 Bing 跨天了。
     * 攒够之后就一直吃缓存轮换 —— 第三方接口不该被反复调用。
     */
    fun needFetch(cacheSize: Int, wantBing: Boolean, cachedDay: String?, today: String): Boolean =
        cacheSize <= 0 || cacheSize < CACHE_MIN || (wantBing && cachedDay != today)

    /**
     * 从池子里取下一张：按游标取，跳过当前这张（避免连点两次看到同一张）。
     * 返回选中的地址与**新的游标**（调用方负责保存游标）。
     */
    fun nextFromPool(pool: List<String>, cursor: Int, current: String?): Pair<String, Int> {
        require(pool.isNotEmpty()) { "池子为空" }
        var c = ((cursor % pool.size) + pool.size) % pool.size
        if (pool.size > 1 && pool.getOrNull(c) == current) c = (c + 1) % pool.size
        return pool[c] to ((c + 1) % pool.size)
    }

    /** 混合模式下用游标奇偶决定这次取 Bing 还是二次元，避免再存一个计数器。 */
    fun wantBingFor(mode: WallpaperMode, cursor: Int): Boolean = when (mode) {
        WallpaperMode.Bing -> true
        WallpaperMode.All -> cursor % 2 == 0
        else -> false
    }
}
