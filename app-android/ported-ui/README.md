# 已搬迁的 JMNeXt 界面（原文，暂不参与编译）

本目录是**懒移植**的中间产物：JMNeXt 的界面代码**原样**放在这里，作为待适配的原文。

**为什么不直接放进 `src/main/kotlin`**：这些屏幕整体依赖 JM 专有类型（`JmRepository` 的方法名、
`AlbumDetail` / `SeriesItem` / `ReadPayload` / `ListItem` 等）。一次全放进去会让构建直接崩，
且无从判断哪里需要改。放在非编译目录里，可以**逐屏适配、逐屏迁入**，每一步都能编译与安装。

## 已搬入（看漫画链路）

| 文件 | 体积 | 待改的耦合点 |
| --- | --- | --- |
| `screens/home/HomeScreen.kt` | 约 20 KB | `repo.promote()` / `latest()` / `weekIssues()` → `ComicSource.home()`（按能力显隐） |
| `screens/search/SearchScreen.kt` | 约 38 KB | `repo.search(...)` + `SearchResult.page.items` → `search()` → `Paged<Comic>` |
| `screens/detail/DetailScreen.kt` | 约 56 KB | `repo.album(id)` / `AlbumDetail.series` → `detail()` → `ComicDetail` |
| `screens/reader/ReaderScreen.kt` | 约 48 KB | `repo.read(id).images` / `needsUnscramble` → `pages()` + `imageRequest()` |
| `nav/JmNavHost.kt` | 约 42 KB | 去掉 JM 独有路由（评论/画师/创作者/我的/签到） |

## 未搬入（JM 独有，按策略停掉）

`comments`、`creator`、`profile`、`category`、`more`、`random`、`favorites`、`auth` ——
统一接口目前不表达它们需要的数据。将来若扩展 `core` 的模型再考虑。

## 适配时的固定要求

1. 页面**只认** `core` 的模型与 `ComicSource`，不得出现任何源的专有类型；
2. 源能力不足时**按能力显隐**或明确提示"该源不支持"，不静默留白；
3. 每迁入一屏：编译通过 + 既有单测通过 + APK 重装 + 设备上点一遍，才算完成。
