package com.tricomix.jm.selftune

/**
 * 一个可调参数的定义：名字、范围、默认值。
 *
 * 硬边界是安全底线：算法只能在这个区间内移动，越界一律夹回。
 * 默认值必须是**当前手工调好的值**，这样刚装上或升级后的行为与现在完全一致，
 * 不会因为算法还没收敛而变差（见 SELFTUNE.md 的"冷启动"一节）。
 */
data class TunableSpec(
    val name: String,
    val lo: Double,
    val hi: Double,
    val default: Double,
    val integer: Boolean = false,
) {
    init {
        require(lo < hi) { "参数 $name 的范围不合法：lo=$lo hi=$hi" }
        require(default in lo..hi) { "参数 $name 的默认值 $default 不在 [$lo, $hi] 内" }
    }

    /** 夹到范围内；整数参数还会取整。 */
    fun clamp(v: Double): Double {
        val c = v.coerceIn(lo, hi)
        return if (integer) Math.round(c).toDouble() else c
    }

    /** 归一化到 [0,1]。 */
    fun normalize(v: Double): Double = ((clamp(v) - lo) / (hi - lo)).coerceIn(0.0, 1.0)

    /** 从 [0,1] 还原为业务值（并夹住、取整）。 */
    fun denormalize(x: Double): Double = clamp(lo + x.coerceIn(0.0, 1.0) * (hi - lo))
}

/** 全部可调参数（顺序即 [ParamValues.raw] 的下标顺序）。 */
object Tunables {
    /** 预取提前量：翻页快的人受益，慢慢看的人浪费带宽。 */
    val prefetchDepth = TunableSpec("prefetchDepth", lo = 2.0, hi = 12.0, default = 6.0, integer = true)

    /** 预取并发：串行保证近处优先，并发提高吞吐但会挤占正在看的那页的带宽。 */
    val prefetchConcurrency = TunableSpec("prefetchConcurrency", lo = 1.0, hi = 3.0, default = 1.0, integer = true)

    /** 图片缓存上限（MB）：内存紧的设备应调小。 */
    val cacheBudgetMB = TunableSpec("cacheBudgetMB", lo = 64.0, hi = 512.0, default = 256.0, integer = true)

    /** 下载失败后的退避（毫秒）：网络差的人需要更宽容。 */
    val retryBackoffMs = TunableSpec("retryBackoffMs", lo = 200.0, hi = 3000.0, default = 800.0, integer = true)

    val all: List<TunableSpec> = listOf(prefetchDepth, prefetchConcurrency, cacheBudgetMB, retryBackoffMs)

    val index: Map<String, Int> = all.withIndex().associate { (i, s) -> s.name to i }
}

/**
 * 一组参数取值（业务值）。构造时一律按各自范围夹住，所以**不可能**带出越界值。
 */
class ParamValues private constructor(val raw: DoubleArray, val specs: List<TunableSpec>) {

    init {
        require(raw.size == specs.size) { "取值个数与参数个数不一致" }
    }

    operator fun get(spec: TunableSpec): Double = raw[Tunables.index.getValue(spec.name)]

    fun asInt(spec: TunableSpec): Int = get(spec).toInt()

    /** 归一化向量（供算法使用）。 */
    fun normalized(): DoubleArray = DoubleArray(specs.size) { specs[it].normalize(raw[it]) }

    override fun toString(): String =
        specs.withIndex().joinToString(", ") { (i, s) ->
            "${s.name}=" + if (s.integer) raw[i].toInt().toString() else raw[i].toString()
        }

    companion object {
        /** 全部取默认值（冷启动用）。 */
        fun defaults(specs: List<TunableSpec> = Tunables.all): ParamValues =
            ParamValues(DoubleArray(specs.size) { specs[it].default }, specs)

        /** 从业务值构造，越界会被夹住、整数参数会被取整。 */
        fun of(values: DoubleArray, specs: List<TunableSpec> = Tunables.all): ParamValues =
            ParamValues(DoubleArray(specs.size) { specs[it].clamp(values[it]) }, specs)

        /** 从归一化向量构造（算法输出的形式）。 */
        fun fromNormalized(x: DoubleArray, specs: List<TunableSpec> = Tunables.all): ParamValues =
            ParamValues(DoubleArray(specs.size) { specs[it].denormalize(x[it]) }, specs)

        /** 从名字到值的映射构造，缺项用默认值。 */
        fun ofMap(m: Map<String, Double>, specs: List<TunableSpec> = Tunables.all): ParamValues =
            ParamValues(DoubleArray(specs.size) { m[specs[it].name] ?: specs[it].default }, specs)
    }
}
