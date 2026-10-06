package com.tricomix.jm.selftune

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 平台无关的状态存储：Android 用应用数据目录、桌面用用户目录，各自实现这两个方法。
 * 放进 shared 的只有"读一段文本 / 写一段文本"，不涉及任何平台路径。
 */
interface SelfTuneStore {
    fun read(): String?
    fun write(text: String)
}

/** 持久化的学习状态。schemaVersion 变化时会被丢弃并回到默认值（绝不带着半截状态运行）。 */
@Serializable
data class SelfTuneState(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val mean: List<Double>,
    val sigma: Double,
    val bestX: List<Double>,
    /**
     * 历史最好适应度；**null 表示"还没有"**（等价于内存里的 +∞）。
     *
     * 为什么要用可空而不是直接存 Double.POSITIVE_INFINITY：JSON 规范不允许无穷大，
     * kotlinx.serialization 默认也会拒绝（allowSpecialFloatingPointValues = false）——
     * 于是"第一次保存"必然抛异常、学习状态从来没落盘过。这是单元测试抓出来的真实缺陷。
     */
    val bestFitness: Double? = null,
    val incumbent: List<Double>,
    val windows: Int,
    /** 冷却期：安全阀触发后暂停学习若干窗口。 */
    val cooldownWindows: Int = 0,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

/** 状态的读写与容错。任何解析失败都返回 null，由调用方回到默认值。 */
object SelfTuneCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(s: SelfTuneState): String = json.encodeToString(SelfTuneState.serializer(), s)

    /** 解析失败、schema 不认识、维度看起来不对 —— 一律返回 null（调用方回默认值）。 */
    fun decode(text: String?): SelfTuneState? {
        if (text.isNullOrBlank()) return null
        val s = runCatching { json.decodeFromString(SelfTuneState.serializer(), text) }.getOrNull() ?: return null
        if (s.schemaVersion != SelfTuneState.CURRENT_SCHEMA) return null
        if (s.mean.size != Tunables.all.size) return null
        if (s.bestX.size != Tunables.all.size) return null
        if (s.incumbent.size != Tunables.all.size) return null
        if (!s.sigma.isFinite() || s.sigma <= 0.0) return null
        // 允许 null（表示还没有历史最好），但不允许非有限值（JSON 也不该出现无穷大）
        if (s.bestFitness != null && !s.bestFitness.isFinite()) return null
        return s
    }

    fun load(store: SelfTuneStore): SelfTuneState? = decode(runCatching { store.read() }.getOrNull())

    /**
     * 保存状态，返回是否成功。
     * **不要把失败吞掉**：早先这里写成 `runCatching { store.write(encode(s)) }` 而不返回结果，
     * 结果编码异常被静默吃掉、状态根本没落盘，是单元测试（"应已写入状态"断言失败）抓出来的。
     */
    fun save(store: SelfTuneStore, s: SelfTuneState): Boolean =
        runCatching { store.write(encode(s)) }.isSuccess
}
