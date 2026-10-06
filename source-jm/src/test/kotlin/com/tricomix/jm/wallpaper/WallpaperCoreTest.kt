package com.tricomix.jm.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperCoreTest {

    @Test
    fun `Bing 尺寸段按屏幕方向改写且只改第一段`() {
        val landscape = "https://cn.bing.com/th?id=OHR.X_1920x1080.jpg&rf=LaDigue"
        assertEquals(
            "https://cn.bing.com/th?id=OHR.X_1080x1920.jpg&rf=LaDigue",
            WallpaperSources.bingSizedUrl(landscape, portrait = true),
        )
        assertEquals(
            "https://cn.bing.com/th?id=OHR.X_1920x1080.jpg&rf=LaDigue",
            WallpaperSources.bingSizedUrl(landscape, portrait = false),
        )
        // 只改第一段：查询串里另一段像尺寸的内容不许被动
        val two = "https://x/a_1920x1080.jpg?p=b_800x600.jpg"
        assertEquals(
            "https://x/a_1080x1920.jpg?p=b_800x600.jpg",
            WallpaperSources.bingSizedUrl(two, portrait = true),
        )
        // 匹配不上就原样返回
        val noMatch = "https://x/a.jpg?w=1920&h=1080"
        assertEquals(noMatch, WallpaperSources.bingSizedUrl(noMatch, portrait = true))
    }

    @Test
    fun `什么时候才联网补图`() {
        // 空缓存要联网
        assertTrue(WallpaperSources.needFetch(0, wantBing = true, cachedDay = "2026-10-04", today = "2026-10-04"))
        // 没攒够 4 张要联网
        assertTrue(WallpaperSources.needFetch(3, wantBing = false, cachedDay = null, today = "2026-10-04"))
        // 攒够且不是 Bing：不联网
        assertFalse(WallpaperSources.needFetch(4, wantBing = false, cachedDay = null, today = "2026-10-04"))
        // 攒够但是 Bing 且跨天了：要联网
        assertTrue(WallpaperSources.needFetch(4, wantBing = true, cachedDay = "2026-10-03", today = "2026-10-04"))
        // 攒够且 Bing 且今天已取过：不联网
        assertFalse(WallpaperSources.needFetch(4, wantBing = true, cachedDay = "2026-10-04", today = "2026-10-04"))
    }

    @Test
    fun `轮换会跳过当前这张并正确回绕`() {
        val pool = listOf("a", "b", "c")
        assertEquals("a" to 1, WallpaperSources.nextFromPool(pool, cursor = 0, current = null))
        // 当前是 a，游标又指到 a 时要跳到 b
        assertEquals("b" to 2, WallpaperSources.nextFromPool(pool, cursor = 0, current = "a"))
        // 回绕
        assertEquals("a" to 1, WallpaperSources.nextFromPool(pool, cursor = 3, current = "c"))
        // 只有一张时不跳（避免死循环）
        assertEquals("solo" to 0, WallpaperSources.nextFromPool(listOf("solo"), cursor = 0, current = "solo"))
    }

    @Test
    fun `混合模式按游标奇偶决定来源`() {
        assertTrue(WallpaperSources.wantBingFor(WallpaperMode.Bing, 1))
        assertTrue(WallpaperSources.wantBingFor(WallpaperMode.All, 0))
        assertFalse(WallpaperSources.wantBingFor(WallpaperMode.All, 1))
        assertFalse(WallpaperSources.wantBingFor(WallpaperMode.Anime, 0))
        assertFalse(WallpaperSources.wantBingFor(WallpaperMode.Off, 0))
    }

    @Test
    fun `只有非纯渐变且有地址时才画图片层`() {
        assertFalse(WallpaperState().showsImage)
        assertFalse(WallpaperState(mode = WallpaperMode.Bing).showsImage)
        assertTrue(WallpaperState(mode = WallpaperMode.Bing, url = "https://x/a.jpg").showsImage)
        assertFalse(WallpaperState(mode = WallpaperMode.Off, url = "https://x/a.jpg").showsImage)
    }
}
