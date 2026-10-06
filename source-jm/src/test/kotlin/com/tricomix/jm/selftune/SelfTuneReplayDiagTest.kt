package com.tricomix.jm.selftune

import org.junit.Test

/** 诊断：把两种停留场景下"不同预取深度的成本"直接算出来，看算法该往哪个方向走。 */
class SelfTuneReplayDiagTest {

    private fun trace(): List<Pair<Long, Long>> {
        val text = javaClass.getResourceAsStream("/selftune-trace.csv")!!
            .bufferedReader().use { it.readText() }
        return text.lines().filter { it.isNotBlank() && it.contains(',') }.map { line ->
            val (b, ms) = line.split(',')
            b.trim().toLong() to ms.trim().toLong()
        }
    }

    @Test
    fun `诊断_不同深度在不同停留下的成本`() {
        val rows = trace()
        for (dwell in longArrayOf(150, 400, 1000, 4000)) {
            val sb = StringBuilder("深度扫描 dwell=${dwell}ms：")
            for (depth in intArrayOf(2, 3, 4, 6, 9, 12)) {
                var latSum = 0L
                var miss = 0
                for (j in 0 until 150) {
                    val (_, dl) = rows[j]
                    val lead = minOf(depth, j) * dwell
                    val need = dl
                    val lat = if (lead >= need) 0L else (need - lead) + dl
                    latSum += lat
                    if (lat == 0L) miss++
                }
                val waste = (0 until depth).sumOf { rows.getOrNull(minOf(150, rows.size - 1) + it)?.first ?: 0L } / 150
                sb.append(" d=$depth:中位延迟=${latSum / 150}ms,命中=$miss/150,浪费=${waste / 1024}KB |")
            }
            println(sb.toString())
        }
    }
}
