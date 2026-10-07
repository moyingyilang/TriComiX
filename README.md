# TriComiX

**统一多源漫画阅读器。** 一个核心，多个可插拔的内容源。

> 状态：**设计阶段**。当前仓库只有文档与结构约定，尚无可用代码。

## 定位

把原本各自独立的几个阅读需求收进同一个客户端：统一的界面、统一的收藏与历史、统一的凭据保护，
而每个内容源只负责自己的协议。

首批三个源（内部代号）：

| 代号 | 内容源 | 协议性质 |
| --- | --- | --- |
| `jm` | 同名主项目现有的源 | 私有 API + 客户端签名 |
| `pica` | PicACG | 私有 API + 请求签名 + 时间同步 |
| `eh` | E-Hentai | 网页站（登录态 + 分页 + 反爬） |

**源名只出现在代码与文档里**，不出现在仓库描述、topics 或包名中 —— 这是刻意的（见 `docs/naming.md`）。

## 技术链

- Kotlin + Compose Multiplatform（Android 与桌面共用 `core` 与各源）
- Kotlin Coroutines（源接口全部 `suspend` + `Result`，异常不上抛到界面层）
- Gradle（版本与主项目保持一致）

## 计划中的模块

```
core/          统一模型 + ComicSource 接口 + 错误类型（纯 Kotlin）
source-jm/     第一个源（从主项目搬迁，行为保持不变）
source-pica/   第二个源
source-eh/     第三个源（未定）
app-desktop/   桌面壳（先做，本地迭代最快）
app-android/   Android 壳（后做）
docs/          设计、命名、协议说明
```

## 不做什么

- 不做运行时下载源的扩展商店（第一版用编译期源 + 开关）
- 不追求源的数量
- **不复制任何第三方实现**；无许可证项目的代码一律不并入（详见设计文档第 7 节）
- 不把任何服务端密钥写入仓库

## 许可证

AGPL-3.0（与主项目一致）。

## 协议笔记与测试方式

- `docs/eh-protocol.md`：E-Hentai 的域名、路径、解析器地图、`hath` 机制与实现顺序；
- `docs/pica-protocol.md`：PicACG 的端点、请求头、5 段签名、时间同步与档位映射；
- `docs/unverified.md`：**已验证 / 未验证清单**（含逐能力对照与验证方式，验证时先看这份）。

**命令行测试（不需要界面）**：

```bash
# 只打印将要访问的地址，不联网
probe.sh eh "关键词" 1 dry

# 完整链路：列表 -> 详情 -> 页列表 -> 取第 1 页图片地址
probe.sh eh "关键词" 1 full

# Pica（多数端点需要登录令牌）
probe.sh pica "关键词" 1
```

探针每一步失败都会打印**失败在哪一环**与错误类别（`Unsupported` / `Unknown` / `Parse`），
不会把失败伪装成空结果。

**所有涉及真实站点的行为都未经本项目验证**：解析选择器、端点、签名都来自静态阅读，
必须由使用者在自己的网络环境下确认。

---

## 构建与运行

### 模块

| 模块 | 作用 |
| --- | --- |
| `core` | 统一模型与 `ComicSource` 接口（只依赖标准库，可离线构建） |
| `source-jm` | JMComic 源（含从既有实现搬迁来的数据层与适配器） |
| `source-pica` | PicACG 源（协议层 + 客户端 + 适配器） |
| `source-eh` | E-Hentai 源（抓取型：列表/详情/图片页解析） |
| `probe` | 命令行探针：不依赖界面即可验证各源链路 |
| `app-android` | Android 应用（Compose；界面沿用 JMNeXt：主题、组件、首页分区等） |

### 构建

```bash
# 全量单测
gradle test

# Android 测试 APK（debug 签名）
gradle :app-android:assembleDebug
# 产物：app-android/build/outputs/apk/debug/app-android-debug.apk

# 只装到设备
adb install -r app-android/build/outputs/apk/debug/app-android-debug.apk
```

### 凭据（重要）

**本仓库不含任何密钥**。PicACG 源需要凭据，通过以下任一方式提供：

```bash
export TRICOMIX_PICA_APIKEY=…
export TRICOMIX_PICA_SIGNINGKEY=…
# 或写入 ~/.tricomix/pica-keys.properties（apiKey=… / signingKey=…）
```

缺失时不会静默失败，而是明确报错并指出该设置哪个变量。

### 探针（不需要界面）

```bash
# 不联网自检（12 项）
gradle -q :probe:run --args="--selfcheck"

# 真实链路：EH 免登录
gradle -q :probe:run --args="--source eh --query 关键词 --page 1 --full"

# 真实链路：Pica（需凭据）
gradle -q :probe:run --args="--source pica --query 关键词 --page 1 --full --user 账号"

# 定向核对某部作品（长作品分批取页用）
gradle -q :probe:ehProbe -PehArgs="<gid> <token>"
```

### 文档

| 文件 | 内容 |
| --- | --- |
| `docs/status.md` | **接手先读**：现状、环境坑、常用命令、过程教训 |
| `docs/unverified.md` | 证据账本：哪些经过真实环境验证、哪些还没有 |
| `docs/ui-port-plan.md` | 界面搬迁方案（JMNeXt 屏幕清单与逐屏改写清单） |
| `docs/pica-protocol.md` | PicACG 协议：端点、请求头、5 段签名、档位映射 |
| `docs/eh-protocol.md` | E-Hentai 协议：域名、解析器地图、`hath` 机制 |
