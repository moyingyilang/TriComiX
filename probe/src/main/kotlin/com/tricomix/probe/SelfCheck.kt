package com.tricomix.probe

import com.tricomix.eh.EhCookieJar
import com.tricomix.eh.EhHost
import com.tricomix.eh.EhSignInParser
import com.tricomix.eh.EhUrl
import com.tricomix.eh.GalleryDetailParser
import com.tricomix.eh.GalleryListParser
import com.tricomix.eh.GalleryPageApiParser
import com.tricomix.pica.PicaApi
import com.tricomix.pica.PicaCredentials
import com.tricomix.pica.PicaHeaders
import com.tricomix.pica.PicaSigning
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Cookie

/**
 * 自检：**不联网、不需要账号**，用合成样本端到端验证纯逻辑。
 *
 * 目的：当 EH 站点不可达、或不想用真账号时，仍然能确认"链路没断、协议构造没写错"。
 * 它**不能**替代真实环境验证（真实页面形状只能由使用者验证），但能挡住"重构把签名或选择器改坏"。
 */
object SelfCheck {

    private val checks = mutableListOf<Pair<String, () -> Unit>>()

    private fun check(name: String, body: () -> Unit) {
        checks.add(name to body)
    }

    fun run(): Int {
        register()
        var failed = 0
        println("=== 自检（不联网）===")
        for ((name, body) in checks) {
            try {
                body()
                println("  通过  $name")
            } catch (e: Throwable) {
                failed++
                println("  失败  $name")
                println("         ${e::class.simpleName}: ${e.message}")
            }
        }
        println("=== 共 ${checks.size} 项，失败 $failed 项 ===")
        return if (failed == 0) 0 else 1
    }

    private fun expect(actual: Any?, expected: Any?, what: String) {
        if (actual != expected) throw AssertionError("$what：期望 $expected，实际 $actual")
    }

    private fun register() {
        // ---- Pica：签名与请求头 ----
        check("Pica 签名原文为 5 段且不含 baseUrl/版本号") {
            val s = PicaSigning.buildInput("comics", 1L, "n", "GET", "K")
            expect(s, "comics1nGETK", "拼接顺序")
        }
        check("Pica 内置密钥为 63 字节") {
            expect(PicaCredentials.signingKeyBytes.size, 63, "密钥长度")
        }
        check("Pica 签名为 64 位小写十六进制") {
            val sig = PicaSigning.signWithEmbeddedKey("comics/1", 1700000000L, "abc", "GET")
            expect(sig.length, 64, "签名长度")
            if (!sig.all { it in "0123456789abcdef" }) throw AssertionError("签名含非小写十六进制字符：$sig")
        }
        check("Pica 请求头齐备（含 authorization 的有无）") {
            val h = PicaHeaders.build("comics/1", "GET", 1700000000L, "n", "uuid")
            val required = listOf(
                "api-key", "accept", "app-platform", "app-version", "app-build-version",
                "app-uuid", "app-channel", "image-quality", "time", "nonce", "signature",
            )
            val missing = required.filterNot { h.containsKey(it) }
            if (missing.isNotEmpty()) throw AssertionError("缺少请求头：$missing")
            if (h.containsKey("authorization")) throw AssertionError("未登录时不应带 authorization")
            val withToken = PicaHeaders.build("comics/1", "GET", 1700000000L, "n", "uuid", token = "t")
            expect(withToken["authorization"], "t", "带令牌时的 authorization")
        }
        check("Pica 端点构造") {
            expect(PicaApi.comic("abc"), "comics/abc", "详情端点")
            expect(PicaApi.episodes("abc", 2), "comics/abc/eps?page=2", "章节端点")
            expect(PicaApi.pages("abc", 3, 1), "comics/abc/order/3/pages?page=1", "图片列表端点")
            expect(PicaApi.favourites(2), "users/favourite?page=2", "收藏端点")
            expect(PicaApi.signIn(), "auth/sign-in", "登录端点")
        }
        check("Pica 响应缺 code 时不默认成功") {
            val root = com.tricomix.pica.PicaJson.json.parseToJsonElement("""{"data":{}}""")
            if (com.tricomix.pica.PicaJson.code(root) == 200) throw AssertionError("缺 code 被当成了成功")
        }

        // ---- EH：URL ----
        check("EH URL 构造") {
            val u = EhUrl(EhHost.E_HENTAI)
            expect(u.home(), "https://e-hentai.org/", "首页")
            expect(u.search("a b", 2), "https://e-hentai.org/?f_search=a+b&page=2", "搜索")
            expect(u.gallery(1, "tok"), "https://e-hentai.org/g/1/tok/", "详情")
            expect(u.galleryPage(1, "tok", 3), "https://e-hentai.org/g/1/tok/?p=3", "分页")
            expect(u.favorites(3), "https://e-hentai.org/favorites.php?page=3", "收藏")
        }

        // ---- EH：解析器 ----
        check("EH 列表解析（含缩略图与页数）") {
            val html = """
            <table class="itg"><tr><td>
              <div class="gl1e"><img class="glthumb" data-src="https://ehgt.org/t/1.jpg"></div>
              <a class="glname" href="https://e-hentai.org/g/77/beef/">标题</a>
              <div>12 pages</div>
            </td></tr></table>
            """.trimIndent()
            val items = GalleryListParser.parse(html, "https://e-hentai.org/")
            expect(items.size, 1, "条目数")
            expect(items[0].gid, 77L, "gid")
            expect(items[0].token, "beef", "token")
            expect(items[0].thumbUrl, "https://ehgt.org/t/1.jpg", "缩略图")
            expect(items[0].pages, 12, "页数")
        }
        check("EH 详情解析（showkey 与逐页 imgkey）") {
            val html = """
            <html><body>
              <div id="gd1"><div style="background-image:url('https://ehgt.org/c.jpg')"></div></div>
              <h1 id="gn">T</h1>
              <div id="gj">alice</div>
              <div id="gdd">Posted: 2024-01-02 03:04 &nbsp; 2 pages</div>
              <table id="taglist"><tr><td><a href="https://e-hentai.org/tag/artist/bob">artist/bob</a></td></tr></table>
              <script>var showkey="abc123";</script>
              <a href="/s/aaaaaaaaaa/77-1">p1</a><a href="/s/bbbbbbbbbb/77-2">p2</a>
            </body></html>
            """.trimIndent()
            val d = GalleryDetailParser.parse(html, "https://e-hentai.org/g/77/beef/")
            expect(d.showKey, "abc123", "showkey")
            expect(d.pageTokens, listOf("aaaaaaaaaa", "bbbbbbbbbb"), "逐页 imgkey")
            expect(d.title, "T", "标题")
        }
        check("EH showpage 解析（图片地址与 hath）") {
            val body = """{"i3":"<img src=\"https://h/1.jpg?hath=deadbeef\" style=\"\">"}"""
            val img = GalleryPageApiParser.parse(body)
            expect(img.imageUrl, "https://h/1.jpg?hath=deadbeef", "图片地址")
            expect(img.hath, "deadbeef", "hath")
        }
        check("EH 登录解析（成功与失败都要认）") {
            expect(EhSignInParser.parse("<p>You are now logged in as: bob<"), "bob", "成功")
            var failed = false
            try { EhSignInParser.parse("<span class=\"postcolor\">bad password</span>") } catch (e: IllegalStateException) { failed = true }
            if (!failed) throw AssertionError("失败页应抛错")
        }
        check("EH cookie 快照往返") {
            val url = "https://e-hentai.org/".toHttpUrl()
            val a = EhCookieJar()
            a.saveFromResponse(url, listOf(Cookie.Builder().domain("e-hentai.org").path("/").name("k").value("v").expiresAt(Long.MAX_VALUE).build()))
            val b = EhCookieJar()
            b.restore(a.snapshot(), url)
            expect(b.loadForRequest(url).size, 1, "恢复后的 cookie 数")
        }
    }
}
