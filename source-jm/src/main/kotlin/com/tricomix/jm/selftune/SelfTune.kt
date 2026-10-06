package com.tricomix.jm.selftune

/** 一次窗口结束后的结果，便于调用方与日志区分（不要用布尔值糊在一起）。 */
sealed class WindowOutcome {
    /** 空窗口：不参与评估（避免被当成 0 分最优）。 */
    object Skipped : WindowOutcome()

    /** 挑战者没赢过现任，保持现任参数。 */
    object Kept : WindowOutcome()

    /** 挑战者赢了并生效。 */
    data class Promoted(val params: ParamValues, val cost: Double, val incumbentCost: Double) : WindowOutcome()

    /** 安全阀触发：回退默认参数并进入冷却。 */
    data class RolledBack(val reason: String) : WindowOutcome()

    /** 冷却期内，窗口只统计不学习。 */
    object CoolingDown : WindowOutcome()

    /** 一代结束（仅用于日志）。 */
    data class GenerationAdvanced(val generation: Int, val bestCost: Double) : WindowOutcome()
}

/**
 * 本机自学习调参的编排器。放在 shared，Android 与桌面共用同一份。
 *
 * 在线评估的硬约束：**一个窗口只能评估一个配置**（没法对同一段真实使用做反事实评估）。
 * 因此一代 = 1 + lambda 个窗口：
 *   - 第 0 个窗口评估**现任参数**（用于同一代内的公平比较，避免跨时段噪声导致误采纳）；
 *   - 之后 lambda 个窗口各评估一个采样候选（lambda 为偶数，保证对偶采样）；
 *   - 一代结束时用这 lambda 个候选的适应度更新搜索分布。
 *
 * 安全阀：窗口内失败与取消占比超过 [failureRateLimit] 时，立即回退到默认参数（即当前手工调好的值），
 * 并进入 [cooldownWindows] 个窗口的冷却期（只统计、不学习）。宁可不变，不能变坏。
 *
 * 调用方式（各端都一样）：
 *   val tune = SelfTune(store = 平台实现, logger = { 日志 })
 *   ... 每页结束时：val outcome = tune.onPage(PageSample(...))   // 非 null 表示窗口结束
 *   ... 窗口开始时读取 tune.params() 应用参数
 */
class SelfTune(
    private val store: SelfTuneStore? = null,
    val enabled: Boolean = true,
    private val logger: (String) -> Unit = {},
    private val specs: List<TunableSpec> = Tunables.all,
    private val scorer: FitnessScorer = FitnessScorer(),
    private val windowSize: Int = 20,
    private val sampledPerGeneration: Int = 6,
    private val promotionMargin: Double = 0.05,
    private val failureRateLimit: Double = 0.25,
    private val cooldownWindows: Int = 3,
    seed: Long = NesCore.DEFAULT_SEED,
) {
    init {
        require(windowSize > 0) { "窗口大小必须为正" }
        require(sampledPerGeneration >= 2 && sampledPerGeneration % 2 == 0) { "每代候选数必须是 >=2 的偶数" }
    }

    private val metrics = WindowMetrics()
    private val defaults = ParamValues.defaults(specs)
    private val core = NesCore(dim = specs.size, initialMean = defaults.normalized(), seed = seed)

    /** 当前生效参数（应用实际使用的）。 */
    var effectiveParams: ParamValues = defaults
        private set

    private var incumbentX: DoubleArray = defaults.normalized()
    private var incumbentCost: Double = Double.NaN
    private var genCandidates: List<DoubleArray> = emptyList()
    private var genFitness: DoubleArray = DoubleArray(0)
    private var genIdx: Int = 0
    private var generation: Int = 0
    private var cooldown: Int = 0
    private var windowsDone: Int = 0

    /** 本窗口要用的参数（窗口中途不会变）。 */
    private var windowParams: ParamValues = defaults

    /** 只提示一次，避免每个窗口都刷同样的日志。 */
    private var warnedNoStore = false
    private var warnedSaveFailed = false

    init {
        // 恢复上次的学习状态；任何异常都回默认值（绝不带半截状态运行）
        val saved = store?.let { SelfTuneCodec.load(it) }
        if (saved != null) {
            runCatching {
                core.restore(
                    saved.mean.toDoubleArray(),
                    saved.sigma,
                    saved.bestX.toDoubleArray(),
                    saved.bestFitness ?: Double.POSITIVE_INFINITY,   // 状态里 null 表示"还没有"
                )
                effectiveParams = ParamValues.fromNormalized(saved.incumbent.toDoubleArray(), specs)
                incumbentX = saved.incumbent.toDoubleArray()
                cooldown = saved.cooldownWindows.coerceAtLeast(0)
                windowsDone = saved.windows.coerceAtLeast(0)
                logger("SELFTUNE 已恢复状态：窗口 $windowsDone，现任 $effectiveParams")
            }.onFailure {
                effectiveParams = defaults
                incumbentX = defaults.normalized()
                logger("SELFTUNE 状态恢复失败，回默认值：${it.message}")
            }
        }
        startGeneration()
    }

    /** 应用读取它来配置自身（禁用时永远返回默认值）。 */
    fun params(): ParamValues = if (enabled) windowParams else defaults

    /** 供日志与测试查看的内部量。 */
    fun stats(): Stats = Stats(
        windows = windowsDone,
        generation = generation,
        sigma = core.sigma,
        bestFitness = core.bestFitness,
        meanNormalized = core.mean.copyOf(),
        elite = ParamValues.fromNormalized(core.bestX, specs),
        effective = effectiveParams,
        cooldown = cooldown,
    )

    data class Stats(
        val windows: Int,
        val generation: Int,
        val sigma: Double,
        val bestFitness: Double,
        val meanNormalized: DoubleArray,
        val elite: ParamValues,
        val effective: ParamValues,
        val cooldown: Int,
    )

    /**
     * 记一页。窗口满时自动结算并返回结果（调用方据此决定是否重新读取 [params]）。
     */
    fun onPage(sample: PageSample): WindowOutcome? {
        metrics.record(sample)
        if (metrics.pages < windowSize) return null
        val outcome = settleWindow()
        return outcome
    }

    /** 记一次协程取消（不算失败，但要计入安全阀）。 */
    fun onCancellation() { metrics.recordCancellation() }

    private fun settleWindow(): WindowOutcome {
        val cost = scorer.cost(metrics)
        val habit = scorer.habit(metrics)
        val pages = metrics.pages
        val failRate = (metrics.failures() + metrics.cancellations()).toDouble() / pages.coerceAtLeast(1)
        val summary = "窗口 ${windowsDone + 1}：页数 $pages，中位延迟 ${metrics.medianLatencyMs()}ms，" +
            "中位停留 ${metrics.medianDwellMs()}ms（$habit），每页 ${metrics.bytes() / pages.coerceAtLeast(1)}B，" +
            "命中率 ${1.0 - metrics.misses().toDouble() / pages.coerceAtLeast(1)}，失败率 $failRate，适应度 $cost"
        logger("SELFTUNE $summary")
        metrics.reset()
        windowsDone++

        // 安全阀优先于学习：失败率过高立刻回退默认参数并冷却
        if (failRate > failureRateLimit) {
            effectiveParams = defaults
            incumbentX = defaults.normalized()
            incumbentCost = Double.NaN
            cooldown = cooldownWindows
            save()
            val reason = "失败率 $failRate 超过上限 $failureRateLimit"
            logger("SELFTUNE 安全阀触发：$reason，回退默认参数并冷却 $cooldownWindows 个窗口")
            startGeneration()
            return WindowOutcome.RolledBack(reason)
        }
        if (cooldown > 0) {
            cooldown--
            save()
            logger("SELFTUNE 冷却中（剩余 $cooldown 个窗口），本窗口不学习")
            advanceWindow()
            return WindowOutcome.CoolingDown
        }
        if (cost.isNaN()) {
            advanceWindow()
            return WindowOutcome.Skipped
        }

        val outcome: WindowOutcome
        if (genIdx == 0) {
            // 本代第 0 个窗口：测量现任参数，作为本代比较基准
            incumbentCost = cost
            logger("SELFTUNE 本代基准：现任 $effectiveParams 适应度 $cost")
            outcome = WindowOutcome.Kept
        } else {
            genFitness[genIdx - 1] = cost
            val candidate = ParamValues.fromNormalized(genCandidates[genIdx - 1], specs)
            if (!incumbentCost.isNaN() && cost < incumbentCost * (1.0 - promotionMargin)) {
                effectiveParams = candidate
                incumbentX = genCandidates[genIdx - 1].copyOf()
                logger("SELFTUNE 采纳挑战者：$candidate 适应度 $cost 优于现任基准 $incumbentCost")
                outcome = WindowOutcome.Promoted(candidate, cost, incumbentCost)
            } else {
                logger("SELFTUNE 保持现任：挑战者 $candidate 适应度 $cost 未超过基准 $incumbentCost（需低 $promotionMargin 以上）")
                outcome = WindowOutcome.Kept
            }
        }
        save()
        advanceWindow()
        return outcome
    }

    /** 推进窗口计数；一代结束时更新搜索分布并开新的一代。 */
    private fun advanceWindow() {
        genIdx++
        if (genIdx > sampledPerGeneration) {
            val valid = genFitness.filter { !it.isNaN() }
            if (valid.size >= 2) {
                val step = core.update(genCandidates, genFitness)
                generation++
                logger("SELFTUNE 一代结束：第 $generation 代，样本 ${valid.size} 个，最好 ${valid.min()}，均值位移上限 $step，步长 ${core.sigma}")
            }
            startGeneration()
        } else {
            prepareWindow()
        }
    }

    private fun startGeneration() {
        genCandidates = core.sample(sampledPerGeneration)
        genFitness = DoubleArray(genCandidates.size) { Double.NaN }
        genIdx = 0
        prepareWindow()
    }

    private fun prepareWindow() {
        windowParams = if (genIdx == 0) effectiveParams else ParamValues.fromNormalized(genCandidates[genIdx - 1], specs)
    }

    /**
     * 保存状态。**两条失败路径都必须说话**：没有配置存储、以及写入失败。
     * 起因：早先写法是 `val s = store ?: return` 加一个不检查返回值 save，结果保存一直没成功，
     * 而日志里一点痕迹都没有 —— 是单元测试的"应已写入状态"断言把它抓出来的。
     */
    private fun save() {
        val s = store
        if (s == null) {
            if (!warnedNoStore) {
                warnedNoStore = true
                logger("SELFTUNE 未配置状态存储：学习结果只在本进程内有效，重启后从头开始")
            }
            return
        }
        val ok = SelfTuneCodec.save(
            s,
            SelfTuneState(
                mean = core.mean.toList(),
                sigma = core.sigma,
                bestX = core.bestX.toList(),
                bestFitness = core.bestFitness.takeIf { it.isFinite() },   // 无穷大不能进 JSON
                incumbent = effectiveParams.normalized().toList(),
                windows = windowsDone,
                cooldownWindows = cooldown,
            ),
        )
        if (!ok && !warnedSaveFailed) {
            warnedSaveFailed = true
            logger("SELFTUNE 状态保存失败（存储写入抛了异常）：本次学习结果未落盘")
        }
    }
}
