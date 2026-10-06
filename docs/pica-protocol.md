# PicACG 协议笔记（TriComiX 自用）

> **来源与边界**：本文来自本项目私有存档 `picacg-reversing` 的协议文档与原生层分析，
> 以及一个**公开参照实现**（`2024baibai/PicaComic-Api`）的结构阅读。
> 我们据此**自行实现**，不复制任何参照代码。
> **未验证**：以下端点与头名都来自静态阅读，**从未对真实服务发过请求**。

## 基址与响应外壳

- 基址：`https://picaapi.picacomic.com/`
- 响应统一为 `{code, message, data}`；`code != 200` 时按失败处理（不要把 `data` 当成功数据）。
- 分页统一为 `{docs, total, limit, page, pages}`（列表在 `data.comics` / `data.eps` / `data.pages` 下）。

## 端点

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `auth/sign-in` | 登录（返回令牌） |
| GET | `comics/{id}` | 作品详情 |
| GET | `comics/{id}/eps?page=N` | 章节（episode）列表 |
| GET | `comics/{id}/order/{order}/pages?page=N` | 某章节的图片列表（**用章节序号 `order`**，不是章节 id） |
| GET | `comics/search?page=N&q=<编码后>` | 搜索 |
| GET | `users/profile`、`users/{id}/profile` | 个人资料 |
| GET | `users/favourite?page=N` | 收藏 |
| POST | `users/punch-in` | 签到 |

**未确认**：首页/推荐与历史记录。参照实现里没有这两个端点，因此本项目**不实现**它们
（宁可返回"未支持"，也不凭空造一个地址）。

## 请求头

`api-key`、`accept`、`app-platform`、`app-version`、`app-build-version`、`app-uuid`、
`app-channel`、`image-quality`、`time`、`nonce`、`signature`；登录后加 `authorization`。

参照实现里还有一个 `app-nonce`，本实现**先不发**：尚未确证它是否参与签名，
盲目发送可能与签名不一致（这一点留待真机验证时确认）。

## 签名（以原生层结论为准）

```
msg  = lower( path + time + nonce + httpMethod + apiKey )     # 5 段
sign = hex( HmacSHA256(key, msg) )                            # 小写十六进制，64 字符
```

**注意两份存档文档曾冲突**：API 层文档写的是 8 段（含 baseUrl/appVersion/buildVersion），
原生层分析给出的是 5 段并明确注明不含那三项。**原生层是对的**（它从实际代码得出）。
本项目曾按 8 段实现过，已改正。

密钥来自原生库 `getStringSigFromNative()`（63 字节 ASCII）。原客户端靠**校验 APK 自身签名**
才返回正确密钥，第三方无法复现该派生，因此本项目内置该常量（方案 A，见
`PicaCredentials.kt` 里的来源、风险与维护说明）。

## 时间同步

服务端在响应头返回 `Server-Time`，客户端把它与本地时间的差值持久化，
后续请求的 `time` 字段加上该偏移（容忍客户端时钟漂移，避免签名因时间戳偏差失效）。

## 图片质量档位

PicACG 只有三档：`original` / `normal` / `low`，通过 **`image-quality` 请求头**下发
（不是 URL 参数）。core 有四档，映射如下：

| core | PicACG |
| --- | --- |
| LOW | low |
| MEDIUM | normal |
| HIGH | normal（**不**升级为原图：原图通常大得多，用户选 HIGH 不代表要原图） |
| ORIGINAL | original |
