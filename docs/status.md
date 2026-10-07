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
