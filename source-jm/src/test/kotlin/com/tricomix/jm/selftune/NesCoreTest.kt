package com.tricomix.jm.selftune

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class NesCoreTest {

    private fun quadratic(x: DoubleArray, opt: DoubleArray, rnd: java.util.Random, noise: Double): Double {
        var s = 0.0
        for (j in x.indices) { val d = x[j] - opt[j]; s += d * d }
        return s + rnd.nextGaussian() * noise
    }

    private fun dist(x: DoubleArray, opt: DoubleArray): Double {
        var s = 0.0
        for (j in x.indices) { val d = x[j] - opt[j]; s += d * d }
        return sqrt(s)
    }

    @Test
    fun `平稳目标：精英参数收敛且均值落在最优附近`() {
        val opt = doubleArrayOf(0.30, 0.70)
        // etaDecay < 1：平稳目标用衰减换收敛保证（在线场景默认不衰减，见下个用例）。
        val core = NesCore(dim = 2, initialMean = doubleArrayOf(0.5, 0.5), etaDecay = 0.97)
        val noise = java.util.Random(7L)
        repeat(300) {
            val cands = core.sample(6)
            val fit = DoubleArray(cands.size) { k -> quadratic(cands[k], opt, noise, 0.001) }
            core.update(cands, fit)
        }
        val dElite = dist(core.bestX, opt)
        val dMean = dist(core.mean, opt)
        // 注意噪声下限：适应度噪声 0.001 意味着约 0.032 的距离差异会被噪声淹没，
        // 所以精英的断言不能比这个更紧，否则是在要求算法分辨噪声。
        assertTrue("精英参数应贴近最优（噪声下限约 0.032）；实际 $dElite，bestX=${core.bestX.toList()}", dElite < 0.06)
        assertTrue("均值应落在最优附近；实际 $dMean，均值=${core.mean.toList()}，步长=${core.sigma}", dMean < 0.10)
        assertTrue("历史最好适应度应接近 0；实际 ${core.bestFitness}", core.bestFitness < 0.005)
    }

    @Test
    fun `在线场景：不衰减时漂移有界且精英仍接近最优`() {
        val opt = doubleArrayOf(0.30, 0.70)
        // 默认 etaDecay = 1.0：目标非平稳（用户习惯会变），要持续适应而不是收敛到一点。
        // 这里要证明的性质不是"收敛"，而是"不发散"，且精英参数依然好用。
        val core = NesCore(dim = 2, initialMean = doubleArrayOf(0.5, 0.5))
        val noise = java.util.Random(7L)
        var maxMeanDist = 0.0
        repeat(300) {
            val cands = core.sample(6)
            val fit = DoubleArray(cands.size) { k -> quadratic(cands[k], opt, noise, 0.001) }
            core.update(cands, fit)
            val d = dist(core.mean, opt)
            if (d > maxMeanDist) maxMeanDist = d
        }
        assertTrue("均值漂移应有界；实际最大距离 $maxMeanDist", maxMeanDist < 0.60)
        assertTrue("精英参数仍应接近最优；实际 ${dist(core.bestX, opt)}", dist(core.bestX, opt) < 0.06)
    }

    /**
     * 诊断用例：只打印轨迹，用来定位收敛问题，不做断言。
     * 输出会被 Gradle 收进测试报告的 system-out。
     */
    @Test
    fun `诊断_打印后验轨迹`() {
        val opt = doubleArrayOf(0.30, 0.70)
        val core = NesCore(dim = 2, initialMean = doubleArrayOf(0.5, 0.5))
        val noise = java.util.Random(7L)
        repeat(60) { gen ->
            val cands = core.sample(6)
            val fit = DoubleArray(cands.size) { k -> quadratic(cands[k], opt, noise, 0.001) }
            core.update(cands, fit)
            if (gen % 5 == 0 || gen == 59) {
                val d = sqrt((core.mean[0] - opt[0]) * (core.mean[0] - opt[0]) + (core.mean[1] - opt[1]) * (core.mean[1] - opt[1]))
                println(
                    "轨迹 gen=${gen + 1} 均值=[%.4f, %.4f] 步长=%.4f 本代最好=%.5f 历史最好=%.5f 距离=%.4f"
                        .format(core.mean[0], core.mean[1], core.sigma, fit.min(), core.bestFitness, d)
                )
            }
        }
    }

    @Test
    fun `相同种子结果完全可复现`() {
        fun run(): List<DoubleArray> {
            val core = NesCore(dim = 3, initialMean = doubleArrayOf(0.2, 0.5, 0.8), seed = 42L)
            val out = ArrayList<DoubleArray>()
            repeat(5) {
                val c = core.sample(4)
                out.addAll(c)
                core.update(c, doubleArrayOf(1.0, 2.0, 3.0, 4.0))
                out.add(core.mean.copyOf())
            }
            return out
        }
        val a = run(); val b = run()
        assertEquals(a.size, b.size)
        for (i in a.indices) assertArrayEquals(a[i], b[i], 0.0)
    }

    @Test
    fun `均值始终留在归一化范围内`() {
        val core = NesCore(dim = 2, initialMean = doubleArrayOf(0.0, 1.0))
        repeat(200) {
            val c = core.sample(6)
            core.update(c, doubleArrayOf(0.0, 100.0, -100.0, 5.0, 6.0, 7.0))
        }
        assertTrue("均值越界：${core.mean.toList()}", core.mean.all { it in 0.0..1.0 })
    }

    @Test
    fun `步长按成功比例收缩放大并被两端夹住`() {
        val core = NesCore(dim = 1, initialMean = doubleArrayOf(0.5))
        repeat(200) { core.adaptSigma(0.0) }
        assertEquals(0.02, core.sigma, 1e-9)
        repeat(400) { core.adaptSigma(1.0) }
        assertEquals(0.30, core.sigma, 1e-9)
    }

    @Test
    fun `更新方向朝适应度更好的候选`() {
        val core = NesCore(dim = 1, initialMean = doubleArrayOf(0.5))
        val before = core.mean[0]
        core.update(listOf(doubleArrayOf(0.8), doubleArrayOf(0.2)), doubleArrayOf(0.0, 1.0))
        assertTrue("均值应朝 0.8 移动；实际 ${core.mean[0]}", core.mean[0] > before)
    }
}
