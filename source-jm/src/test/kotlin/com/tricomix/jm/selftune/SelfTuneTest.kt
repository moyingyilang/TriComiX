package com.tricomix.jm.selftune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 测试用的内存存储，模拟平台侧的读写。 */
private class MemoryStore(var text: String? = null) : SelfTuneStore {
    override fun read(): String? = text
    override fun write(text: String) { this.text = text }
}

class SelfTuneTest {

    private fun feed(
        tune: SelfTune,
        pages: Int,
        latencyMs: Long = 200,
        bytes: Long = 300_000,
        hit: Boolean = true,
        failed: Boolean = false,
        dwellMs: Long = 500,
    ): WindowOutcome? {
        var last: WindowOutcome? = null
        repeat(pages) {
            last = tune.onPage(PageSample(latencyMs = latencyMs, bytes = bytes, hitCache = hit, failed = failed, dwellMs = dwellMs))
        }
        return last
    }

    @Test
    fun `参数越界会被夹回范围`() {
        val v = ParamValues.of(doubleArrayOf(99.0, 0.0, -5.0, 99_999.0))
        assertEquals(12.0, v[Tunables.prefetchDepth], 0.0)
        assertEquals(1.0, v[Tunables.prefetchConcurrency], 0.0)
        assertEquals(64.0, v[Tunables.cacheBudgetMB], 0.0)
        assertEquals(3000.0, v[Tunables.retryBackoffMs], 0.0)
        // 默认值必须落在范围内，否则冷启动就不安全
        for (s in Tunables.all) assertTrue("${s.name} 默认值越界", s.default in s.lo..s.hi)
    }

    @Test
    fun `习惯档按中位停留时长判定且权重方向正确`() {
        val scorer = FitnessScorer()
        val quick = WindowMetrics().apply { repeat(5) { record(sample(dwell = 400)) } }
        val slow = WindowMetrics().apply { repeat(5) { record(sample(dwell = 8000)) } }
        val mid = WindowMetrics().apply { repeat(5) { record(sample(dwell = 2000)) } }
        assertEquals(Habit.QUICK, scorer.habit(quick))
        assertEquals(Habit.SLOW, scorer.habit(slow))
        assertEquals(Habit.NEUTRAL, scorer.habit(mid))
        assertTrue("快翻应更看重延迟", scorer.weights(Habit.QUICK).latency > scorer.weights(Habit.QUICK).bytes)
        assertTrue("慢读应更看重带宽", scorer.weights(Habit.SLOW).bytes > scorer.weights(Habit.SLOW).latency)
        // 失败项在任何习惯档下都最高，安全底线不能被抵消
        for (h in Habit.values()) {
            val w = scorer.weights(h)
            assertTrue("$h 的失败权重应最高", w.failure > w.latency && w.failure > w.bytes)
        }
    }

    private fun sample(dwell: Long) =
        PageSample(latencyMs = 500, bytes = 200_000, hitCache = true, failed = false, dwellMs = dwell)

    @Test
    fun `空窗口适应度为 NaN 而不参与评估`() {
        assertTrue(FitnessScorer().cost(WindowMetrics()).isNaN())
    }

    @Test
    fun `挑战者没赢过现任时不采纳`() {
        val tune = SelfTune(store = null, windowSize = 3, logger = {})
        // 第 0 个窗口测现任：延迟低、无失败 → 基准很好
        assertTrue(feed(tune, 3, latencyMs = 100) is WindowOutcome.Kept)
        val before = tune.effectiveParams.toString()
        // 第 1 个窗口测候选：延迟高、有失败 → 不该被采纳
        feed(tune, 3, latencyMs = 3000, bytes = 2_000_000, hit = false, failed = true)
        assertEquals("参数不该被改", before, tune.effectiveParams.toString())
    }

    @Test
    fun `挑战者明显更好时被采纳`() {
        val tune = SelfTune(store = null, windowSize = 3, logger = {})
        // 现任窗口：慢、丢包
        feed(tune, 3, latencyMs = 4000, bytes = 3_000_000, hit = false, failed = false)
        // 候选窗口：又快又省 → 应被采纳
        val outcome = feed(tune, 3, latencyMs = 50, bytes = 50_000, hit = true, failed = false)
        assertTrue("应采纳挑战者，实际 $outcome", outcome is WindowOutcome.Promoted)
    }

    @Test
    fun `失败率超限立刻回退默认并进入冷却`() {
        val tune = SelfTune(store = null, windowSize = 4, logger = {})
        val outcome = feed(tune, 4, failed = true)   // 失败率 100% > 25%
        assertTrue("应触发安全阀，实际 $outcome", outcome is WindowOutcome.RolledBack)
        assertEquals(
            "应回退到默认参数",
            ParamValues.defaults().toString(),
            tune.effectiveParams.toString(),
        )
        val next = feed(tune, 4, latencyMs = 100, failed = false)
        assertTrue("冷却期内不应学习，实际 $next", next is WindowOutcome.CoolingDown)
    }

    @Test
    fun `损坏的状态一律回默认值`() {
        assertNull(SelfTuneCodec.decode(null))
        assertNull(SelfTuneCodec.decode("这不是 JSON"))
        assertNull(SelfTuneCodec.decode("""{"schemaVersion":99,"mean":[],"sigma":0.1,"bestX":[],"bestFitness":1.0,"incumbent":[],"windows":5}"""))
        val tune = SelfTune(store = MemoryStore("垃圾内容"), windowSize = 3, logger = {})
        assertEquals("损坏状态应回到 0 个窗口", 0, tune.stats().windows)
        assertEquals(ParamValues.defaults().toString(), tune.effectiveParams.toString())
    }

    @Test
    fun `窗口中途参数不变且窗口结束后才可能切换`() {
        val tune = SelfTune(store = null, windowSize = 4, logger = {})
        val p0 = tune.params().toString()
        tune.onPage(PageSample(100, 1000, true, false, 500))
        assertEquals("窗口内不应变", p0, tune.params().toString())
        feed(tune, 4, latencyMs = 100)   // 填满窗口
        assertNotNull(tune.stats())
    }

    @Test
    fun `状态可保存并可恢复`() {
        val store = MemoryStore()
        val a = SelfTune(store = store, windowSize = 3, logger = {})
        repeat(2) { feed(a, 3, latencyMs = 300) }
        val windowsA = a.stats().windows
        val meanA = a.stats().meanNormalized.toList()
        assertNotNull("应已写入状态", store.text)
        val b = SelfTune(store = store, windowSize = 3, logger = {})
        assertEquals(windowsA, b.stats().windows)
        assertEquals(meanA, b.stats().meanNormalized.toList())
    }
}
