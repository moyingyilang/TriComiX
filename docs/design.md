# TriComiX 设计草案

> 状态：**草案，尚未落地**。第一步只做"抽象 + JM 回归"，不改现有行为。
> 本文档**不含任何凭据/密钥值**，也不应加入。

## 0. 目标与非目标

**目标**：一个核心阅读器 + 可插拔的"源"，首批三家（JMComic / Pica / E-Hentai）；
沿用现有技术链（Kotlin + Compose Multiplatform，`:shared` 数据层 + Android/桌面界面）；
源的差异不泄漏到界面层。

**非目标**：不做扩展商店与运行时下载源（Mihon 那套独立 APK 机制过重，第一版用编译期源 + 开关）；
不追求源数量；不发布到 Google Play；**不复制任何第三方实现**（见第 7 节）。

## 1. 为什么必然是"重写重实现"

| 参考实现 | 技术链 | 代码能否复用 |
| --- | --- | --- |
| JMComic 客户端（存档） | Java + `android.support.*` 26 + Sugar ORM + ButterKnife | 不能 |
| `raoxwup/haka_comic`（★1.5k） | Dart/Flutter，**GPL-3.0** | 不直接复用；但许可允许并入 AGPL 项目（需署名） |
| Pica 客户端若干 | Dart / Python-Qt / TypeScript | 不能 |
| EhViewer 系列 | Java / Kotlin，但**多数无许可证** | **不可**（默认保留所有权利） |

技术链差异已决定只能做**协议级重实现**。许可证层面的实际约束只剩两条：
并入 GPL-3.0 代码要署名同许可；**无许可证项目只能当行为参照**。

## 2. 源接口（第一版草案）

```kotlin
interface ComicSource {
    val id: String                                  // "jm" / "pica" / "eh"
    val capabilities: Set<Capability>               // 登录、搜索、收藏、下载…

    suspend fun login(credential: SourceCredential): Result<Session>
    suspend fun logout()
    suspend fun home(): Result<List<Section>>
    suspend fun search(query: String, page: Int): Result<Paged<ListItem>>
    suspend fun detail(comicId: String): Result<ComicDetail>
    suspend fun chapters(comicId: String): Result<List<Chapter>>
    suspend fun pages(chapterId: String): Result<List<PageRef>>
    suspend fun imageRequest(page: PageRef, quality: ImageQuality): Result<ImageRequest>
    suspend fun favorites(page: Int): Result<Paged<ListItem>>
    suspend fun history(page: Int): Result<Paged<ListItem>>
}
```

设计要点：

- `PageRef` / `ImageRequest` 是必需的**间接层**，因为三家取图方式根本不同：
  JM 的图片**被打乱**（需按算法还原）；EH 的图片**按分页组织**且每张需要会过期的 `hath` 键；
  Pica 同一章有多个**质量档位**，取图前先要决定档位。
- 源接口全部 `suspend` + `Result`，异常不抛到界面层；失败要能定位（沿用现有日志规范）。
- 统一模型只放"三家都能表达"的字段；源特有字段进 `extra: Map<String, String>`，**不做万能模型**。

## 3. 统一模型与各家映射

| 概念 | JM | Pica | EH |
| --- | --- | --- | --- |
| 作品 | album | comic | gallery |
| 章节 | chapter | chapter(eps) | ——（EH 是"分页"而非章节） |
| 阅读单元 | chapter | chapter | page（每页含若干图） |
| 图片地址 | 需按算法还原 | 直链（带质量档） | 直链 + `hath` 键（会过期） |
| 登录 | 账号 | 账号 | cookie（并需注意站点访问限制） |
| 收藏 | 服务端 | 服务端 | 服务端 |

**无法统一的点要提前认账**：EH 的"页"与另两家的"章"不是同一概念；JM 的打乱图是源私有逻辑；
Pica 的质量档位在另两家没有对应物。界面层据此应接受"页序列"而不是"章"。

## 4. 凭据与隐私政策

- 所有源统一走现有 `SecureStore` + `SecretKeyProvider` —— **口令保护一处实现，三家受益**
  （这是 issue #12/#14 那套工作的直接复用）；
- **任何服务端凭据/密钥都不进仓库**（含 git 历史）；第三方签名密钥一律**运行时提供**（环境变量或本地文件），缺失时明确报错；
- 文档、日志、提交信息中都不出现密钥值。

## 5. 分阶段计划

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| **0** | 抽出 `ComicSource` 接口，让 **JM 成为第一个实现**，行为与现在完全一致 | 现有测试与自检全绿；界面无感知变化 |
| **1** | 新增 Pica 源（协议已研究：请求头、签名原文顺序、HMAC-SHA256、`Server-Time` 同步、证书固定） | 列表/搜索/详情/阅读可用；签名与时间同步有单测 |
| **2** | 评估 EH：先做可行性判断（是否接受"随时失效 + 低频手动使用 + 不抄任何客户端代码"） | 判定文档；决定做或不做 |

每阶段必须：能编译、有测试、版本号与发布流程不受影响。

## 6. 已知风险

- **EH 的服务约束与许可证无关**：网页站 + 反爬 + 封禁风险，属长期不稳定来源；
- **Pica 的密钥问题**：开源版**不含密钥**，由使用者在运行时提供；该密钥曾被推送过，应视为已暴露（详见 docs/pica-protocol.md 文末）；
- **维护成本**：三家 API 各自会变，单人多源压力大；建议阶段 1 之后再评估是否继续扩源；
- **发行**：基本告别主流应用商店。

## 7. 工程规则（不可协商）

1. **重实现，不搬运**；引用协议时用描述，不贴原代码；
2. 若确需并入 **GPL-3.0** 代码：署名 + 同许可（本项目 AGPL-3.0 已满足）+ 文件头注明来源；
3. **绝不**并入**无许可证**项目的代码（EhViewer 系列多数无许可证）；
4. 原 APK 与反编译产物**不进入本仓库**；
5. 不逐行翻译反编译产物（仍属衍生），只依协议文档与行为观察重写。

## 8. 待决策

- Pica 的签名密钥政策；
- EH 是否投入（以及以何种形态）；
- Qt 线（`JMNeXt4QtDesktop`）是否也支持多源，还是专注 JM 单源；
- `JMNeXt4QtDesktop` 补许可证（目前无 LICENSE 文件，默认"保留所有权利"，与主项目 AGPL 定位冲突）。
