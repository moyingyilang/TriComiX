# 界面搬迁方案（沿用 JMNeXt，图源换成 ComicSource）

定位（用户明确）：**这个项目是"下一个 JMNeXt"** —— UI 大体沿用 JMNeXt，只是图源不同；
前期先沿用，后期再单独设计。

## 一、JMNeXt 的界面结构（实测文件清单）

| 文件 | 体积 | 作用 |
| --- | --- | --- |
| `ui/JmNavHost.kt` | 42 KB | 全部导航 |
| `ui/screens/detail/DetailScreen.kt` | 56 KB | 详情 |
| `ui/screens/reader/ReaderScreen.kt` | 48 KB | 阅读器 |
| `ui/screens/profile/ProfileScreen.kt` | 56 KB | 我的 |
| `ui/screens/search/SearchScreen.kt` | 38 KB | 搜索 |
| `ui/screens/favorites/FavoritesScreen.kt` | 26 KB | 收藏 |
| `ui/screens/category/CategoryScreen.kt` | 22 KB | 分类 |
| `ui/screens/home/HomeScreen.kt` | 20 KB | 首页 |
| `ui/screens/creator/CreatorScreen.kt` | 18 KB | 画师 |
| `ui/screens/comments/CommentsScreen.kt` | 17 KB | 评论 |
| `ui/screens/random/RandomListScreen.kt` | 15 KB | 随机 |
| `ui/screens/auth/AuthScreen.kt` | 14 KB | 登录 |
| `ui/screens/more/MoreListScreen.kt` | 13 KB | 更多列表 |
| `ui/components/{Glass,FloatingBottomBar,…}.kt` | — | 通用组件 |
| `ui/theme/{Theme,ThemeStyle}.kt` | 34 KB | 主题 |

## 二、搬迁原则

1. **可原样搬的**（与图源无关）：`ui/theme/`、`ui/components/`、以及各屏幕的**布局与交互**部分；
2. **必须改写的**：屏幕里对 JM 专有数据类型的直接使用（`AlbumDetail` / `SeriesItem` / `ReadPayload` /
   `ListItem` / `JmRepository` 的方法名）→ 一律改为只依赖 `core` 的统一模型与 `ComicSource` 接口；
3. **不搬的**（与图源无关或 JM 特有）：评论、画师、创作者内容、签到、通知等 —— 统一接口目前不表达它们，
   强行搬会让"图源无关"这件事破产。这些留待后期单独设计。

## 三、逐屏的接口映射（这是搬迁的实际工作量所在）

| 屏幕 | 现在依赖（JM 专有） | 改为 |
| --- | --- | --- |
| Home | `repo.promote()` / `repo.latest()` / `repo.weekIssues()` | `ComicSource.home()`（分区块）；无 home 的源显示"未支持" |
| Search | `repo.search(query,page,…)` + `SearchResult.page.items` | `ComicSource.search(query,page)` → `Paged<Comic>` |
| Detail | `repo.album(id)`、`AlbumDetail.series`、`repo.isTracked`、`repo.toggleFavorite` | `ComicSource.detail(id)` → `ComicDetail.comic` + `.chapters` |
| Reader | `repo.read(chapterId).images`、`needsUnscramble` | `ComicSource.pages(chapterId)` → `PageRef`；`ComicSource.imageRequest(page,quality)` → `ImageRequest`（含 `unscramble`） |
| Favorites | `repo.favorites(page,…)` | `ComicSource.favorites(page)`（EH/Pica 已实现） |
| Auth | `repo.login/register/forgotPassword` | `ComicSource.login(SourceCredential)` / `logout()`；只保留登录，不含注册/找回 |
| 其它（Category/Creator/Comments/Random/More/Profile） | JM 专有 | **本期不搬**，见原则 3 |

## 四、分批实施顺序（每批独立可验证）

1. **批次 1（骨架）**：在 `app-android` 里建 `ui/` 包，搬 `theme/` 与 `components/`，用 `JmNavHost` 的
   导航结构搭出"首页/搜索/详情/阅读"四条路由，屏幕先用最小实现 —— 保证 APK 能装能跑；
2. **批次 2（列表）**：把 Home/Search 换成 JMNeXt 的布局，数据走 `ComicSource`；
3. **批次 3（详情）**：Detail 的布局照搬，章节列表来自 `ComicDetail.chapters`；
4. **批次 4（阅读器）**：Reader 的翻页/手势照搬，取图走 `imageRequest`（含反切片与 `hath`）；
5. **批次 5（登录与收藏）**：Auth 只留登录，Favorites 接 `favorites()`。

每批结束都要求：编译通过 + 既有 168 个单测通过 + APK 重装 + 在设备上实际点一遍。

## 五、必须向使用者说明的取舍

- 统一接口**不表达**评论、画师、创作者内容、签到、通知 —— 这些屏幕**不会**搬过来，
  除非将来扩展 `core` 的模型；
- 各源能力不同（EH 无"首页"、Pica 无"首页/历史"），界面必须**按能力显隐**，
  不能假设每个源都有全部能力；
- 目前的三屏最小界面（`MainActivity.kt`）是脚手架，批次 1 起会被逐步替换。

## 六、HomeScreen 的实际改写清单（读 430 行原文后得到）

`ported-ui/screens/home/HomeScreen.kt` 的耦合面与处理方式：

| 原文里的东西 | 处理 |
| --- | --- |
| `LocalRepository.current`（CompositionLocal 提供 `JmRepository`） | **删掉**；改由参数传入 `ComicSource` |
| `HomeViewModel(repo)`、`state.sections/latest/promoteError` | **换掉**：新写一个只依赖 `ComicSource.home()` 的状态持有者（`List<Section>` + 一个错误串） |
| `repo.promote()` / `repo.latest()` / `repo.weekIssues()` | → `ComicSource.home()`；**首页缺失时按能力显示"该源不支持首页"** |
| `repo.coverUrl(comic)` | → `comic.coverUrl`（`core.Comic` 已带） |
| `ListItem` 等 JM DTO | → `core.Comic` |
| tag 屏蔽（`hiddenFlow` / `tagBlocker` / `hiddenIds`） | **丢掉**（JM 专有；统一接口不表达屏蔽规则） |
| `com.jmnext.ui.*` 导入 | 映射为 `com.tricomix.android.ui.*`（组件已迁入） |
| 布局本身（Section 标题、LazyColumn/grid、加载与错误态的支架） | **保留**（这正是"沿用其界面"的部分） |

**结论**：HomeScreen 的**布局可复用**，要换的是"数据管道 + 状态形状 + 屏蔽逻辑"。
Home 之后，Search / Detail / Reader 是同一套路子（换数据管道、留布局），
其中 Reader 额外要处理 EH 的 `hath` 与 JM 的反切片标记。

## 七、SearchScreen 的改写清单（读 881 行原文后得到）

结构与要素：`SearchViewModel` + `SearchUiState`（结果 / 历史 / 推荐 / 热词 / 筛选），
界面件有 `SearchScreen`、`TagBlockedNotice`、`SuggestionPanel`、`WordChips`、`SearchFilterRows`、`FilterRow`、`DateFilterRows`；
分页按 `pageSize = 80`；搜索历史存在 `AppPrefs`。

| 原文里的东西 | 处理 |
| --- | --- |
| `SearchViewModel` + `repo.bootstrap()/search(...)` | 换成只吃 `ComicSource` 的状态持有者：`search(query, page)` → `Paged<Comic>`（`bootstrap` 已由 `JmSource` 内部自动完成） |
| `repo.hotTags()`、`repo.randomRecommend()` | **统一接口里没有** → 要么不显示这两块，要么扩展 `core` 的接口（见下方"接口缺口"） |
| `prefs.searchHistory`（JM 的 `AppPrefs`） | 用本机存储（`SharedPreferences`）自己实现搜索历史；不依赖 JM 的 prefs 实现 |
| `filters`（排序 / 日期区间） | **接口缺口**：`ComicSource.search(query, page)` 不接受排序与日期 → 先隐藏这些筛选行；要保留就得扩展接口 |
| `TagBlockedNotice` / `LocalTagBlocker` | JM 专有屏蔽逻辑 → 去掉 |
| `ListItem` | → `core.Comic` |
| `ComicCard` / `ComicRow` | `ComicCard` 已搬并已改为吃 `core.Comic`；`ComicRow` 未搬 → 先统一用 `ComicCard` |
| `jmAnimateItem` / `jmComicSharedKey` / `LocalBottomBarInset` | 过渡动画与底栏内边距 → 已有 `jmSharedElement` 空实现；`LocalBottomBarInset` 需补一个默认值桩 |
| 分页（80/页） | 统一接口按页取 → 可做"加载更多" |

### 这一屏暴露的接口缺口（值得单独记）

统一接口目前是"最小可用"形态，搬 JMNeXt 的完整搜索界面时不够用：

1. **搜索筛选**：排序（最新/热门/评分…）、日期区间、分类 —— `search()` 没有对应参数；
2. **热词与随机推荐**：`hotTags()` / `randomRecommend()` 在接口里没有位置；
3. **搜索历史**：属于本机状态，不该由源承担（但界面需要它，所以要有一个上层存储）。

处理建议（按代价从小到大）：

- **先用能力与显隐解决**：没有的能力就不显示对应 UI（符合目标里"按能力显隐"的要求）；
- **再考虑扩展**：给 `ComicSource` 增加一个可选的 `SearchOptions`（排序/日期/分类）与
  `Capability.SEARCH_FILTERS`，各源按自身支持情况实现或忽略。

在扩展之前**不假装支持**：界面里不会出现"点了没反应"的筛选器。

## 八、第二个接口缺口：收藏的"写"操作（Detail 屏需要）

现状（实测接口表）：

| 层 | 有什么 | 缺什么 |
| --- | --- | --- |
| `ComicSource` | `favorites(page)` —— 只能**读**收藏列表 | **没有**"加收藏/取消收藏" |
| `source-eh` | `EhUrl.addFavorite(gid, token)` —— 请求地址已具备 | 没有暴露到源接口，也没有解析返回 |
| `source-jm` | `JmRepository.toggleFavorite(aid)` —— 实现已存在 | 适配器没有转发 |

也就是说：**底层能力基本都在，缺的是接口上的一个位置**。

### 建议的扩展（等有人拍板再做）

```kotlin
// core：新增一个能力与一个方法
Capability.FAVORITE_WRITE

suspend fun toggleFavorite(comicId: String, favorite: Boolean): Result<Boolean>
```

各源按自身情况实现：EH 用已有的 `addFavorite` 地址；JM 转发 `toggleFavorite`；
Pica 需要先确认它的收藏写接口（`users/favourite` 目前只用于读取）。
**没有该能力的源，界面不显示收藏按钮**，而不是"点了没反应"。

### Detail 屏因此的临时处理

Detail 目前**不显示**收藏按钮 —— 这是刻意的，等接口扩好再接。
按目标要求，"不支持"要说出来而不是静默：界面上会体现为按钮不存在（该源无此能力），
而不是出现一个点了没反应的按钮。

## 九、DetailScreen 的改写清单（读 1316 行原文后得到）

结构：`DetailViewModel`（约 160-520 行）+ `DetailScreen`（526）+ `DetailHeader`（736，封面区）
+ `DetailContent`（841，标签/简介/章节）+ `ChapterPager`（1237）。

| 原文里的东西 | 处理 |
| --- | --- |
| `repo.bootstrap()` + `repo.album(comicId)` | → `ComicSource.detail(comicId)`（`bootstrap` 已由 `JmSource` 内部自动完成） |
| `repo.coverUrl(id, addTime)` | → `comic.coverUrl`（`core.Comic` 已带） |
| `AlbumDetail` / `SeriesItem` | → `ComicDetail` / `Chapter` |
| **章节选择逻辑**（"单章节=从头开始"、"无章节时用作品 id 调 `comic_read`"） | **保留**：我已按同样规则实现（`MainActivity.openDetail` 里的单篇回退），原文注释与我的行为一致，可对账 |
| `ChapterPager`（章节切换） | 保留布局 |
| `DetailHeader`（封面区） | 保留布局；封面已用 Coil 接上（本会话已做） |
| `CategoryChip` 组件 | 未搬 → 先直接用 `Text`，或按需搬该组件 |
| `ComicCard` | 已搬并已改为吃 `core.Comic` |
| `jmSharedElement` / `jmVanishWhenLeaving` / `jmComicSharedKey` | 共享元素过渡 → 已有 `jmSharedElement` 空实现；其余按需补桩 |
| `ReadProgressStore`（本地阅读进度） | **属于上层本机状态**，可移植（该实现已在 `source-jm` 里，直接复用） |
| 跟踪（`isTracked`/`toggleTracking`）、点赞（`like`）、下载（`albumDownload`）、收藏夹（`folderList`/`editFavoriteFolder`）、收藏标签（`updateFavoriteTags`） | **统一接口都没有** → 按能力显隐或不做（见下方缺口） |
| `TagPickerDialog` / `FolderPickerDialog` | 依赖 JM 的收藏体系 → **不搬** |

### 这一屏暴露的第三个接口缺口：Interaction 类操作

统一接口目前只有"读"与登录，**没有**这些"写/互动"操作：

| 操作 | 原文方法 | 建议 |
| --- | --- | --- |
| 收藏（加/取消） | `toggleFavorite` | 加 `toggleFavorite(comicId, favorite)` 与 `Capability.FAVORITE_WRITE`（EH/JM 底层都已具备，见第八节） |
| 点赞 | `like` | 加 `Capability.LIKE`（JM 有实现；其他源未必支持） |
| 跟踪更新 | `isTracked` / `toggleTracking` | JM 专有概念，建议**不进入统一接口** |
| 下载 | `albumDownload` | 属于 `Capability.DOWNLOAD`，但实现是源特异的，本轮不做 |
| 收藏夹/标签管理 | `folderList` / `editFavoriteFolder` / `updateFavoriteTags` | JM 专有，建议不进统一接口 |

**原则不变**：接口里没有的能力，界面就不显示对应按钮（或显示"该源不支持"），
绝不出现点了没反应的控件。

## 十、主体（JmNavHost + HomeScreen）搬迁方案

前面已逐块搬进：主题、8 个组件、Detail/Search/Reader 三屏、底部导航、首页横向分区排布。
**剩下最像"主体"的两块**是 JMNeXt 的 `JmNavHost`（42 KB）与 `HomeScreen`（20 KB）。

### 为什么至今没一口气搬完（如实记录）

本会话尝试过 4 次"跨几十行整体替换"，其中 3 次把文件改坏、靠回滚兜底。
结论：**大块搬迁需要一次完整、独立的会话上下文**，在长会话尾部做，失败率明显偏高。

### 建议的实施顺序（下次开工照此执行）

1. **先打 tag 保底**：`git tag port-before-navhost && git push --tags`（随时可整体回退）；
2. **抽导航壳**（不改屏幕内容）：新建 `ui/AppNav.kt`，只用 Compose 原生导航（`NavHost` + `composable("route")`）
   定义路由：`home` / `search` / `favorites` / `detail/{id}` / `reader/{comicId}/{chapterId}`；
   `MainActivity` 只保留 `setContent { JmTheme { AppNav(...) } }`；
   每加一条路由就编译一次 —— 这是把 42 KB 的 `JmNavHost` **拆成可验证小步**的唯一办法；
3. **迁 HomeScreen**：按第九节同款清单（换数据管道、留布局）。它 430 行，建议分三块：
   ① 头部与分区标题；② 分区横向行（已在 `ComicResults` 实现，可直接复用）；
   ③ 分页与下拉刷新（`Paged.hasMore` 已具备，配 `LoadMoreFooter`）；
4. **转场动画**：`JmNavHost` 里的过渡（共享元素、淡入淡出）最后做；注意 `jmSharedElement`
   目前是空实现（见 `ui/screens/` 的桩），要做真动画需从 JMNeXt 一并搬入；
5. **入口补齐**（JMNeXt 有、当前没有）：分类、画师、我的 —— 其中"我的"需要新接口能力
   （见第八、九节的缺口清单），不要先做空壳。

### 界面上已经"像 JMNeXt"的部分（可对照验收）

| 元素 | 用的 JMNeXt 组件 |
| --- | --- |
| 配色/形状/排版 | `JmTheme`（theme 包 5 个文件） |
| 顶栏 | `GlassTopBar` |
| 背景 | `AmbientBackdrop`（`Modifier.ambientBase()`） |
| 卡片 | `ComicCard`（只吃 `core.Comic`） |
| 底部导航 | `FloatingBottomBar` |
| 加载/错误/空态 | `LoadingBox` / `ErrorBox` / `MessageState` |
| 首页分区排布 | 分区标题 + 横向滑动卡片行 |
| 阅读器 | `LiteFeatures` 预取、`JmImage` + `ImageUnscramble` 反切片 |
| 详情 | `ReadProgressStore`（继续阅读） |

### 尚未搬的交互（按价值排序）

1. 阅读器 **左右滑动翻页** 与 **双指缩放**；
2. 列表 **下拉刷新 / 上拉加载更多**（`LoadMoreFooter` 已就位）；
3. 详情页的 **收藏状态行**（接口已扩 `FAVORITE_WRITE`，JM 源已实现写；EH 待接）。
