package com.tricomix.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tricomix.android.ui.components.GlassSurface
import com.tricomix.core.source.Capability

/**
 * 侧栏导航（做法搬迁自 `moyingyilang.github.io` 的 `SideNav.astro` + `NavTree.astro` + `utils/nav.ts`）。
 *
 * 站点侧栏有三条值得照搬的做法，这里都落地了：
 * 1. **单一数据源**：导航项集中在一处（[buildNavEntries]），外壳只负责画；
 * 2. **剪空**：源不支持的能力在列表里**消失**（对应站点的 `pruneEmpty` / `HIDE_EMPTY_CATEGORIES`）——
 *    这同时满足本项目"按能力显隐、不给死控件"的硬约束；
 * 3. **当前项高亮 + trail 自动展开**：这里层级只有一层，先落"当前项高亮"；
 *    等将来真出现"分类 → 标签 → 画师"这类层级，再启用站点的递归折叠与 trail 展开。
 *
 * 外壳用 JMNeXt 的 [GlassSurface]，与顶栏质感一致（站点是 `.sidenav.glass`，同一思路）。
 *
 * 只认 `core` 的 `Capability`：条目本身不携带任何图源的专有类型。
 */
data class NavEntry(
    val label: String,
    val route: String,
    /** 该条目要求的能力；为 null 表示始终可用（如"搜索"页签本身、关于页）。 */
    val requires: Capability? = null,
)

/** 导航项的唯一来源（站点 `config/menu.js` 的对应物）。 */
fun buildNavEntries(): List<NavEntry> = listOf(
    NavEntry("首页", "home", Capability.HOME),
    NavEntry("搜索", "search", Capability.SEARCH),
    NavEntry("收藏", "favorites", Capability.FAVORITES),
    NavEntry("历史", "history", Capability.HISTORY),
    NavEntry("关于", "about"),
)

/** 剪空：把源不支持的能力项去掉（站点的 `pruneEmpty`）。 */
fun visibleNavEntries(caps: Set<Capability>): List<NavEntry> =
    buildNavEntries().filter { it.requires == null || caps.contains(it.requires) }

/** 底部栏与侧栏共用的三个页签（同样剪空）。 */
fun visibleTabs(caps: Set<Capability>): List<NavEntry> =
    visibleNavEntries(caps).filter { it.route in setOf("home", "search", "favorites") }

@Composable
fun GlassSideNav(
    sourceName: String,
    caps: Set<Capability>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = visibleNavEntries(caps)
    GlassSurface(modifier = modifier.width(260.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("导航", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "当前图源：$sourceName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entries.forEach { entry ->
                val active = entry.route == currentRoute
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            MaterialTheme.shapes.small,
                        )
                        .clickable { onNavigate(entry.route) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }

            Text(
                "不支持的能力不会出现在这里（与站点侧栏的剪空一致）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
