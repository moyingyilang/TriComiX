package com.tricomix.jm.selftune

/** 使用习惯档：由本窗口的**中位停留时长**判定，用来决定适应度里各项的权重。 */
enum class Habit { QUICK, NEUTRAL, SLOW }

/** 适应度权重。 */
data class FitnessWeights(
    val latency: Double,
    val bytes: Double,
    val miss: Double,
    val failure: Double,
)

/**
 * 把一个窗口的指标折算成**越小越好**的适应度。
 *
 * 这是"适配不同使用习惯"的具体含义（不是玄学）：
 *  - 快翻的人（中位停留短）主要在等图，所以延迟项权重高、带宽项低；
 *  - 慢读的人（中位停留长）图早就下完了，延迟不再重要，反而预取太深会白下很多页，所以带宽项权重高。
 *
 * 权重数值本身是**设计选择**，不是实测结论；它的正确性由离线回放验证
 * （判据：快翻轨迹上算法应把预取深度调大、慢读轨迹上应调小）。若回放不成立，先改这里。
 *
 * 失败项权重固定最高：任何配置下"出错"都不能被延迟或带宽的优势抵消，这是安全底线。
 */
class FitnessScorer(
    private val quickDwellMs: Long = 1000,
    private val slowDwellMs: Long = 3000,
    private val latencyScaleMs: Double = 1000.0,
    private val bytesScale: Double = 1_000_000.0,
) {

    fun habit(m: WindowMetrics): Habit = when {
        m.medianDwellMs() < quickDwellMs -> Habit.QUICK
        m.medianDwellMs() > slowDwellMs -> Habit.SLOW
        else -> Habit.NEUTRAL
    }

    fun weights(h: Habit): FitnessWeights = when (h) {
        Habit.QUICK -> FitnessWeights(latency = 1.0, bytes = 0.2, miss = 0.5, failure = 3.0)
        Habit.SLOW -> FitnessWeights(latency = 0.3, bytes = 1.0, miss = 0.3, failure = 3.0)
        Habit.NEUTRAL -> FitnessWeights(latency = 0.6, bytes = 0.6, miss = 0.4, failure = 3.0)
    }

    /** 空窗口返回 NaN，明确表示"本窗口不参与评估"，避免被当成 0 分最优。 */
    fun cost(m: WindowMetrics): Double {
        if (m.isEmpty()) return Double.NaN
        val pages = m.pages.toDouble()
        val w = weights(habit(m))
        val latency = (m.medianLatencyMs() / latencyScaleMs).coerceIn(0.0, 5.0)
        val bytesPerPage = (m.bytes() / pages / bytesScale).coerceIn(0.0, 10.0)
        val missRate = m.misses().toDouble() / pages
        val failRate = (m.failures() + m.cancellations()).toDouble() / pages
        // 字节全部未知时（例如 Android 用 Coil 拿不到字节数）把这一项的权重整个丢掉，
        // 而不是把未知当成 0 —— 否则这一端会显得无限省流量从而支配评分。
        val bytesWeight = if (m.bytesKnownCount() > 0) w.bytes else 0.0
        return w.latency * latency + bytesWeight * bytesPerPage + w.miss * missRate + w.failure * failRate
    }
}
