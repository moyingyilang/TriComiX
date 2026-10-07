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
