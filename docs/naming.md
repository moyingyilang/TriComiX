# 命名记录

## 名字

**TriComiX** —— 仓库名 `TriComiX`，包名/命名空间 `com.tricomix`，可执行文件 `tricomix`。

## 构成

| 片段 | 含义 |
| --- | --- |
| `Tri` | 三个源统一在一个客户端里 |
| `Comi` | 漫画（comic） |
| `X` | cross（跨平台、跨源）；与既有项目 `JMNeXt` 的收尾大写 X 呼应 |

## 查重结论（建库前已执行）

| 查询 | 结果 |
| --- | --- |
| GitHub 仓库名 `tricomix` | **无任何同名仓库** |
| 漫画阅读器领域 `tricomix comic` | 无占用 |
| `ComiX` 系列（`comix reader` 等） | 只有若干零星小项目，无同名者 |

## 两条硬约束（候选筛选时使用）

1. **绝不出现 `JAV` 片段** —— 属成人向词，会把关联重新带入仓库元信息；
2. **不保留完整的 `Pica` / `Eh` / `JM` 商标式片段** —— 既有商标问题，也会把成人向关联写进元信息
   （主项目曾因此类关联在搜索引擎中难以被发现）。

按这两条，被否决的候选包括 `Vica`、`Picomi`（残留 Pica 影子）、`Jaview`（命中第 1 条）等。

## 命名规则（对外元信息）

- 仓库描述：`A Kotlin/Compose Multiplatform comic reader with pluggable sources.`
- topics：`kotlin`、`compose-multiplatform`、`android`、`desktop-app`、`comic-reader`、`multi-source`
- 三个源的名字**只出现在代码模块名、文档与提交信息里**

## 已知取舍

- `ComiX` 的写法与 ComiXology 有一点联想（纯个人项目可接受；若在意，可退化为 `TriComic`，代价是失去与 `JMNeXt` 的家族感）；
- `Tri` 把"三家"写进了名字 —— 若将来扩到第四家源，名字会比功能"老"。
