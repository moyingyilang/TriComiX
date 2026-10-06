package com.tricomix.jm.selftune

/**
 * 一次翻页的观测样本（由各端在阅读页采集）。
 *
 * 只采计数与耗时，不采内容：全部留在本机，不涉及任何上传。
 */
data class PageSample(
    /** 从翻到该页到图片可见的耗时（毫秒）；命中预取时接近 0。 */
    val latencyMs: Long,
    /** 该页实际下载的字节数（命中缓存为 0）。 */
    /**
     * 该页实际下载的字节数（命中缓存为 0）。**`null` 表示"这一端拿不到字节数"**，不是 0：
     * Android 用 Coil 显示图片，其公开钩子（`SuccessResult` 与 `EventListener`）都拿不到下载字节数
     * （`FetchResult` 是空标记接口），所以 Android 传 null。
     * 全部样本都为 null 时，适应度会**把字节这一项的权重丢掉**，只用延迟/未命中/失败来比较 ——
     * 若把"未知"当成 0，这一端会显得"无限省流量"从而支配评分，那是错的。
     */
    val bytes: Long? = null,
    /** 该页是否命中缓存（预取或历史）。 */
    val hitCache: Boolean,
    /** 该页是否失败或被打断（取消不算失败，单独统计）。 */
    val failed: Boolean,
    /** 用户在该页停留了多久（毫秒），用于判断"快翻"还是"慢读"。 */
    val dwellMs: Long,
)

/**
 * 一个评估窗口内累积的指标。
 *
 * 窗口是算法的基本单位：窗口内**不改变参数**，窗口结束时才算适应度、才决定是否采纳新参数
 * —— 避免同一页行为不一致，也避免拿半截数据下结论。
 */
class WindowMetrics {
    private val latencies = ArrayList<Long>()
    private val dwells = ArrayList<Long>()
    private var bytesSum = 0L
    /** 已知字节数的样本条数；为 0 时适应度不把字节计入（见 PageSample.bytes 的注释）。 */
    private var bytesKnown = 0
    private var misses = 0
    private var failures = 0
    private var cancellations = 0

    val pages: Int get() = latencies.size

    fun record(s: PageSample) {
        latencies.add(s.latencyMs)
        dwells.add(s.dwellMs)
        s.bytes?.let { bytesSum += it; bytesKnown++ }
        if (!s.hitCache) misses++
        if (s.failed) failures++
    }

    /** 协程取消单独计数：它既不是失败也不是命中，用于安全阀判断。 */
    fun recordCancellation() { cancellations++ }

    fun medianLatencyMs(): Long = median(latencies)
    fun medianDwellMs(): Long = median(dwells)
    fun bytes(): Long = bytesSum

    /** 这一窗口里有多少条样本带真值字节数；0 表示整窗未知。 */
    fun bytesKnownCount(): Int = bytesKnown
    fun misses(): Int = misses
    fun failures(): Int = failures
    fun cancellations(): Int = cancellations

    fun isEmpty(): Boolean = latencies.isEmpty()

    /** 清空累积。窗口结算后必须调用，否则下一个窗口会带着上一个窗口的数据。 */
    fun reset() {
        latencies.clear()
        dwells.clear()
        bytesSum = 0L
        bytesKnown = 0
        misses = 0
        failures = 0
        cancellations = 0
    }

    private fun median(xs: List<Long>): Long {
        if (xs.isEmpty()) return 0
        val s = xs.sorted()
        return s[s.size / 2]
    }
}
