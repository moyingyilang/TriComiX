package com.tricomix.jm.selftune

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryStore2(var text: String? = null) : SelfTuneStore {
    override fun read(): String? = text
    override fun write(text: String) { this.text = text }
}

/**
 * 只测"编码与保存本身"，用来把被 runCatching 吞掉的真实异常暴露出来。
 * 起因：SelfTune 的保存一直静默失败（store.text 是 null），而 save 当时不返回结果，
 * 看不到原因；这里直接调用 encode/save，异常会带真实信息报出来。
 */
class SelfTuneCodecTest {

    @Test
    fun `状态编码与保存可用`() {
        val s = SelfTuneState(
            mean = Tunables.all.map { 0.5 },
            sigma = 0.15,
            bestX = Tunables.all.map { 0.5 },
            bestFitness = 1.0,
            incumbent = Tunables.all.map { 0.5 },
            windows = 1,
        )
        val text = SelfTuneCodec.encode(s)
        assertNotNull("编码结果不该为空", text)
        assertNotNull("应能解回", SelfTuneCodec.decode(text))
        val store = MemoryStore2()
        assertTrue("保存应成功", SelfTuneCodec.save(store, s))
        assertNotNull("存储里应有内容", store.text)
        // 回归：JSON 不允许无穷大，历史最好未产生时应写成 null 而不是 Infinity
        assertTrue("编码里不该出现无穷大", !text.contains("Infinity"))
    }
}
