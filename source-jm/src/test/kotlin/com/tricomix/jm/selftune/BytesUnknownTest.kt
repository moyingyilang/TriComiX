package com.tricomix.jm.selftune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "字节数未知"（Android 用 Coil 拿不到下载字节数）不该让适应度失真。
 *
 * 说准这条性质：丢掉字节权重与"把未知当成 0 字节"在**单个窗口的代价上是数值等价的**
 * （都是 0 项）。真正让评分成立的是**同一台设备上样本一致** —— 桌面端每个样本都有字节、
 * Android 端每个样本都没有，所以比较始终在同一维度上进行，不会出现"某端因为缺字节而显得更省流量"。
 * 下面把这两条都钉住，避免以后有人把未知当成"0 字节的好处"去解读。
 */
class BytesUnknownTest {
    private fun window(latencyMs: Long, dwellMs: Long, bytes: Long?, n: Int = 20): WindowMetrics {
        val m = WindowMetrics()
        repeat(n) {
            m.record(
                PageSample(latencyMs = latencyMs, bytes = bytes, hitCache = false, failed = false, dwellMs = dwellMs),
            )
        }
        return m
    }

    @Test
    fun `字节全部未知时，排序完全由延迟决定`() {
        val scorer = FitnessScorer()
        val fast = scorer.cost(window(latencyMs = 120, dwellMs = 800, bytes = null))
        val slow = scorer.cost(window(latencyMs = 2000, dwellMs = 800, bytes = null))
        assertTrue("延迟低的窗口代价应更低（fast=$fast slow=$slow）", fast < slow)
    }

    @Test
    fun `未知与零字节在单窗口代价上等价（所以不能把未知解读成"更省流量"）`() {
        val scorer = FitnessScorer()
        val unknown = scorer.cost(window(latencyMs = 1000, dwellMs = 2000, bytes = null))
        val zero = scorer.cost(window(latencyMs = 1000, dwellMs = 2000, bytes = 0L))
        assertEquals("未知与 0 字节应得到同一代价", zero, unknown, 1e-9)
    }

    @Test
    fun `同一窗口内已知字节数仍按真实值计入`() {
        val scorer = FitnessScorer()
        val light = scorer.cost(window(latencyMs = 1000, dwellMs = 2000, bytes = 100_000L))
        val heavy = scorer.cost(window(latencyMs = 1000, dwellMs = 2000, bytes = 5_000_000L))
        assertTrue("延迟/停留相同时，字节多的代价应更高（light=$light heavy=$heavy）", light < heavy)
    }

    @Test
    fun `字节已知条数为零时该窗口不依赖字节项`() {
        val m = window(latencyMs = 500, dwellMs = 1500, bytes = null)
        assertEquals(0, m.bytesKnownCount())
        assertEquals(0L, m.bytes())
        val known = window(latencyMs = 500, dwellMs = 1500, bytes = 42L)
        assertEquals(20, known.bytesKnownCount())
        assertEquals(840L, known.bytes())
    }
}
