package com.tricomix.jm.data.prefs

/**
 * 阅读模式（从 app 模块移到共享层，桌面端复用同一套语义）。
 *
 * 移动的理由：Android 用 HorizontalPager 实现 Page、桌面端也要用同一个控件做同一件事，
 * 枚举定义在 app 里桌面端就看不到，复制一份又会两边分叉。包名保持不变，app 端无需改动。
 */
enum class ReaderMode {
    /** 纵向连续滚动。 */
    Scroll,

    /** 横向逐页翻动，每页适配整屏。 */
    Page,
}
