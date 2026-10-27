# CoSky 8.0 重构路线图

> 设计依据:[architecture-redesign.md](architecture-redesign.md)(下文 §x 均指该文档章节)。
> 本文档跟踪**怎么做、做到哪**;设计本身的变更只改设计文档,并在这里记录影响。

---

## 1. 目标与范围

- 交付 **CoSky 8.0.0**:按目标架构完整重写,不兼容 6.x 的 API、Redis 键结构和客户端。
- 6.x 进入维护期:只修安全问题和严重缺陷,不再加功能。
- 版本序列:`8.0.0-alpha.N`(开发预览,API 可变)→ `8.0.0-beta.N`(API 冻结)→ `8.0.0-rc.N` → `8.0.0`。起始版本为 `8.0.0-alpha.1`。
- 8.0 的范围就是设计文档 §3–§7;明确不做的事见设计文档 D7。

## 2. 里程碑

| 里程碑 | 内容 | 完成标志 |
|---|---|---|
| **M0 设计定稿** | 设计文档评审通过,开放问题(本文 §6)全部关闭 | 维护者确认 |
| **M1 契约** | `api/` 完成:proto、生成代码、校验规则、错误码;构建骨架就位 | W1 合并 |
| **M2 语义** | Store SPI + 内存实现 + conformance 套件;client 在假服务上跑通 | W2 全部合并 |
| **M3 组件完成** | server、store-redis 完成并通过 conformance;client 对接真实 server | W3 全部合并 |
| **M4 可发布** | 端到端与故障注入通过、Dashboard 切换、迁移工具、文档、性能目标(§7.10)达成 | W4 全部合并 |
| **8.0.0 GA** | RC 在至少一个真实环境试运行两周无 P0/P1 问题 | GitHub Release |

## 3. 任务波次

同一波次内的任务互不修改相同文件,可并行;后一波次从 `v8` 上前一波次的合并结果切出。

```
W1  骨架 + api
     │
     ├──────────────┐
W2  store + 一致性   client + starter(假服务)
     │
     ├──────────────┐
W3  server          store-redis
     │              │
     └──────┬───────┘
W4  端到端+示例 ∥ Dashboard ∥ 迁移工具 ∥ 文档
```

### W1 构建骨架 + `api/`

- **范围**
  - 新模块目录(D8)与 `archivesName = "cosky-${project.name}"`;`settings.gradle.kts` 只包含新模块。
  - 版本目录加入 protobuf、grpc-java、grpc-kotlin、kotlinx-coroutines、kotlinx-collections-immutable、Testcontainers。
  - Detekt `ForbiddenImport` 禁止 `reactor.*`(允许清单见 §3.2)。
  - 删除旧模块:`cosky-core`、`cosky-config`、`cosky-discovery`、`cosky-spring-cloud-*`、`cosky-rest-api`、`examples/`;`cosky-bom`、`cosky-dependencies`、`cosky-test` 改为 `bom/`、`dependencies/`、`test/`;`code-coverage-report` 指向新模块。
  - `api/`:§4.1、§4.3 的 proto,protobuf + grpc-kotlin 代码生成;§4.2、§7.1 的命名与大小校验;§4.4 错误码与 status detail 定义;OpenAPI 混合生成(D18,供 Dashboard);项目版本改为 `8.0.0-alpha.1`。
- **不在范围**:任何 server / client / store 逻辑;`dashboard/`、`wiki/` 不动。
- **验收**:`./gradlew build` 通过;校验规则有完整的边界测试;proto 通过 `buf lint`(或等价检查)。

### W2a `store/` + `conformance-test/`

- **范围**:§5.0 Store SPI;内存实现;conformance 抽象测试类,覆盖:
  CAS 三种模式、批写原子性(任一失败全部不生效)、无变化不推进 revision、`changes` 续传 / 窗口外 `RevisionGoneException`、
  租约授予 / 批量保活 / 到期 / 撤销 / 挂载实例随租约删除、Config 历史截断、`dropNamespace`、`tryLock` 互斥与过期。
- **验收**:内存实现通过全部 conformance 用例;测试类可被 `store-redis` 直接继承复用。

### W2b `client/` + `spring-boot-starter/`

- **范围**:§6.5、§7.5 客户端全部行为;§7.7 starter 全部集成点;Java `CompletableFuture` 门面推迟到 8.1(见 §6 Q5)。
- **测试**:以测试内的 gRPC 假服务(grpc-kotlin in-process)模拟 server,覆盖断线续传、`gone` 重新 List、租约丢失重注册、快照启动。
- **验收**:不依赖 Spring 的 `client` 模块无 `reactor` 依赖;starter 有 Spring Boot 集成测试(ConfigData 加载、刷新、注册、发现)。

### W3a `server/`

- **范围**:§6.1–§6.4、§7.2–§7.4、§7.6、§7.8、§7.9;运行在内存 Store 上。
- **验收**:client 驱动的协议级 conformance 用例全部通过;REST 与 gRPC 行为一致的对照测试;鉴权矩阵测试。

### W3b `store-redis/`

- **范围**:§5.1–§5.3 键布局与 4 个脚本;Lettuce 协程桥接;Standalone / Cluster 均需验证。
- **验收**:继承 W2a conformance 用例并全部通过(Testcontainers Redis);Cluster 模式下所有脚本键均显式声明。

### W4a 端到端 + 示例 + 部署

- 全链路测试(server + store-redis + client);Toxiproxy 故障注入(§7.11);重写 provider / consumer 示例;
  Dockerfile、k8s 清单 / Helm;JMH 与压测达成 §7.10 目标。

### W4b Dashboard

- OpenAPI 生成客户端;OIDC(PKCE)与 token 登录;SSE 实时刷新;页面对应新资源模型(Config、Instance、ServiceAccount、Role、RoleBinding、Views)。

### W4c 迁移工具

- 读取 6.x Redis 键结构中的 Config(含当前版本,可选历史),通过 8.0 REST API 导入;实例不迁移(重新注册)。
  用户与角色不迁移(身份模型已变),输出一份对照报告供手工重建。

### W4d 文档

- wiki 中英文按新架构重写;README;`AGENTS.md`;6.x → 8.0 迁移指南。

## 4. 协作方式

- **主线分支 `v8`**:8.0 的全部工作汇入 `v8`,`main` 在 8.0 GA 前继续承载 6.x。`v8` 定期合入 `main` 的修复。
- **任务分支**:每个任务由一个**独立子会话**在自己的 worktree 中完成,从 `v8` 最新提交切出,提交到自己的分支
  (`claude/<任务>-…`)。
- **一个任务一个 PR**:任务完成后向 `v8` 提 PR,标题遵循 Conventional Commits,squash 合并;合并前由协调会话审查并独立重跑
  `CI=true ./gradlew build`,PR 的 CI 必须通过。审查意见在同一任务分支上修改,不另开 PR。
- **文档变更**同样经 PR 进入 `v8`;只改文档的 PR 可以不等 6.x 流水线跑完。
- **协调会话**负责:编写任务说明、审查 PR(对照设计文档)、合并到 `v8`、更新设计文档 / 本路线图 / `AGENTS.md`、
  与维护者讨论设计变更。
- **过渡**:W1 已在本地分支 `claude/project-architecture-redesign-181c01` 上完成审查。恢复开工时,先把该分支变基到 `v8`,
  以 W1 PR 合入 `v8`,同时把 `AGENTS.md` 的分支说明改为本节规则;此后不再使用该本地集成分支。
- 实现中发现设计需要调整时,子会话**停下来说明**,由协调会话与维护者讨论并更新设计文档,不在代码中悄悄偏离。
- **通用完成标准**:`./gradlew build` 通过(Detekt、license header、覆盖率 90% 行 / 70% 分支);新增行为有测试;
  不修改任务范围外的模块;提交信息遵循 Conventional Commits。

## 5. 进度

| 任务 | 子会话 | 分支 | 状态 |
|---|---|---|---|
| M0 设计定稿 | 协调会话 | `v8`(#1001) | ✅ 完成 |
| W1 骨架 + api | w1-api | `claude/w1-api-c1af77` | 已审查,待变基后以 PR 合入 `v8` |
| W2a store + 一致性 | — | — | 未开始 |
| W2b client + starter | — | — | 未开始 |
| W3a server | — | — | 未开始 |
| W3b store-redis | — | — | 未开始 |
| W4a 端到端 + 示例 + 部署 | — | — | 未开始 |
| W4b Dashboard | — | — | 未开始 |
| W4c 迁移工具 | — | — | 未开始 |
| W4d 文档 | — | — | 未开始 |

## 6. 已关闭的问题

M0 的开放问题已全部按推荐采纳,结论记录在设计文档 §1(D10–D16)。

| # | 问题 | 结论 |
|---|---|---|
| Q1 | Spring 配置加载方式 | ConfigData API(D10) |
| Q2 | Dashboard 部署 | server 托管,也支持独立部署(D11) |
| Q3 | 审计 | 结构化日志(D12) |
| Q4 | 删除 namespace | 只允许删除空的(D13) |
| Q5 | Java 门面 | 推迟到 8.1(D14) |
| Q6 | server gRPC 接入 | Spring gRPC(D15) |
| Q7 | 性能目标 | 采纳设计文档 §7.10(D16) |

## 7. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| W3a server 体量最大,成为关键路径 | M3 延期 | W3a 内部再拆:先 ResourceService + WatchService,后 LeaseService + 鉴权 + ViewService |
| Spring Boot 4 / Spring gRPC 版本兼容 | 构建与集成受阻 | W1 先做最小可运行验证 |
| Redis Cluster 下 Stream 与脚本的行为差异 | W3b 返工 | conformance 同时跑 Standalone 与 Cluster |
| 8.0 不兼容导致用户迁移成本高 | 采用受阻 | W4c 迁移工具 + 迁移指南;6.x 维护期 |
