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
 * 站点侧栏的做法在这里落地为：
 * 1. **单一数据源**：[entries] 一处定义，外壳只负责画；
 * 2. **剪空**：源不支持的能力项**不出现**（站点的 `pruneEmpty`）—— 对应本项目"按能力显隐、不给死控件"；
 * 3. **当前项高亮**：当前源与当前页面都高亮；
 * 4. **玻璃外壳**：[GlassSurface]（站点是 `.sidenav.glass`），与顶栏质感一致。
 *
 * 结构（按使用者的定位）：
 * - 顶部：**图源切换**（当前源高亮）+ **登录状态**（一行状态，点击去登录/登出）
 * - 中部：内容入口（首页 / 搜索 / 收藏 / 历史 / 追更）—— 不支持的能力自动剪掉
 * - 底部：**设置** / **关于**
 *
 * 只认 `core` 的 `Capability`，不含任何图源专有类型。
 */
data class NavEntry(
    val label: String,
    val route: String,
    /** 该条目要求的能力；为 null 表示始终可用（如搜索页签、设置、关于）。 */
    val requires: Capability? = null,
)

/** 可选图源（顺序即展示顺序）。 */
val NAV_SOURCES: List<String> = listOf("JM", "Pica", "EH")

/** 导航项的唯一来源（站点 `config/menu.js` 的对应物）。 */
fun buildNavEntries(): List<NavEntry> = listOf(
    NavEntry("首页", "home", Capability.HOME),
    NavEntry("搜索", "search", Capability.SEARCH),
    NavEntry("收藏", "favorites", Capability.FAVORITES),
    // 以下两项当前会被剪空（没有源支持），支持后自动出现
    NavEntry("历史", "history", Capability.HISTORY),
    NavEntry("追更", "follow", Capability.FOLLOW),
)

/** 剪空：把源不支持的能力项去掉（站点的 `pruneEmpty`）。 */
fun visibleNavEntries(caps: Set<Capability>): List<NavEntry> =
    buildNavEntries().filter { it.requires == null || caps.contains(it.requires) }

/** 底部栏与侧栏共用：底栏只取内容入口里的前三个（高频项）。 */
fun visibleTabs(caps: Set<Capability>): List<NavEntry> =
    visibleNavEntries(caps).filter { it.route in setOf("home", "search", "favorites") }

@Composable
fun GlassSideNav(
    sourceName: String,
    caps: Set<Capability>,
    currentRoute: String,
    loginLabel: String,
    onSelectSource: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = visibleNavEntries(caps)
    val content = entries.filter { it.route !in setOf("settings", "about") }

    GlassSurface(modifier = modifier.width(268.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // 图源切换
            Text("图源", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            NAV_SOURCES.forEach { name ->
                val active = name == sourceName
                NavRow(
                    label = name,
                    active = active,
                    trailing = if (active) "当前" else null,
                    onClick = { onSelectSource(name) },
                )
            }

            // 登录状态
            Text(
                "登录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp),
            )
            NavRow(label = loginLabel, active = false, trailing = null, onClick = onLoginClick)

            // 内容入口
            Text(
                "内容",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp),
            )
            content.forEach { entry ->
                NavRow(
                    label = entry.label,
                    active = entry.route == currentRoute,
                    trailing = null,
                    onClick = { onNavigate(entry.route) },
                )
            }

            // 设置与关于
            Text(
                "其它",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp),
            )
            listOf("设置" to "settings", "关于" to "about").forEach { (label, route) ->
                NavRow(
                    label = label,
                    active = route == currentRoute,
                    trailing = null,
                    onClick = { onNavigate(route) },
                )
            }

            Text(
                "源不支持的能力不会出现在上面（与站点侧栏的剪空一致）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 一行导航项：高亮、可点、右侧可带说明。 */
@Composable
private fun NavRow(
    label: String,
    active: Boolean,
    trailing: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
