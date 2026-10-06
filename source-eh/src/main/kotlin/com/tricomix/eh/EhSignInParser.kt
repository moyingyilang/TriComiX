package com.tricomix.eh

/**
 * 登录结果解析。
 *
 * 依据：对参照实现 `SignInParser` 与 `EhEngine.signIn` 的结构阅读 ——
 * - 成功：页面含 `<p>You are now logged in as: <名字><`；
 * - 失败：`<h4>The error returned was:</h4><p><原因></p>`，或 `<span class="postcolor"><原因></span>`。
 *
 * 正则由我们自己写，只借用其**形状**。**未验证**：从未对真实站点发过请求。
 */
object EhSignInParser {

    private val NAME = Regex("<p>You are now logged in as: (.+?)<")
    private val ERROR_H4 = Regex("<h4>The error returned was:</h4>\\s*<p>(.+?)</p>", RegexOption.DOT_MATCHES_ALL)
    private val ERROR_SPAN = Regex("<span class=\"postcolor\">(.+?)</span>", RegexOption.DOT_MATCHES_ALL)

    /** 成功返回用户名；失败抛出带上服务端原话的异常（不猜成功）。 */
    fun parse(html: String): String {
        NAME.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val reason = (ERROR_H4.find(html) ?: ERROR_SPAN.find(html))?.groupValues?.get(1)?.trim()
        if (reason != null) throw IllegalStateException("登录被拒绝：$reason")
        throw IllegalStateException("无法解析登录结果（既没有成功标记也没有错误信息）")
    }
}
