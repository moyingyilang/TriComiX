package com.tricomix.core.model

/**
 * 统一模型。
 *
 * 设计约束（见 docs/design.md）：只放"各家都能表达"的字段；源特有信息进 [extra]，
 * 避免把统一模型做成万能字典。
 */

/** 作品：各源的 album / comic / gallery 统一到这里。 */
data class Comic(
    val sourceId: String,
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val author: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val extra: Map<String, String> = emptyMap(),
)

data class Chapter(
    val id: String,
    val title: String,
    /** 展示与排序用的序号（各源顺序语义不同，这里只保证单调）。 */
    val order: Int = 0,
    val extra: Map<String, String> = emptyMap(),
)

data class ComicDetail(
    val comic: Comic,
    val chapters: List<Chapter> = emptyList(),
)

/**
 * 阅读单元。
 *
 * **注意**：各源语义不同 —— jm/pica 是"章节"，eh 是"分页"。
 * 因此界面层只依赖"页序列"，不要依赖"章节"这个概念。
 */
data class PageRef(
    val chapterId: String,
    val index: Int,
    val extra: Map<String, String> = emptyMap(),
)

/** 取图请求：URL + 需要的请求头 + 可选的本地变换。 */
data class ImageRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** 需要在本地做的变换，例如打乱图的还原参数。 */
    val unscramble: UnscrambleSpec? = null,
)

/** 打乱图还原参数（源私有逻辑，放在这里是为了让界面层不必知道细节）。 */
data class UnscrambleSpec(val seed: String, val index: Int)

enum class ImageQuality { LOW, MEDIUM, HIGH, ORIGINAL }

data class Section(val title: String, val items: List<Comic>)

data class Paged<T>(val items: List<T>, val page: Int, val hasMore: Boolean)
