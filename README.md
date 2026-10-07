# TriComiX

**一个核心，多个可插拔内容源。** 使用 Kotlin 与 Compose Multiplatform 实现的多源漫画阅读器：
界面与业务逻辑仅依赖统一的 `ComicSource` 接口，每个内容源只负责自身的协议实现。

> **项目状态**：Android 测试版可安装（`0.2.0-preview`，tag `preview-0.2.0`，`applicationId` 为 `com.tricomix`）。
> 三个内容源均已在真实环境跑通（见[内容源验证状态](#内容源验证状态)）；界面沿用 JMNeXt，
> 搜索 / 详情 / 阅读三屏已迁入，首页采用分区横向排布，另有侧栏与登录表单。
> 仓库当前**没有 GitHub Release**，APK 需自行构建（见[快速开始](#快速开始)）。

## 定位

TriComiX 是在 [JMNeXt](https://github.com/moyingyilang/JMNeXt) 之后迭代开发的**三源统一阅读器**，
目标是把 EH、Pica、JM 三个原本各自独立的阅读器整合进同一个客户端：共用一套界面、一套收藏与历史、
一处凭据保护，各内容源只负责自身协议。界面、主题与部分组件引用 JMNeXt
（同一作者、同一许可证，见[致谢](#致谢)）；三个内容源的协议实现均为自行重写，不复制任何第三方实现。

TriComiX 与 JMNeXt 是两个独立项目：JMNeXt 继续按单源维护与发布，不引入多源架构；
本项目同样不会改动其界面层实现。

## 目录

- [定位](#定位)
- [架构与内容源](#架构与内容源)
- [内容源验证状态](#内容源验证状态)
- [快速开始](#快速开始)
- [命令行探针](#命令行探针)
- [凭据](#凭据)
- [已知边界与未完成项](#已知边界与未完成项)
- [文档](#文档)
- [致谢](#致谢)
- [许可](#许可)

## 架构与内容源

| 代号 | 内容源 | 协议性质 |
| --- | --- | --- |
| `jm` | 同名主项目现有的源（搬迁，行为保持一致） | 私有 API 与客户端签名 |
| `pica` | PicACG | 私有 API、请求签名与时间同步 |
| `eh` | E-Hentai | 网页站点（登录态、分页、反爬） |

**统一的部分**：一套界面、一套 `ComicSource` 接口、一套错误类型，以及一处凭据保护
（沿用 JMNeXt 的 `SecureStore` 与 `SecretKeyProvider`）。

**不做形式统一的部分**：EH 的「页」与另两家的「章」不是同一概念，JM 的图片需要按算法还原，
Pica 同一章节存在质量档位。这些差异被收敛在 `PageRef` 与 `ImageRequest` 这一层间接中，
界面只消费「页序列」，不假设每个源都有章节。源的差异不会泄漏到界面层。

界面**仅依据能力（`Capability`）决定入口显隐**，现有能力位为 LOGIN / SEARCH / HOME / FAVORITES /
HISTORY / DOWNLOAD / FAVORITE_WRITE / FOLLOW。源不具备的能力，对应入口会被移除或显示「该源不支持」，
不保留无响应的控件。

内容源代号仅出现在代码模块名、文档与提交信息中，不进入仓库描述与 topics，理由见
[`docs/naming.md`](docs/naming.md)。

## 内容源验证状态

下表是判断各源可用性的依据，完整证据账本见 [`docs/unverified.md`](docs/unverified.md)。

| 源 | 真实环境验证 | 真机验证 | 说明 |
| --- | --- | --- | --- |
| Pica | 全链路通过：登录、搜索 20 条、详情、章节、26 页页列表、取图 | 使用者实测「哔咔全正常」 | 修正三处协议差异后通过（`nonce` 固定、补 `User-Agent`、`app-build-version` 45） |
| EH | 全链路通过：搜索 25 条、详情、页列表、取图 | 图片修复待复验 | 长作品分批取页已验证（141 页全部带 imgkey，末页取图成功） |
| JM | 探针验证 45 条搜索与详情、标签 | 使用者实测可用 | 修正三处：主机发现、单篇作品以作品 id 作为章节、反切片参数与缓存键 |

**尚未验证或明确不支持**：EH 登录与收藏读取（已实现，未在真实环境使用）；EH 收藏写入
（接口已扩展 `FAVORITE_WRITE`，目前仅 JM 源实现）；Pica 首页与历史、EH 历史
（**不实现**，相关端点在任何可得资料中均不存在）；**会话不支持跨重启**
（Pica 令牌与 EH cookie 仅存在于运行内存中）。

测试证据：单元测试 **168 个**（JM 56 / Pica 40 / EH 72）全部通过，另有 app 侧搜索历史测试；
不联网自检 **12 项**。单元测试使用自写的合成样本，只能证明「解析逻辑对给定形状自洽」，
不能证明「真实页面即为该形状」；上表中每一项均另有真实响应核对。

## 快速开始

### 模块

| 模块 | 作用 |
| --- | --- |
| `core` | 统一模型、`ComicSource` 接口与错误类型（仅依赖标准库，可离线构建） |
| `source-jm` | JMComic 源（自 JMNeXt 搬迁的数据层与适配器） |
| `source-pica` | PicACG 源（协议层、客户端与适配器） |
| `source-eh` | E-Hentai 源（抓取型：列表、详情、图片页解析） |
| `probe` | 命令行探针：不依赖界面即可验证各源链路 |
| `app-android` | Android 应用（Compose；界面沿用 JMNeXt） |

`app-desktop` 尚未建立，界面适配先在 Android 上进行。

### 构建

```bash
gradle test                                   # 全部单元测试
gradle :app-android:assembleDebug             # 测试 APK（debug 签名）
# 产物：app-android/build/outputs/apk/debug/app-android-debug.apk

adb install -r app-android/build/outputs/apk/debug/app-android-debug.apk
```

应用参数：`compileSdk 37` / `minSdk 24` / `targetSdk 36`，`versionCode 2`，
`versionName 0.2.0-preview`。当前按 **lite 档**构建（`BuildConfig.LITE = true`），界面读取该常量。

> 在 aarch64 设备（Termux）上构建需要若干本机规避：`aapt2` 指向本机 arm64 版本、
> 关闭 AGP 的资源优化步骤、Gradle 需在容器内执行。相关说明见
> [`docs/status.md`](docs/status.md) 第三节。

### 界面

界面沿用 JMNeXt，已迁入的部分如下。

| 文件 | 作用 |
| --- | --- |
| `ui/screens/SearchScreen.kt` | 搜索：关键词、最近搜索、分页加载更多、底部三页签、能力显隐 |
| `ui/screens/DetailScreen.kt` | 详情：封面、标题、作者、标签、简介、章节列表、继续阅读 |
| `ui/screens/ReaderScreen.kt` | 阅读：反切片、按页缓存、预取与节流、左右滑动翻页、玻璃控制条 |
| `ui/screens/ComicResults.kt` | 结果列表：首页分区横向行、搜索平铺 |
| `ui/NavDrawer.kt` | 侧栏：图源切换（重启后保留）、登录状态、内容入口（按能力剪空）、设置与关于 |

`MainActivity.kt` 已精简为路由层（约 265 行）。引自 JMNeXt 的组件包括：`JmTheme`、`GlassTopBar`、
`AmbientBackdrop`、`ComicCard`（已改为仅依赖 `core.Comic`）、`FloatingBottomBar`、
`LoadingBox` / `ErrorBox` / `MessageState`、`LoadMoreFooter`、`ItemMotion`、`GlassSurface`、
`LiteFeatures`（预取窗口）、`JmImage` / `ImageUnscramble`（反切片）、`ReadProgressStore`（继续阅读）。

## 命令行探针

探针不依赖界面，可用于验证各源链路。

```bash
# 不联网自检（12 项）
gradle -q :probe:run --args="--selfcheck"

# 真实链路：EH（免登录）
gradle -q :probe:run --args="--source eh --query 关键词 --page 1 --full"

# 真实链路：Pica（需登录；密码经环境变量传入，避免出现在进程列表）
TRICOMIX_PASS='…' gradle -q :probe:run --args="--source pica --query 关键词 --page 1 --full --user 账号"

# 定向核对单部作品（长作品分批取页）
gradle -q :probe:ehProbe -PehArgs="<gid> <token>"

# 开发期核对原始响应（用于确认真实字段名）
gradle -q :probe:rawDump -PrawArgs="<comicId>"
```

探针在每一步失败时会输出**失败的环节**与错误类别（`Unsupported` / `Unknown` / `Parse`），
不会将失败表示为空结果。

## 凭据

PicACG 源需要两个常量（apiKey 与 HMAC 签名密钥）。按项目所有者的决定，这两个值当前**直接内置**于
`source-pica/…/PicaCredentials.kt`，仓库保持公开；环境变量与本地文件仍可覆盖，便于调试或更换密钥。

```bash
export TRICOMIX_PICA_APIKEY=…
export TRICOMIX_PICA_SIGNINGKEY=…
# 或写入 ~/.tricomix/pica-keys.properties（apiKey=… / signingKey=…）
```

**风险说明**：该签名密钥在此前一次推送中已经暴露，因此写入仓库并未新增暴露面；
但仓库公开意味着**任何人都可以取得这两个值**。该密钥不属于本项目可轮换的对象（属于对方客户端），
只能作为已知事实记录，详见 [`docs/pica-protocol.md`](docs/pica-protocol.md) 文末。

取值缺失或为空时不会静默失败，而是明确报错并指出需要设置的变量
（否则表现为「签名持续被拒」，排查困难）。

## 已知边界与未完成项

- **会话不支持跨重启**：Pica 令牌与 EH cookie 仅存在于运行内存中，重启后需重新登录；
- **界面迁移尚未完成**：JMNeXt 的 `JmNavHost` 主体、`HomeScreen` 完整布局、
  下拉刷新与上拉加载更多（`LoadMoreFooter` 已就位但尚未接线）、双指缩放、EH 收藏写入、
  分类与画师入口。侧栏目前为**内容区叠加**形态，尚未改为标准抽屉（`ModalNavigationDrawer`）；
- **设置项**当前仅「默认图源重启后保留」为有效项，图片质量与预取窗口仍为编译期常量；
- **两处已记录的接口缺口**：搜索的排序、日期筛选、热词与随机推荐在 `ComicSource` 中没有对应位置
  （界面暂不显示，不做形式支持）；收藏写入接口已补 `FAVORITE_WRITE`，但仅 JM 源接入。
  两处的底层能力均已具备，缺的是接口层的位置。

## 文档

| 文件 | 内容 |
| --- | --- |
| [`docs/status.md`](docs/status.md) | **新接手者优先阅读**：现状、环境问题、常用命令、过程教训、后续顺序 |
| [`docs/unverified.md`](docs/unverified.md) | 证据账本：哪些经过真实环境验证，哪些没有 |
| [`docs/ui-port-plan.md`](docs/ui-port-plan.md) | 界面搬迁方案：JMNeXt 屏幕清单、逐屏改写清单与三处接口缺口 |
| [`docs/design.md`](docs/design.md) | 设计草案：源接口、统一模型与各家映射、工程规则 |
| [`docs/naming.md`](docs/naming.md) | 命名记录与两条硬约束 |
| [`docs/pica-protocol.md`](docs/pica-protocol.md) | PicACG 协议：端点、请求头、5 段签名、时间同步、档位映射 |
| [`docs/eh-protocol.md`](docs/eh-protocol.md) | E-Hentai 协议：域名、解析器地图、`hath` 机制 |

## 致谢

- **[moyingyilang](https://github.com/moyingyilang) 的 [JMNeXt](https://github.com/moyingyilang/JMNeXt)**：
  本项目的基础。界面、主题与部分组件引用自该项目（同一作者，同一许可证 AGPL-3.0），
  源的适配与协议实现是在其之后自行迭代的。
- **[seven332](https://github.com/seven332) 的 [EhViewer](https://github.com/seven332/EhViewer)**：
  EH 源的思路来源。本项目具体阅读的是一份第三方分支的反编译产物
  （应用包名 `com.xjs.ehviewer` 2.0.2.3，其资源引用
  [xiaojieonly/Ehviewer_CN_SXJ](https://github.com/xiaojieonly/Ehviewer_CN_SXJ)），
  仅作结构阅读与协议参考，解析与请求链路均为自行重写，不复制其代码
  （该系列多数没有许可证，属明确禁区，见 [`docs/design.md`](docs/design.md) 第 7 节与
  [`docs/eh-protocol.md`](docs/eh-protocol.md)）。
- **[raoxwup](https://github.com/raoxwup) 的 [haka_comic](https://github.com/raoxwup/haka_comic)**
  （GPL-3.0）：屏蔽与列表过滤的设计思路来源，未共用代码。
- **JMNeXt 致谢中的全部项目同样适用**：**[tiann](https://github.com/tiann) 的
  [KernelSU](https://github.com/tiann/KernelSU)**（悬浮底栏与四栏切换的几何）、
  **[moyingyilang](https://github.com/moyingyilang) 的
  [moyingyilang.github.io](https://moyingyilang.github.io)**（UI 设计语言与设计令牌）、
  **raoxwup 的 haka_comic**，以及所有提交 issue 与在真机上验证修复的使用者。

## 许可

[AGPL-3.0](LICENSE)，与 JMNeXt 一致。

界面、主题与部分组件引自 [JMNeXt](https://github.com/moyingyilang/JMNeXt)（同一许可证）；
屏蔽功能的设计参考 [haka_comic](https://github.com/raoxwup/haka_comic)（GPL-3.0），未共用代码。
各内容源及其内容的权利归其权利人所有；本项目与各家官方无关，仅进行协议级重实现，
不复制任何第三方实现，也不并入无许可证项目的代码。
