package com.tricomix.jm.selftune

/**
 * 本机自学习调参的核心：一个极小的自然进化策略（NES，对角高斯）。
 *
 * 设计约束见 desktop/SELFTUNE.md：纯 Kotlin、零新依赖、可复现（固定种子）、可单测。
 * 这里只负责"搜索分布、采样、更新"，不涉及任何业务参数与日志。
 *
 * 约定：
 *  - 参数在归一化空间 [0,1] 内搜索，业务值由调用方按 lo/hi 映射；
 *  - 适应度越小越好；
 *  - 只有排名参与更新（排序适应度整形），绝对数值不参与：在线场景噪声大，这样更稳。
 *
 * 放在 shared 是为了**两端共用同一份实现**：Android 端经 `:shared` 使用，
 * 桌面端经 `kotlin.srcDir("../shared/src/main/kotlin")` 直接编译这份源码。
 */
internal class NesCore(
    val dim: Int,
    initialMean: DoubleArray,
    seed: Long = DEFAULT_SEED,
    // 学习率。别按直觉调大：本式是 θ ← θ + (η/(nσ))·Σ u_k ε_k，而 ε_k 是标准正态，
    // 与 σ 无关，因此单代步长约为 1.9η（与 σ 无关）。曾用 0.35，结果单代位移约 0.5（半个参数空间），
    // 均值在边界上乱撞、σ 缩到下限，300 代后距离最优 0.76 —— 由单元测试的逐代轨迹量出来的。
    eta: Double = 0.02,
    // 学习率每代乘这个系数。1.0 表示不衰减（应用在线场景用：目标非平稳，需要持续适应）；
    // 小于 1 用于平稳目标求收敛（单元测试用它证明"能收敛到最优"）。
    private val etaDecay: Double = 1.0,
    sigmaInit: Double = 0.15,
    private val sigmaMin: Double = 0.02,
    private val sigmaMax: Double = 0.30,
) {
    init {
        require(dim > 0) { "维度必须为正" }
        require(initialMean.size == dim) { "初始均值长度必须等于维度" }
    }

    /** 搜索分布的均值（归一化空间）。 */
    val mean: DoubleArray = initialMean.copyOf()

    /** 每维步长（各维相同，简化实现）。 */
    var sigma: Double = sigmaInit.coerceIn(sigmaMin, sigmaMax)
        private set

    /** 到目前为止见过的最好适应度（越小越好），用于 1/5 成功规则。 */
    var bestFitness: Double = Double.POSITIVE_INFINITY
        private set

    /**
     * 取得最好适应度时的那组参数（elite）。
     * 调用方应当用**它**，而不是直接用带抖动的 [mean]：均值只是搜索中心，
     * 恒定学习率下它会在最优点附近持续随机游走（单测轨迹里 gen=11 到最优距离 0.013，
     * gen=60 又漂到 0.205），而 elite 始终是"找到过的最好那组"。
     */
    var bestX: DoubleArray = initialMean.copyOf()
        private set

    /** 当前学习率（每代按 etaDecay 衰减；默认不衰减）。 */
    private var etaNow: Double = eta

    /**
     * 从持久化状态恢复（进程重启后继续学习）。
     * 只接受形状与取值都合法的输入，否则抛异常由调用方回默认值。
     */
    fun restore(meanValues: DoubleArray, sigmaValue: Double, bestXValue: DoubleArray, bestFitnessValue: Double) {
        require(meanValues.size == dim) { "恢复的均值维度不对" }
        require(bestXValue.size == dim) { "恢复的精英维度不对" }
        require(sigmaValue.isFinite() && sigmaValue > 0.0) { "恢复的步长非法：$sigmaValue" }
        for (j in 0 until dim) {
            mean[j] = meanValues[j].coerceIn(0.0, 1.0)
            bestX[j] = bestXValue[j].coerceIn(0.0, 1.0)
        }
        sigma = sigmaValue.coerceIn(sigmaMin, sigmaMax)
        bestFitness = bestFitnessValue
    }

    private val rnd = java.util.Random(seed)

    /** 抽一代候选：lambda 个向量，两两对偶（θ+σε 与 θ-σε）；越界按镜像折回。 */
    fun sample(lambda: Int): List<DoubleArray> {
        require(lambda >= 2 && lambda % 2 == 0) { "lambda 必须是 >=2 的偶数（对偶采样）" }
        val out = ArrayList<DoubleArray>(lambda)
        repeat(lambda / 2) {
            val eps = DoubleArray(dim) { rnd.nextGaussian() }
            out.add(shift(eps, 1.0))
            out.add(shift(eps, -1.0))
        }
        return out
    }

    private fun shift(eps: DoubleArray, sign: Double): DoubleArray =
        DoubleArray(dim) { j -> reflect(mean[j] + sign * sigma * eps[j]) }

    /**
     * 越界时按镜像折回，而不是夹住。
     * 夹住会让边界处大量候选堆在同一点、破坏对偶采样的对称性，使更新产生系统性偏移
     * —— 这是单元测试抓到的第二个问题（收敛测试里第 0 维跑反了方向）。
     */
    private fun reflect(x: Double): Double {
        var v = x
        var guard = 0
        while ((v < 0.0 || v > 1.0) && guard < 8) {
            v = if (v < 0.0) -v else 2.0 - v
            guard++
        }
        return v.coerceIn(0.0, 1.0)
    }

    /**
     * 用一代候选的适应度更新搜索分布（越小越好），返回本次更新的最大位移（归一化空间）。
     * 位移回传是为了能在日志里看到"这一步动了多少"，避免算法悄悄漂移。
     */
    fun update(candidates: List<DoubleArray>, fitness: DoubleArray): Double {
        require(candidates.size == fitness.size) { "候选数与适应度数不一致" }
        require(candidates.size >= 2) { "至少需要两个候选" }
        val n = candidates.size
        // 本代均值：更新过程中 mean 会被改写，而 eps 必须相对"抽样时的均值"计算，
        // 否则后处理的候选偏离量算错，更新方向被污染
        // —— 这是单元测试抓到的第三个问题。
        val theta0 = mean.copyOf()
        val order = fitness.indices.sortedBy { fitness[it] }
        val u = DoubleArray(n)
        for ((rank, idx) in order.withIndex()) {
            u[idx] = 0.5 - rank.toDouble() / (n - 1)   // 最好者 +0.5，最差者 -0.5
        }
        var maxStep = 0.0
        for (k in 0 until n) {
            if (u[k] == 0.0) continue
            for (j in 0 until dim) {
                val eps = (candidates[k][j] - theta0[j]) / sigma
                val step = (etaNow / (n * sigma)) * u[k] * eps
                mean[j] = (mean[j] + step).coerceIn(0.0, 1.0)
                if (kotlin.math.abs(step) > maxStep) maxStep = kotlin.math.abs(step)
            }
        }
        val genBestIdx = fitness.indices.minBy { fitness[it] }
        val genBest = fitness[genBestIdx]
        val success = genBest < bestFitness
        if (success) {
            bestFitness = genBest
            bestX = candidates[genBestIdx].copyOf()   // elite：记录取得最好适应度的那组参数
        }
        // 1/5 成功规则（修正版）：判据是"本代最好者是否优于历史最好"，
        // 不是"候选是否优于均值"——后者在凸景观上恒约为 0.5、恒大于目标 0.2，
        // 会让步长每代放大直到上限（单元测试抓到的第一个问题：300 代后距离最优 0.31）。
        sigma = (sigma * (if (success) 1.05 else 0.95)).coerceIn(sigmaMin, sigmaMax)
        etaNow *= etaDecay
        return maxStep
    }

    /** 1/5 成功规则的手动版本（供离线实验与单测直接驱动；常规路径由 [update] 内部调用）。 */
    fun adaptSigma(successRate: Double, target: Double = 0.2) {
        val f = if (successRate > target) 1.05 else 0.95
        sigma = (sigma * f).coerceIn(sigmaMin, sigmaMax)
    }

    /** 归一化空间到业务值。 */
    fun denormalize(lo: DoubleArray, hi: DoubleArray, x: DoubleArray = mean): DoubleArray =
        DoubleArray(dim) { j -> lo[j] + x[j].coerceIn(0.0, 1.0) * (hi[j] - lo[j]) }

    /** 业务值到归一化空间（超出范围会被夹住）。 */
    fun normalize(lo: DoubleArray, hi: DoubleArray, v: DoubleArray): DoubleArray =
        DoubleArray(dim) { j -> ((v[j] - lo[j]) / (hi[j] - lo[j])).coerceIn(0.0, 1.0) }

    companion object {
        const val DEFAULT_SEED = 20261004L
    }
}
