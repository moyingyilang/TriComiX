# E-Hentai 协议笔记（TriComiX 自用）

> **来源与边界**：本文是对一份第三方 EhViewer（`com.xjs.ehviewer` 2.0.2.3，本地反编译产物）的
> **结构阅读**结果，用于互操作性研究。我们据此**自行实现**，不复制对方代码。
> **未验证**：以下全部细节来自静态阅读，**没有对真实站点发过请求**。

## 站点与域名

| 域名 | 用途 |
| --- | --- |
| `e-hentai.org` | 主站（公开内容） |
| `exhentai.org` | 需登录的站点（同一套页面结构） |
| `forums.e-hentai.org` | 论坛（登录入口之一） |
| `ehgt.org` | 缩略图/图片 CDN |
| `ehwiki.org` | 标签维基 |

主要路径：`/`（首页）、`/api.php`、`/favorites.php`、`/home.php`、`/mytags`、`/popular`、
`/toplist.php`、`/uconfig.php`、`/watched`、`/t/<tag>`（标签）、`/g/<gid>/<token>/`（作品详情）、
`gallerypopups.php?gid=&t=&act=addfav`（收藏）、`archiver.php?gid=&token=`（归档下载）。

## 参照实现的模块划分（我们要自己实现的对应物）

| 参照类 | 职责 | 解析方式 |
| --- | --- | --- |
| `EhClient` + `EhRequest(Builder)` | 请求编排 | —— |
| `EhCookieStore` | cookie 持久化（登录态） | —— |
| `EhHosts` | 域名选择与可用性 | —— |
| `EhUrl` | URL 构造（详情/收藏/归档等） | —— |
| `GalleryListParser` | 首页/搜索列表 | Jsoup |
| `GalleryApiParser` / `GalleryTokenApiParser` | 列表的 JSON 接口 / 取 token | JSON |
| `GalleryDetailParser` | 作品详情 | HTML |
| `GalleryPageApiParser` / `GalleryPageParser` | 作品的图片列表与分页 | **JSON**（含 `hath`） |
| `SignInParser` | 登录 | HTML + cookie |
| `FavoritesParser` / `EhHomeParser` / `TopListParser` / `ArchiveParser` / `TorrentParser` | 收藏/首页/榜单/归档/种子 | 混合 |

## 关键机制

- **登录态就是 cookie**（无 token 体系）：登录后把 cookie 持久化，后续请求带上即可；
- **`hath`**：图片列表里每张图带的键，出现在分页解析、归档解析与请求构造中 —— 它不是加密，
  而是服务端随页面下发的参数，**必须与图片 URL 一起原样使用**；
- **图片地址**：由页面/JSON 给出（CDN 为 `ehgt.org`），我们只做拼接与携带 `hath`；
- **分页**：作品的图片列表按"页"组织（每页若干张），与 JM/Pica 的"章节"语义不同 ——
  这正是 `core` 里 `PageRef` 存在的原因。

## 我们的实现顺序（务实版）

1. `EhUrl`（纯逻辑，可测；本轮已落）；
2. `EhCookieJar` + `EhClient`（OkHttp + cookie 持久化）；
3. `GalleryListParser`（首页/搜索，Jsoup）→ 对应 `ComicSource.search/home`；
4. `GalleryDetailParser`（详情）→ `detail/chapters`；
5. `GalleryPageApiParser`（图片列表 + `hath`）→ `pages/imageRequest`；
6. `SignInParser`（登录）→ `login`。

**验证策略**：纯逻辑（URL 构建、cookie 解析、JSON 字段映射）用单元测试 + **我们自写的合成样本**；
真实站点行为必须由使用者在自己的网络环境下验证（我们不对站点发起自动化访问）。
