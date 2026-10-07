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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tricomix.android.ui.components.ComicCard
import com.tricomix.core.model.Chapter
import com.tricomix.core.model.Comic
import com.tricomix.core.model.ImageQuality
import com.tricomix.core.model.PageRef
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
 * 分层要求：界面**只**通过 [ComicSource] 取数据，不认识任何源的具体类型；
 * 源能力不足处按 [Capability] 显隐（例如 EH 没有首页、Pica 首页未实现 → 按钮置灰）。
 *
 * 界面元素取自 JMNeXt：主题走 `JmTheme`，列表用 `ComicCard`；
 * 更完整的屏幕正在从 `ported-ui/` 逐屏迁入（见 docs/ui-port-plan.md）。
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

    LaunchedEffect(sourceName) {
        user = prefs.getString("user_$sourceName", "").orEmpty()
        pass = ""
        loginStatus = ""
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
                            onClick = {
                                busy = true; status = "搜索中…"
                                scope.launch {
                                    source().search(query, 1).fold(
                                        onSuccess = { results = it.items; status = "取到 ${it.items.size} 条" },
                                        onFailure = { status = "失败：${it.message}" },
                                    )
                                    busy = false
                                }
                            },
                        ) { Text("搜索") }
                        Button(
                            enabled = !busy && source().capabilities.contains(Capability.HOME),
                            onClick = {
                                busy = true; status = "取首页…"
                                scope.launch {
                                    source().home().fold(
                                        onSuccess = { results = it.flatMap { s -> s.items }; status = "首页 ${results.size} 条" },
                                        onFailure = { status = "失败：${it.message}" },
                                    )
                                    busy = false
                                }
                            },
                        ) { Text("首页") }
                        Button(
                            enabled = !busy && source().capabilities.contains(Capability.FAVORITES),
                            onClick = {
                                busy = true; status = "取收藏…"
                                scope.launch {
                                    source().favorites(1).fold(
                                        onSuccess = { results = it.items; status = "收藏 ${it.items.size} 条" },
                                        onFailure = { status = "失败：${it.message}" },
                                    )
                                    busy = false
                                }
                            },
                        ) { Text("收藏") }
                    }
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp))
                    Text(status, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    capabilityHint(source().capabilities).takeIf { it.isNotEmpty() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Divider(Modifier.padding(vertical = 8.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results) { comic ->
                            // 用搬迁自 JMNeXt 的卡片：它只吃 core.Comic（底层可切换的关键接口面）
                            ComicCard(
                                comic = comic,
                                onClick = {
                                    busy = true; status = "载入详情…"
                                    scope.launch {
                                        source().detail(comic.id).fold(
                                            onSuccess = { d -> screen = Screen.Detail(d.comic, d.chapters); status = "详情已载入" },
                                            onFailure = { status = "详情失败：${it.message}" },
                                        )
                                        busy = false
                                    }
                                },
                            )
                        }
                    }
                }

                is Screen.Detail -> {
                    Button(onClick = { screen = Screen.Search }) { Text("返回") }
                    Text(s.comic.title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(s.comic.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                    Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Divider(Modifier.padding(vertical = 8.dp))
                    if (s.chapters.isEmpty()) Text("这个源没有给出章节")
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(s.chapters) { chapter ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    busy = true; status = "载入页列表…"
                                    scope.launch {
                                        source().pages(chapter.id).fold(
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
                    Text("${index + 1} / ${s.pages.size}　$status", style = MaterialTheme.typography.bodySmall)

                    val target = source()
                    val page = s.pages.getOrNull(index)
                    if (page != null) {
                        LaunchedEffect(s, index) {
                            bitmap = null; imageError = null
                            target.imageRequest(page, ImageQuality.ORIGINAL).fold(
                                onSuccess = { req ->
                                    val bmp = fetchBitmap(req.url)
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

private suspend fun fetchBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        imageClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val bytes = response.body?.bytes() ?: return@use null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }.getOrNull()
}

/**
 * 明确提示"该源不支持什么"。
 *
 * 目标要求：源能力不足时**按能力显隐，或明确提示"该源不支持"**。
 * 只把按钮置灰是不够的 —— 用户会以为是 bug。这里把缺失的能力列出来，
 * 让置灰有解释（例如 EH 没有首页、Pica 的首页与历史端点未确认）。
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
