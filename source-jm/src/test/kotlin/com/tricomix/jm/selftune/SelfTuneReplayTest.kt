package com.tricomix.jm.selftune

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 离线回放：算法能不能上线的那道闸。
 *
 * 用**真实数据**：shared/src/test/resources/selftune-trace.csv 是从真机日志里抽出的
 * 177 条反切片下载样本（字节数, 下载毫秒），网络条件取自同一连续时间段。
 *
 * 用**构造数据**：停留时长。日志里几乎没有停留时长记录（只有 16 行键盘翻页），
 * 所以这里构造两种极端场景（狂点 150ms/页、慢读 4000ms/页），这一点必须在结论里说清。
 *
 * 仿真模型（刻意保持简单、可解释）：
 *   - 页面 j 最多能提前 d 页开始下载（d = 预取深度，c = 预取并发）；
 *   - 因此可用提前时间 lead = min(d, j) * 每页停留；
 *   - 下载它需要 need = 下载毫秒 / c；
 *   - lead >= need 记为命中（延迟约 0），否则用户要等它下完：延迟 = (need - lead) + 下载毫秒；
 *   - 带宽：已看的页各自计费；结尾处被预取但没看到的 d 页字节按窗口均摊计入浪费。
 *
 * 判据（写死，达不到就不上线）：狂点场景最终选出的预取深度应**不小于**慢读场景。
 */
class SelfTuneReplayTest {

    private fun trace(): List<Pair<Long, Long>> {
        val text = javaClass.getResourceAsStream("/selftune-trace.csv")!!
            .bufferedReader().use { it.readText() }
        return text.lines().filter { it.isNotBlank() && it.contains(',') }.map { line ->
            val (b, ms) = line.split(',')
            b.trim().toLong() to ms.trim().toLong()
        }
    }

    private class Sim(val dwellMs: Long, val depth: Int, val concurrency: Int) {
        fun latencyFor(j: Int, downloadMs: Long): Long {
            val lead = minOf(depth, j) * dwellMs
            val need = downloadMs / concurrency
            return if (lead >= need) 0L else (need - lead) + downloadMs
        }
    }

    /** 跑一个场景，返回算法最终生效的参数。 */
    private fun runScenario(dwellMs: Long, trackedPages: Int = 150): ParamValues {
        val rows = trace()
        val tune = SelfTune(store = null, windowSize = 10, logger = {})
        var params = tune.params()
        for (j in 0 until minOf(trackedPages, rows.size)) {
            val (bytes, dl) = rows[j]
            val sim = Sim(dwellMs, params.asInt(Tunables.prefetchDepth), params.asInt(Tunables.prefetchConcurrency))
            val latency = sim.latencyFor(j, dl)
            // 结尾浪费：最后 depth 页被预取但没看到，均摊到已看页
            val waste = (0 until params.asInt(Tunables.prefetchDepth))
                .map { rows.getOrNull(minOf(trackedPages, rows.size - 1) + it)?.first ?: 0L }
                .sum() / trackedPages
            tune.onPage(
                PageSample(
                    latencyMs = latency,
                    bytes = bytes + waste,
                    hitCache = latency == 0L,
                    failed = false,
                    dwellMs = dwellMs,
                )
            )
            params = tune.params()
        }
        return tune.effectiveParams
    }

    @Test
    fun `狂点场景选出的预取深度不小于慢读场景`() {
        val fast = runScenario(dwellMs = 150)
        val slow = runScenario(dwellMs = 4000)
        val dFast = fast.asInt(Tunables.prefetchDepth)
        val dSlow = slow.asInt(Tunables.prefetchDepth)
        println("回放结果：狂点(150ms/页) 深度=$dFast，慢读(4000ms/页) 深度=$dSlow")
        println("回放结果：狂点参数 $fast")
        println("回放结果：慢读参数 $slow")
        assertTrue("狂点场景的预取深度($dFast) 应不小于慢读场景($dSlow)", dFast >= dSlow)
    }
}
