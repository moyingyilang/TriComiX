package com.tricomix.pica

import com.tricomix.core.model.ImageQuality
import com.tricomix.core.model.PageRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 档位映射单测（不联网）。
 *
 * PicACG 只有三档，core 有四档，所以映射必须**写死并测到**，否则就是"看起来支持、实际不生效"。
 */
class PicaQualityMappingTest {

    private val source = PicaSource()

    @Test
    fun `四档到三档的映射规则`() {
        assertEquals(PicaImageQuality.LOW, source.toPicaQuality(ImageQuality.LOW))
        assertEquals(PicaImageQuality.NORMAL, source.toPicaQuality(ImageQuality.MEDIUM))
        assertEquals(PicaImageQuality.NORMAL, source.toPicaQuality(ImageQuality.HIGH))
        assertEquals(PicaImageQuality.ORIGINAL, source.toPicaQuality(ImageQuality.ORIGINAL))
    }

    @Test
    fun `HIGH 不会被悄悄升级成原图`() {
        // 原图通常大得多，用户选 HIGH 不代表要原图；这条断言防止将来"顺手优化"改坏语义
        assertEquals(PicaImageQuality.NORMAL, source.toPicaQuality(ImageQuality.HIGH))
    }

    @Test
    fun `取图时档位真的落到客户端（不是死代码）`() {
        val client = PicaClient()
        val s = PicaSource(client)
        val page = PageRef(chapterId = "c|1", index = 0, extra = mapOf("url" to "https://img.example/1.jpg"))

        assertEquals(PicaImageQuality.NORMAL, client.imageQuality, "初始应为默认档")

        runBlocking { s.imageRequest(page, ImageQuality.LOW) }
        assertEquals(PicaImageQuality.LOW, client.imageQuality, "LOW 应生效")

        runBlocking { s.imageRequest(page, ImageQuality.ORIGINAL) }
        assertEquals(PicaImageQuality.ORIGINAL, client.imageQuality, "ORIGINAL 应生效")
    }
}
