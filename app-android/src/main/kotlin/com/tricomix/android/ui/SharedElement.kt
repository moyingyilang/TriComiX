package com.tricomix.android.ui

import androidx.compose.ui.Modifier

/**
 * JMNeXt 的共享元素过渡（列表卡片 -> 详情封面）。
 *
 * 懒移植策略下先做**空实现**：不改动任何布局与手势，只让搬迁过来的代码能编译与渲染。
 * 需要时再回来实现真正的过渡动画。
 */
fun Modifier.jmSharedElement(key: Any?): Modifier = this
