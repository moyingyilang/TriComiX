package com.tricomix.android.ui

import com.tricomix.jm.data.prefs.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 内存实现，替身用（与 Android 的 SharedPreferences 实现同一接口）。 */
private class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, Any>()
    override fun getString(key: String, def: String?): String? = map[key] as? String ?: def
    override fun putString(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    override fun getBoolean(key: String, def: Boolean): Boolean = map[key] as? Boolean ?: def
    override fun putBoolean(key: String, value: Boolean) { map[key] = value }
    override fun getInt(key: String, def: Int): Int = map[key] as? Int ?: def
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun getLong(key: String, def: Long): Long = map[key] as? Long ?: def
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun clear() = map.clear()
}

class SearchHistoryTest {

    @Test
    fun `最近搜索排在最前`() {
        val h = SearchHistory(MemoryStore())
        h.add("一")
        h.add("二")
        assertEquals(listOf("二", "一"), h.all())
    }

    @Test
    fun `重复的词提到最前而不是新增一条`() {
        val h = SearchHistory(MemoryStore())
        h.add("一"); h.add("二"); h.add("一")
        assertEquals(listOf("一", "二"), h.all())
    }

    @Test
    fun `超出上限时丢弃最旧的`() {
        val h = SearchHistory(MemoryStore(), limit = 3)
        listOf("一", "二", "三", "四").forEach { h.add(it) }
        assertEquals(listOf("四", "三", "二"), h.all())
    }

    @Test
    fun `空白词被忽略`() {
        val h = SearchHistory(MemoryStore())
        h.add("  ")
        h.add("")
        assertTrue(h.all().isEmpty())
    }

    @Test
    fun `删除与清空`() {
        val h = SearchHistory(MemoryStore())
        h.add("一"); h.add("二")
        assertEquals(listOf("二"), h.remove("一"))
        h.clear()
        assertTrue(h.all().isEmpty())
    }

    @Test
    fun `重新读取同一存储得到同样的历史（说明确实落盘了）`() {
        val store = MemoryStore()
        SearchHistory(store).add("关键词")
        assertEquals(listOf("关键词"), SearchHistory(store).all())
    }
}
