# 项目现状与接手须知

> 先读这份，再看 `unverified.md`（证据账本）、`ui-port-plan.md`（界面搬迁方案）、
> `eh-protocol.md` / `pica-protocol.md`（协议笔记）。**不要**只看"编译通过"就以为能用。

## 一、目标与分层

目标：把 TriComiX 做成「**JMNeXt 的上层 UI + 可插拔的底层图源**」，产出可安装的测试 APK。

- **底层**：统一接口 `ComicSource`（`core` 模块），已有三源 `source-jm` / `source-pica` / `source-eh`；
  能力用 `Capability`（LOGIN / SEARCH / HOME / FAVORITES / HISTORY / DOWNLOAD）表达；
- **上层**：沿用 JMNeXt 的界面（`ui/theme` + `ui/components` + 看漫画链路各屏幕）；
- 界面**只认** `core` 的模型与 `ComicSource`；源能力不足处按能力显隐，不静默留白。

## 二、已完成并验证到什么程度

| 部分 | 状态 | 证据位置 |
| --- | --- | --- |
| Pica 源 | **真实环境全链路通过**（登录/搜索/详情/章节/页列表/取图） | `unverified.md` 第一节 |
| EH 源 | **真实环境全链路通过**，但仅首批 20 页 | 同上 |
| JM 源 | 探针实证（45 条真实结果）；**真机未验证** | 同上 |
| 单测 | 168 个（JM 56 / Pica 40 / EH 72）全绿 | `gradle test` |
| 不联网自检 | 12 项全过 | `probe.sh eh "" 1 selfcheck` |
| APK | 可构建、可安装；含 JMNeXt 主题 + 8 个组件 + 源切换 + 搜索/详情/阅读 + 登录 + 能力显隐 | 见下命令 |

## 三、环境坑（都会浪费你时间，务必先看）

| 现象 | 原因 | 对策 |
| --- | --- | --- |
| 构建报 JAVA_HOME | 在**容器外**跑了 Gradle | 一律经 `.work/enter.sh '命令'` 在容器内执行 |
| `aapt2 ... Syntax error` | AGP 从 Maven 拉的 aapt2 是 x86_64 | 已在 `gradle.properties` 设 `android.aapt2FromMavenOverride` 指向 build-tools/35.0.1（aarch64） |
| release 包缺 manifest | aarch64 + arm64 aapt2 时资源优化会产出空资源表 | 已设 `android.enableResourceOptimizations=false`（照搬主项目结论） |
| `git push` 报权限/不存在 | 主机（Termux）DNS 只返回 IPv6 且无 IPv6 路由 | 从容器取 IPv4，再 `GIT_SSH_COMMAND="ssh -o HostName=<ip> -p 443" git push` |
| 容器连不上外网（超时） | 容器走 tun0，出口时通时不通 | JVM 加 `-Djava.net.preferIPv4Stack=true`；失败多为瞬时，重试 |
| 容器里看不到项目 | bind 挂载会掉 | 重新挂：`su -c 'mount -o bind <home>/jmc <chroot>/data/.../home/jmc'`（android-sdk、.android 同理） |
| 写文件失败/文件被清空 | **Termux 的 `/tmp` 不可写** | 临时文件一律放 `.work/`；写文件用 heredoc 整体写，不要"切分+重定向拼接" |

## 四、常用命令

```bash
# 构建 APK（容器内）
.work/enter.sh 'bash .work/tc-apk.sh'
# 安装（用户已授权 root 直接安装）
su -c "pm install -r -t app-android/build/outputs/apk/debug/app-android-debug.apk"
# 全量单测
.work/enter.sh 'bash .work/tc-all.sh'
# 真实链路探针（EH 免登录；Pica 需登录，密码走环境变量）
.work/probe.sh eh "关键词" 1 full
TRICOMIX_PASS='…' .work/probe.sh pica "关键词" 1 full 账号
# JM 源探针（内存存储 + JDK 生成密钥，不需要 Android）
.work/enter.sh "cd TriComiX && gradle -q :probe:jmProbe -PjmArgs='test'"
# 开发期核对真实响应
.work/enter.sh "cd TriComiX && gradle -q :probe:rawDump -PrawArgs='<comicId>'"
```

## 五、下一步（逐屏迁入，按体积从小到大）

1. `HomeScreen`（20 KB）：`repo.promote()/latest()/weekIssues()` → `ComicSource.home()`，按能力显隐；
2. `SearchScreen`（38 KB）→ `search()` → `Paged<Comic>`；
3. `DetailScreen`（56 KB）→ `detail()` → `ComicDetail`（章节取 `chapters`）；
4. `ReaderScreen`（48 KB）→ `pages()` + `imageRequest()`（EH 的 `hath`、JM 的反切片标记）；
5. 接 `JmNavHost` 式导航，替换现有三屏脚手架。

原文在 `app-android/ported-ui/`（**不参与编译**）。每迁入一屏：编译 + 168 单测 + 重装 + 设备上点一遍。

## 六、过程教训（血泪，别再犯）

1. **多行 sed/perl 替换在本机极不可靠**（本会话失败了近十次，多因花括号/斜杠/`$` 转义）。
   可靠做法：**整体重写文件**（heredoc），或用 `\Q…\E` 加不同分隔符，或先打印上下文再精确替换；
2. **写完文件必须验证产物**：我曾因 `/tmp` 不可写导致重定向把一个文档**清空后提交并推送**，只能重建；
3. **"编译通过"不等于"语义完整"**：Pica 的章节一直为空，根因是 `detail()` 里写死
   `chapters = emptyList()` —— 编译与单测都发现不了，只有真机/探针链路能暴露；
4. **超时 ≠ 协议错**：本会话多次把网络超时误判成协议问题，浪费了不少轮次。先区分
   "HTTP 000 超时" 与 "服务端返回错误码"，再下结论；
5. **别把猜测当事实**：我早先说 EH "解析被污染、在此环境不可能通"，后来证明那只是瞬时网络状态。

---

## 七、进展更新（懒移植阶段）

### 已完成

| 项 | 说明 |
| --- | --- |
| 主题 | JMNeXt 的 `theme/` 五个文件（Palettes/Shapes/Theme/ThemeStyle/Tokens，约 58 KB）已迁入并生效（`JmTheme`） |
| 组件 | JMNeXt 的 `components/` 八个文件已迁入并通过编译（ComicCard/Glass/GlassTopBar/FloatingBottomBar/StateBox/LoadMoreFooter/ItemMotion/AmbientBackdrop） |
| 壁纸 | `LocalWallpaper` / `WallpaperStore` 原样迁入（含正确的包名映射：jm 模块 → `com.tricomix.jm.*`，app 自身 → `com.tricomix.android.*`） |
| 关键抽象面 | `ComicCard` 已改为**只吃 `core.Comic`**（主项目里它吃 JM 的 `ListItem`） |
| 界面能力化 | 搜索/首页/收藏/登录四个按钮按 `Capability` 显隐，并**显示"该源不支持：…"** |
| 屏幕原文 | 看漫画四个屏幕 + NavHost（约 205 KB）已入库 `app-android/ported-ui/`，**不参与编译**，作为逐屏适配的原文 |

### 已实测（真实环境）

- **EH**：搜索 25 条 → 详情（作者/标签）→ 章节 1 → **页列表 16 页** → 第 1 页图片地址（本轮再次复跑确认）；
- **Pica**：登录 → 搜索 20 条 → 详情 → 章节 → 页列表 26 页 → 图片地址（含用户手机实测登录与搜索）；
- **JM**：探针实证 45 条搜索 + 详情与标签；**真机待验证**。

### 待办（按顺序）

1. 按 `ui-port-plan.md` 第六节的清单实写 HomeScreen（换数据管道、留布局），随后 Search / Detail / Reader；
2. Reader 要一并处理 EH 的 `hath` 与 JM 的**反切片标记**（`ImageRequest.unscramble`）；
3. 会话授权过的账号实测：用 JM 源在真机完成「搜索 → 详情 → 阅读」（目标 (d) 的判定项之一）。

## 八、近期变更（许可证与阅读页）

### 三家仓库的许可证已统一为 AGPL-3.0

| 仓库 | LICENSE | 说明 |
| --- | --- | --- |
| `JMNeXt` | AGPL-3.0（34,523 字节） | 原有 |
| `JMNeXt4QtDesktop` | AGPL-3.0（34,523 字节） | **本次补上**（取自主项目 LICENSE，逐字节一致），README 增加许可证说明与分发要求 |
| `TriComiX` | AGPL-3.0（34,523 字节） | 原有 |

意义：TriComiX 搬迁 JMNeXt 的界面代码、Qt 线将来复用 TriComiX 的源实现，**在许可上都没有障碍**。

### 界面侧

- 阅读页现在显示搬迁自 JMNeXt 的 `LiteFeatures` 预取窗口（lite 档 `1/3`、full 档 `2/8`）——
  让搬来的调优参数**参与界面**，而不是躺在代码里当死代码；
- 首页按**分区**展示（分区标题 + 各自卡片），搜索/收藏会清空分区状态，避免残留；
- 结果装载统一走一个入口并按返回类型分派（`List<Section>` 或 `Paged<Comic>`）。

### 待办（承接第七节）

1. **阅读页预取**：目前只加载当前页；`LiteFeatures.prefetchAfter` 已取到 3，
   但**尚未真正预取**（只显示在界面上）。要做的是加一层按页缓存 + 预取当前页之后 N 页；
2. `SearchScreen` / `DetailScreen` 的布局要素继续从 `ported-ui/` 吸收；
3. 真机验证：JM 源的「搜索 → 详情 → 阅读」（目标 (d) 判定项）。

## 九、开源与脱敏（2026-10-07）

- 仓库 `moyingyilang/TriComiX` 已改为 **public**；GitHub Project 已改名为「**漫画**」；
- **脱敏**：含哔咔签名密钥的文件已从**全部 git 历史**移除（pickaxe 对密钥与 `api-key` 字面量均为 0 命中），
  当前版本零硬编码，改为环境变量/本地文件提供，缺失时明确报错；
- **残留风险（如实记录）**：该密钥曾被推送，托管方可能短期保留未引用对象 ⇒ 应视为已暴露；
- 三家仓库许可证统一 AGPL-3.0。

---

## 十、下次从哪里开始（给接手的人）

### 先确认环境（本文件第三节的七个坑都还会遇到）

```bash
cd /data/data/com.termux/files/home/jmc/TriComiX
.work/enter.sh 'bash .work/tc-all.sh'        # 全量单测（应全绿）
.work/enter.sh 'bash .work/tc-apk.sh'        # 构建 APK
su -c "pm install -r -t app-android/build/outputs/apk/debug/app-android-debug.apk"
```

### 当前该做什么

按 `docs/ui-port-plan.md` 逐屏迁入，**顺序建议从 Detail 开始**（比 Search 少交互、比 Reader 少状态）：

1. **Detail**：读 `ported-ui/screens/detail/DetailScreen.kt`（56 KB，先只 grep 结构），
   按第七节同款清单换数据管道（`repo.album(id)` → `detail()`），保留其布局；
2. **Search**：按第六节清单；注意已记录的**接口缺口**（排序/日期筛选、热词、随机推荐没有位置）；
3. **Reader**：最后做，要同时处理 EH 的 `hath`（嵌在主机名里）与 JM 的反切片标记
   （`ImageRequest.unscramble`）；预取已在 app 里实现，可直接复用其思路。

### 两个已记录的接口缺口（做界面之前先决定要不要扩接口）

- 搜索侧：筛选/热词/随机推荐在 `ComicSource` 里没有位置；
- 收藏侧：只有 `favorites(page)` 读，没有写（`toggleFavorite` 与 `FAVORITE_WRITE` 待加）。
  两边底层能力其实都在（EH 的请求地址、JM 的实现），缺的是接口位置。

### 尚未拿到真机证据的一项

**JM 源**：探针实证 45 条搜索 + 详情/标签；真机上还没点过。已修掉"未做主机发现"的缺陷
（`JmSource` 现在首次使用时自动 `bootstrap()`），那很可能就是此前失败的原因。

### 工作方式（本项目已经验证有效的）

- 改动前先**核对锚点在文件里是否唯一**；多行改写与按行号删除在本机**都出过错**，
  可靠做法是**整体重写文件**；
- 每步：编译 + 单测 + 装包 + 核对 `git status` 后再 commit；
- **构建失败时"安装成功"装的是旧包** —— 本会话踩过两次，务必看构建结果再看安装结果。

---

## 十一、界面搬迁进展（本次会话，界面为主）

### 结构：脚手架已瘦身为路由文件

`MainActivity.kt` 从约 500 行降到 265 行（只剩路由、源切换、状态），三个屏各自独立：

| 文件 | 作用 |
| --- | --- |
| `ui/screens/SearchScreen.kt` | 搜索屏：关键词、最近搜索、分页加载更多、`FloatingBottomBar` 三页签、能力显隐 |
| `ui/screens/DetailScreen.kt` | 详情屏：封面、标题、作者、标签、简介、章节数、章节列表、从头开始、继续阅读 |
| `ui/screens/ReaderScreen.kt` | 阅读屏：反切片、按页缓存、预取与节流、**左右滑动翻页**、玻璃控制条 |
| `ui/screens/ComicResults.kt` | 结果列表：首页分区横向行（`jmAnimateItem` 动画）、搜索平铺 |
| `ui/screens/LoginForm.kt` | 登录表单（状态由调用方持有，界面不碰持久化） |
| `ui/NavDrawer.kt` | 侧栏（做法搬迁自 `moyingyilang.github.io` 的 SideNav/NavTree） |

### 已接入的 JMNeXt 自家件

主题 `JmTheme`；`GlassTopBar`；`AmbientBackdrop`；`ComicCard`；`FloatingBottomBar`；
`LoadingBox`/`ErrorBox`/`MessageState`；`LoadMoreFooter`；`ItemMotion`；`GlassSurface`；
`LiteFeatures`（预取窗口）；`JmImage`/`ImageUnscramble`（反切片）；`ReadProgressStore`（继续阅读）。

### 侧栏（按使用者定位）

结构：**图源切换**（当前源高亮，切换后**重启保留**）+ **登录状态行** + 内容入口
（首页/搜索/收藏；历史/追更按能力剪空）+ 设置/关于。
做法照搬站点侧栏四条原则：单一数据源、剪空（`pruneEmpty` 对应 `Capability`）、当前项高亮、玻璃外壳。
**形态仍是内容区叠加，不是标准抽屉**（`ModalNavigationDrawer`）—— 待做。

### 本次会话追加的接口能力

| 能力/方法 | 状态 |
| --- | --- |
| `Capability.FAVORITE_WRITE` + `toggleFavorite(comicId)` | 已加（默认"不支持"）；**JM 源已实现**，EH/Pica 未接 |
| `Capability.FOLLOW` | 只加能力位（无方法），无源实现故侧栏追更入口被剪空 |

### 仍未做（按价值排序）

1. **侧栏改成标准抽屉**（手势滑出、遮罩、返回键关闭）；
2. **图片质量 / 预取窗口做成真设置**（目前是编译期常量 `ImageQuality.HIGH` 与 `LiteFeatures` 1/3；
   要做就得把值提升为可读写并让阅读器读取，**不能只放一个点了没反应的开关**）；
3. **JmNavHost 主体与 HomeScreen 完整布局**（方案见 `ui-port-plan.md` 第十节）；
4. 双指缩放、下拉刷新、EH 收藏写、分类/画师入口。
