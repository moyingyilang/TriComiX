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
 * TriComiX 的最小可用界面。
 *
 * 数据层一律走统一接口 [ComicSource]：界面**不认识任何源的内部结构**，
 * 只调用 login / search / home / detail / chapters / pages / imageRequest。
 *
 * 三条设计约束：
 * 1. **每个源只建一次并记住** —— 否则每次调用新建实例，登录状态（令牌/cookie）会立刻丢失；
 * 2. 凭据只发往所选源；本机**只记住用户名，不存密码**（密码每次输入）。
 *    会话令牌/cookie 目前只活在本次运行内，重启需重新登录 —— 这是已知缺口，不假装已解决；
 * 3. 图片用 OkHttp + BitmapFactory 自取，不引入图片库；失败一律显示原因，不静默留白。
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

    // 登录表单状态
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loginStatus by remember { mutableStateOf("") }
    val prefs = remember { context.getSharedPreferences("tricomix_ui", Context.MODE_PRIVATE) }

    // 只记住用户名（不存密码）；首次进入时预填
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
                    // --- 登录 ---
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
                            enabled = !busy && user.isNotBlank() && pass.isNotBlank(),
                            onClick = {
                                busy = true
                                loginStatus = "登录中…"
                                val target = source()
                                scope.launch {
                                    target.login(
                                        SourceCredential(
                                            mapOf("username" to user, "email" to user, "password" to pass)
                                        )
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

                    // --- 搜索 ---
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !busy && query.isNotBlank() && source().capabilities.contains(Capability.SEARCH),
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
                    }
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp))
                    Text(status, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                    Divider(Modifier.padding(vertical = 8.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(results) { comic ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    busy = true; status = "载入详情…"
                                    scope.launch {
                                        source().detail(comic.id).fold(
                                            onSuccess = { d -> screen = Screen.Detail(d.comic, d.chapters); status = "详情已载入" },
                                            onFailure = { status = "详情失败：${it.message}" },
                                        )
                                        busy = false
                                    }
                                }.padding(8.dp),
                            ) {
                                Text(comic.title.ifBlank { "(无标题)" }, fontWeight = FontWeight.Bold)
                                Text(
                                    "${comic.author ?: "未知作者"} · ${comic.tags.take(3).joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(comic.coverUrl ?: "(无封面地址)", style = MaterialTheme.typography.labelSmall)
                            }
                            Divider()
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

/** 取一张图并解码；失败返回 null（由界面给出明确提示）。 */
private suspend fun fetchBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        imageClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val bytes = response.body?.bytes() ?: return@use null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }.getOrNull()
}
