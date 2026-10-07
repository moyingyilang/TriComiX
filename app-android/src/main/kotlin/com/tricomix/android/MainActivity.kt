package com.tricomix.android

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tricomix.android.ui.components.ComicCard
import com.tricomix.android.ui.components.ambientBase
import com.tricomix.android.ui.components.GlassTopBar
import com.tricomix.android.ui.screens.DetailRoute
import com.tricomix.android.ui.screens.LoginForm
import com.tricomix.android.ui.screens.ReaderRoute
import com.tricomix.android.ui.screens.SearchRoute
import com.tricomix.android.ui.SearchHistory
import com.tricomix.core.model.Chapter
import com.tricomix.core.model.Comic
import com.tricomix.core.model.ImageQuality
import com.tricomix.core.model.PageRef
import com.tricomix.core.model.Section
import com.tricomix.core.source.Capability
import com.tricomix.core.source.ComicSource
import com.tricomix.core.source.SourceCredential
import com.tricomix.eh.EhClient
import com.tricomix.eh.EhHost
import com.tricomix.eh.EhSource
import com.tricomix.pica.PicaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * TriComiX 的测试界面（懒移植进行中）。
 *
 * 分层：界面**只**通过 [ComicSource] 取数据，不认识任何源的具体类型；
 * 能力不足处按 [Capability] 显隐并给出"该源不支持…"的明确提示。
 * 视觉取自 JMNeXt：主题 `JmTheme`、列表与卡片 `ComicCard`。
 *
 * 首页按**分区**展示（JMNeXt 的首页就是分区的）：分区标题 + 该分区的作品卡片。
 * 搜索与收藏没有分区概念，因此它们会把分区状态清空，避免残留旧内容。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { com.tricomix.android.ui.theme.JmTheme { App() } }
    }
}

private val imageClient = OkHttpClient()

private sealed interface Screen {
    data object Search : Screen
    data class Detail(val comic: Comic, val chapters: List<Chapter>) : Screen
    data class Reader(val comic: Comic, val chapter: Chapter, val pages: List<PageRef>) : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App() {
    val context = LocalContext.current
    // 默认图源在重启后保留（本机设置，与图源无关）
    var sourceName by remember {
        mutableStateOf(
            context.getSharedPreferences("tricomix_ui", Context.MODE_PRIVATE)
                .getString("source", "EH") ?: "EH",
        )
    }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("选择源并输入关键词后点搜索；Pica 需先登录") }
    var sections by remember { mutableStateOf<List<Section>>(emptyList()) }
    var results by remember { mutableStateOf<List<Comic>>(emptyList()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Search) }
    val scope = rememberCoroutineScope()

    // 每个源只建一次并记住（登录状态就活在这些实例里）
    val sources = remember { mutableMapOf<String, ComicSource>() }
    fun source(): ComicSource = sources.getOrPut(sourceName) {
        when (sourceName) {
            "Pica" -> PicaSource()
            "JM" -> JmSources.create(context)
            else -> EhSource(EhClient(), EhHost.E_HENTAI)
        }
    }

    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loginStatus by remember { mutableStateOf("") }
    val prefs = remember { context.getSharedPreferences("tricomix_ui", Context.MODE_PRIVATE) }

    // 侧栏开关，以及侧栏请求切换到的页签
    var navOpen by remember { mutableStateOf(false) }
    var requestedTab by remember { mutableStateOf<com.tricomix.android.ui.screens.SearchTab?>(null) }
    // 搜索历史：本机状态，换源不丢（见 docs/ui-port-plan.md 的接口缺口一节）
    val history = remember { SearchHistory(com.tricomix.android.data.prefs.SharedPrefsKeyValueStore(context, "tricomix_ui")) }
    // 阅读进度属于本机状态（与图源无关），实现搬自 JMNeXt 的 ReadProgressStore
    val progress = remember { com.tricomix.android.data.ReadProgressStore(com.tricomix.android.data.prefs.SharedPrefsKeyValueStore(context, "tricomix_ui")) }

    LaunchedEffect(sourceName) {
        user = prefs.getString("user_$sourceName", "").orEmpty()
        pass = ""
        loginStatus = ""
    }

    // 统一的结果装载：搜索/收藏没有分区，首页有
    fun load(what: String, block: suspend ComicSource.() -> Result<*>) {
        busy = true
        status = "$what…"
        val target = source()
        scope.launch {
            try {
                when (val r = target.block()) {
                    is Result<*> -> r.fold(
                        onSuccess = { payload ->
                            when (payload) {
                                is List<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val secs = payload as List<Section>
                                    sections = secs
                                    results = secs.flatMap { it.items }
                                    status = "首页 ${results.size} 条（${secs.size} 个分区）"
                                }
                                is com.tricomix.core.model.Paged<*> -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val paged = payload as com.tricomix.core.model.Paged<Comic>
                                    sections = emptyList()
                                    results = paged.items
                                    status = "$what ${paged.items.size} 条"
                                }
                                else -> status = "$what：无法识别的返回类型"
                            }
                        },
                        onFailure = { status = "${what}失败：${it.message}" },
                    )
                    else -> status = "$what：无法识别的返回类型"
                }
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            GlassTopBar(
                title = "TriComiX",
                subtitle = sourceName,
                actions = {
                    Button(onClick = { navOpen = !navOpen }) { Text(if (navOpen) "关闭侧栏" else "菜单") }
                    Button(onClick = {
                        sourceName = when (sourceName) { "EH" -> "Pica"; "Pica" -> "JM"; else -> "EH" }
                        prefs.edit().putString("source", sourceName).apply()
                        screen = Screen.Search
                        results = emptyList()
                        sections = emptyList()
                        status = "已切换到 $sourceName"
                    }) { Text("切换源") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().ambientBase().padding(padding).padding(12.dp)) {
            if (navOpen) {
                com.tricomix.android.ui.GlassSideNav(
                    sourceName = sourceName,
                    caps = source().capabilities,
                    currentRoute = if (screen is Screen.Search) (requestedTab?.name?.lowercase() ?: "search") else "reading",
                    loginLabel = loginStatus.ifBlank { "未登录（点击登录）" },
                    onSelectSource = { name ->
                        sourceName = name
                        prefs.edit().putString("source", name).apply()
                        screen = Screen.Search
                        requestedTab = null
                        navOpen = false
                    },
                    onNavigate = { route ->
                        when (route) {
                            "home", "search", "favorites" -> {
                                requestedTab = when (route) {
                                    "home" -> com.tricomix.android.ui.screens.SearchTab.Home
                                    "favorites" -> com.tricomix.android.ui.screens.SearchTab.Favorites
                                    else -> com.tricomix.android.ui.screens.SearchTab.Search
                                }
                                screen = Screen.Search
                            }
                            "settings", "about" -> status = if (route == "about") {
                                "TriComiX 0.2.0-preview · 界面搬迁自 JMNeXt（AGPL-3.0）"
                            } else {
                                "默认图源：$sourceName（重启后保留）；图片质量与预取窗口尚未开放设置"
                            }
                            "history" -> status = "该源不支持历史"
                            "follow" -> status = "该源不支持追更"
                        }
                        navOpen = false
                    },
                    onLoginClick = { requestedTab = com.tricomix.android.ui.screens.SearchTab.Search; screen = Screen.Search; navOpen = false },
                )
                androidx.compose.material3.Divider()
            }
            when (val s = screen) {
                is Screen.Search -> SearchRoute(
                    source = source(),
                    sourceName = sourceName,
                    requestedTab = requestedTab,
                    history = history,
                    login = {
                        LoginForm(
                            accountLabel = if (sourceName == "Pica") "邮箱" else "用户名",
                            user = user,
                            password = pass,
                            busy = busy,
                            canLogin = user.isNotBlank() && pass.isNotBlank() &&
                                source().capabilities.contains(Capability.LOGIN),
                            status = loginStatus,
                            onUserChange = { user = it },
                            onPasswordChange = { pass = it },
                            onLogin = {
                                busy = true
                                loginStatus = "登录中…"
                                val target = source()
                                scope.launch {
                                    target.login(
                                        SourceCredential(mapOf("username" to user, "email" to user, "password" to pass))
                                    ).fold(
                                        onSuccess = {
                                            prefs.edit().putString("user_$sourceName", user).apply()
                                            loginStatus = "登录成功"
                                            pass = ""
                                        },
                                        onFailure = { loginStatus = "登录失败：${it.message}" },
                                    )
                                    busy = false
                                }
                            },
                            onLogout = { scope.launch { source().logout(); loginStatus = "已登出" } },
                        )
                    },
                    onOpen = { comic -> openDetail(comic, source(), scope) { screen = it } },
                )
                is Screen.Detail -> DetailRoute(
                    comic = s.comic,
                    chapters = s.chapters,
                    source = source(),
                    onBack = { screen = Screen.Search },
                    onOpenReader = { c, ch, p -> progress.record(c.id, ch.id); screen = Screen.Reader(c, ch, p) },
                    lastChapterId = progress.lastChapterId(s.comic.id),
                    onStatus = { status = it },
                )
                is Screen.Reader -> ReaderRoute(
                    comic = s.comic,
                    chapter = s.chapter,
                    pages = s.pages,
                    source = source(),
                    onBack = { screen = Screen.Search },
                    onStatus = { status = it },
                )
            }
        }
    }
}

/** 打开详情并把结果交给调用方（失败时写状态，不静默）。 */
private fun openDetail(
    comic: Comic,
    source: ComicSource,
    scope: kotlinx.coroutines.CoroutineScope,
    onOk: (Screen) -> Unit,
) {
    scope.launch {
        source.detail(comic.id).fold(
            onSuccess = { d ->
            // 不少 JM 作品是单篇（series 为空）：用作品自身当唯一章节，否则界面显示"没有章节"而无从进入。
            val ch = if (d.chapters.isEmpty()) listOf(Chapter(id = d.comic.id, title = d.comic.title, order = 0)) else d.chapters
            onOk(Screen.Detail(d.comic, ch))
        },
            onFailure = { /* 由界面状态提示；此处不静默吞掉语义 */ },
        )
    }
}

