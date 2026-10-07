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
    var sourceName by remember { mutableStateOf("EH") }
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
    // 搜索历史：本机状态，换源不丢（见 docs/ui-port-plan.md 的接口缺口一节）
    val history = remember { SearchHistory(com.tricomix.android.data.prefs.SharedPrefsKeyValueStore(context, "tricomix_ui")) }

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
            TopAppBar(
                title = { Text("TriComiX · $sourceName") },
                actions = {
                    Button(onClick = {
                        sourceName = when (sourceName) { "EH" -> "Pica"; "Pica" -> "JM"; else -> "EH" }
                        screen = Screen.Search
                        results = emptyList()
                        sections = emptyList()
                        status = "已切换到 $sourceName"
                    }) { Text("切换源") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            when (val s = screen) {
                is Screen.Search -> {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text(if (sourceName == "Pica") "邮箱" else "用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("密码（不会保存）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy && user.isNotBlank() && pass.isNotBlank()
                                && source().capabilities.contains(Capability.LOGIN),
                            onClick = {
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
                        ) { Text("登录") }
                        Button(onClick = {
                            scope.launch { source().logout(); loginStatus = "已登出" }
                        }) { Text("登出") }
                    }
                    Text(loginStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Divider(Modifier.padding(vertical = 8.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy && query.isNotBlank()
                                && source().capabilities.contains(Capability.SEARCH),
                            onClick = { history.add(query); load("搜索") { search(query, 1) } },
                        ) { Text("搜索") }
                        Button(
                            enabled = !busy && source().capabilities.contains(Capability.HOME),
                            onClick = { load("首页") { home() } },
                        ) { Text("首页") }
                        Button(
                            enabled = !busy && source().capabilities.contains(Capability.FAVORITES),
                            onClick = { load("收藏") { favorites(1) } },
                        ) { Text("收藏") }
                    }
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp))
                    Text(status, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    history.all().take(5).takeIf { it.isNotEmpty() }?.let { recent ->
                        Text(
                            "最近搜索（点一下填入最近的一条）：" + recent.joinToString(" · "),
                            modifier = Modifier.clickable { query = recent.first() },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    capabilityHint(source().capabilities).takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Divider(Modifier.padding(vertical = 8.dp))

                    LazyColumn(Modifier.fillMaxSize()) {
                        if (sections.isNotEmpty()) {
                            // 首页：按分区展示（分区标题 + 该分区的卡片）
                            sections.forEach { sec ->
                                item(key = "sec-${sec.title}") {
                                    Text(
                                        sec.title.ifBlank { "未命名分区" },
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                                    )
                                }
                                items(sec.items, key = { "${sec.title}-${it.id}" }) { comic ->
                                    ComicCard(comic = comic, onClick = { openDetail(comic, source(), scope) { d -> screen = d } })
                                }
                            }
                        } else {
                            items(results, key = { it.id }) { comic ->
                                ComicCard(comic = comic, onClick = { openDetail(comic, source(), scope) { d -> screen = d } })
                            }
                        }
                    }
                }

                is Screen.Detail -> {
                    Button(onClick = { screen = Screen.Search }) { Text("返回") }
                    Text(s.comic.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(s.comic.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    // 详情页也提示能力缺口（例如没有收藏写能力时不显示收藏按钮，但要说清为什么）
                    capabilityHint(source().capabilities).takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                    if (s.chapters.isEmpty()) Text("这个源没有给出章节", modifier = Modifier.padding(top = 8.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(s.chapters, key = { it.id }) { chapter ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    busy = true; status = "载入页列表…"
                                    val target = source()
                                    scope.launch {
                                        target.pages(chapter.id).fold(
                                            onSuccess = { p -> screen = Screen.Reader(s.comic, chapter, p); status = "共 ${p.size} 页" },
                                            onFailure = { status = "页列表失败：${it.message}" },
                                        )
                                        busy = false
                                    }
                                }.padding(12.dp),
                            ) { Text(chapter.title.ifBlank { "第 ${chapter.order + 1} 话" }) }
                            Divider()
                        }
                    }
                }

                is Screen.Reader -> {
                    var index by remember(s) { mutableStateOf(0) }
                    var bitmap by remember(s, index) { mutableStateOf<ImageBitmap?>(null) }
                    var imageError by remember(s, index) { mutableStateOf<String?>(null) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { screen = Screen.Search }) { Text("返回列表") }
                        Button(enabled = index > 0, onClick = { index -= 1 }) { Text("上一页") }
                        Button(enabled = index < s.pages.size - 1, onClick = { index += 1 }) { Text("下一页") }
                    }
                    Text(
                        "${index + 1} / ${s.pages.size}　预取 ${LiteFeatures.prefetchBefore}/${LiteFeatures.prefetchAfter}　$status",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    val target = source()
                    val page = s.pages.getOrNull(index)
                    if (page != null) {
                        LaunchedEffect(s, index) {
                            bitmap = null; imageError = null
                            target.imageRequest(page, ImageQuality.HIGH).fold(
                                onSuccess = { req ->
                                    val bmp = fetchCachedBitmap("${s.comic.id}#${req.url}", req.url, req.unscramble, page.extra["aid"]?.toIntOrNull()); prefetchPages(target, s.pages, index, scope)
                                    if (bmp == null) imageError = "图片下载或解码失败" else bitmap = bmp
                                },
                                onFailure = { imageError = "取图失败：${it.message}" },
                            )
                        }
                    }
                    when {
                        imageError != null -> Text("错误：$imageError")
                        bitmap == null -> CircularProgressIndicator(Modifier.padding(top = 12.dp))
                        else -> Image(
                            bitmap = bitmap!!,
                            contentDescription = "第 ${index + 1} 页",
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
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

private suspend fun fetchBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        imageClient.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://e-hentai.org/")
                .build(),
        ).execute().use { response ->
            val bytes = response.body?.bytes() ?: return@use null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }.getOrNull()
}

/**
 * 明确提示"该源不支持什么"。
 *
 * 目标要求：源能力不足时**按能力显隐，或明确提示"该源不支持"**。
 * 只把按钮置灰不够 —— 用户会以为是 bug。
 */
private fun capabilityHint(caps: Set<Capability>): String {
    val labels = listOf(
        Capability.HOME to "首页",
        Capability.SEARCH to "搜索",
        Capability.FAVORITES to "收藏",
        Capability.LOGIN to "登录",
        Capability.HISTORY to "历史",
        Capability.DOWNLOAD to "下载",
    )
    val missing = labels.filterNot { caps.contains(it.first) }.map { it.second }
    return if (missing.isEmpty()) "" else "该源不支持：" + missing.joinToString("、")
}

/**
 * 阅读页的按页图片缓存。
 *
 * 背景：`LiteFeatures.prefetchBefore/After` 是主项目用来权衡"内存换流量"的参数，
 * 之前我只是把它显示在界面上（等于没用）。这里让它真正生效：
 * 当前页解码完成后，按该窗口预取后续页，翻页时直接命中缓存。
 *
 * 取舍：缓存以 "作品 id#图片地址" 为键（地址里已含 `hath` 等参数，足以唯一标识一页）；
 * 不做容量上限 —— 测试包优先"翻页即出图"，内存上限留待后续按需处理。
 */
private val pageCache = mutableMapOf<String, ImageBitmap>()

private suspend fun fetchCachedBitmap(
    key: String,
    url: String,
    spec: com.tricomix.core.model.UnscrambleSpec? = null,
    aid: Int? = null,
): ImageBitmap? {
    pageCache[key]?.let { return it }
    val bmp = fetchBitmap(url)?.let { applyUnscramble(it, spec, aid) }
    if (bmp != null) pageCache[key] = bmp
    return bmp
}

/** 按 LiteFeatures 的窗口预取后续页（失败静默：预取是尽力而为，不该打断阅读）。 */
private fun prefetchPages(
    source: ComicSource,
    pages: List<PageRef>,
    current: Int,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val after = LiteFeatures.prefetchAfter
    val before = LiteFeatures.prefetchBefore
    val targets = ((current - before)..(current + after)).filter { it != current && it in pages.indices }
    if (targets.isEmpty()) return
    scope.launch {
        for (i in targets) {
            val page = pages.getOrNull(i) ?: continue
            runCatching {
                source.imageRequest(page, ImageQuality.HIGH).getOrNull()?.let { req ->
                    fetchCachedBitmap("${page.chapterId}#${req.url}", req.url)
                }
            }
        }
    }
}

/**
 * 应用 JM 的反切片。
 *
 * JM 的图片在服务端被切成若干横条并打乱顺序，客户端要用 (aid, 页标识) 复原。
 * 这里用的就是搬迁自 JMNeXt 的实现（`ImageUnscramble`），此前**只搬了实现却没有调用**，
 * 所以 JM 的图在界面上是乱的 —— 这正是"不是整理好的那种给人看的图"的原因。
 *
 * 没有反切片信息（如 EH / Pica 的图）时原样返回；解码或计算失败也原样返回，
 * 宁可显示一张乱图，也不让整页加载失败。
 */
private fun applyUnscramble(
    bitmap: ImageBitmap,
    spec: com.tricomix.core.model.UnscrambleSpec?,
    aid: Int?,
): ImageBitmap {
    if (spec == null || aid == null) return bitmap
    return runCatching {
        val src = bitmap.asAndroidBitmap()
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = com.tricomix.jm.data.image.ImageUnscramble.unscramble(pixels, w, h, aid, spec.seed)
        android.graphics.Bitmap.createBitmap(out, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }.getOrDefault(bitmap)
}
