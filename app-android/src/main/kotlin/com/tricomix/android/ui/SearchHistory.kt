package com.tricomix.android.ui

import com.tricomix.jm.data.prefs.KeyValueStore

/**
 * 搜索历史：去重、最近优先、有上限。
 *
 * 为什么放在上层：这属于**本机状态**，不是任何图源的能力 —— 换源不该丢掉自己的搜索记录，
 * 而源也不该被迫实现它（见 `docs/ui-port-plan.md` 里"接口缺口"一节）。
 *
 * 存储走已有的 [KeyValueStore] 抽象（Android 下是 SharedPreferences 实现），
 * 因此这里不含任何 Android 依赖，可以直接单测。
 */
class SearchHistory(
    private val store: KeyValueStore,
    private val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val KEY = "search_history"
        private const val SEPARATOR = "\n"
    }

    /** 全部记录，最近的在前。 */
    fun all(): List<String> =
        store.getString(KEY).orEmpty()
            .split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /**
     * 记一次搜索：重复的提到最前、超出上限的丢弃。
     *
     * 空白词直接忽略（点空搜索不该污染历史）。
     */
    fun add(word: String): List<String> {
        val w = word.trim()
        if (w.isEmpty()) return all()
        val next = (listOf(w) + all().filter { it != w }).take(limit)
        save(next)
        return next
    }

    fun remove(word: String): List<String> = all().filter { it != word.trim() }.also { save(it) }

    fun clear() = store.remove(KEY)

    private fun save(list: List<String>) = store.putString(KEY, list.joinToString(SEPARATOR))
}
