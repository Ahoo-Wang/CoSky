# CoSky 8.0 重构路线图

> 设计依据:[architecture-redesign.md](architecture-redesign.md)(下文 §x 均指该文档章节)。
> 本文档跟踪**怎么做、做到哪**;设计本身的变更只改设计文档,并在这里记录影响。

---

## 1. 目标与范围

- 交付 **CoSky 8.0.0**:按目标架构完整重写,不兼容 6.x 的 API、Redis 键结构和客户端。
- 6.x 进入维护期:只修安全问题和严重缺陷,不再加功能。
- 版本序列:`8.0.0-alpha.N`(开发预览,API 可变)→ `8.0.0-beta.N`(API 冻结)→ `8.0.0-rc.N` → `8.0.0`。起始版本为 `8.0.0-alpha.1`。
- alpha 阶段不发布制品:API 还会变,只有 `api` 模块时对用户也没有用。W4a 扩展发布流程,M4 后开始发布 `8.0.0-beta.N`。
- 8.0 的范围就是设计文档 §3–§7;明确不做的事见设计文档 D7。

## 2. 里程碑

| 里程碑 | 内容 | 完成标志 |
|---|---|---|
| **M0 设计定稿** | 设计文档评审通过,开放问题(本文 §6)全部关闭 | 维护者确认 |
| **M1 契约** | `api/` 完成:proto、生成代码、校验规则、错误码;构建骨架就位 | W1a、W1b 合并 |
| **M2 语义** | Store SPI + 内存实现 + conformance 套件;client 在假服务上跑通;真实 Redis 贯通验证 | W2 全部合并 |
| **M3 组件完成** | server、store-redis 完成并通过 conformance;client 对接真实 server | W3 全部合并 |
| **M4 可发布** | 端到端与故障注入通过、Dashboard 切换、迁移工具、文档、性能目标(§7.10)达成 | W4 全部合并 |
| **8.0.0 GA** | RC 在至少一个真实环境试运行两周无 P0/P1 问题 | GitHub Release |

## 3. 任务波次

同一波次内的任务互不修改相同文件,可并行;后一波次从 `v8` 上前一波次的合并结果切出。

```
W1a 骨架 → W1b api
     │
     ├──────────────┐
W2a store + 一致性   W2b client + starter(假服务)
     │
W2c 贯通验证(真实 Redis + 最小 server)
     │
     ├──────────────┐
W3a server          W3b store-redis
     │              │
     └──────┬───────┘
W4  端到端+示例 ∥ Dashboard ∥ 迁移工具 ∥ 文档
```

### W1a 构建骨架

删除旧模块单独成一个 PR,避免 API 契约的评审淹没在删除里。

- **范围**
  - 新模块目录(D8),发布的 artifactId 统一加 `cosky-` 前缀;`settings.gradle.kts` 只包含新模块。
  - 版本目录加入 protobuf、grpc-java、grpc-kotlin、kotlinx-coroutines、kotlinx-collections-immutable、Testcontainers。
  - Detekt `ForbiddenImport` 禁止 `reactor.*`(允许清单见 §3.2)。
  - 删除旧模块:`cosky-core`、`cosky-config`、`cosky-discovery`、`cosky-spring-cloud-*`、`cosky-rest-api`、`examples/`;`cosky-bom`、`cosky-dependencies`、`cosky-test` 改为 `bom/`、`dependencies/`、`test/`;`code-coverage-report` 指向新模块;项目版本改为 `8.0.0-alpha.1`。
  - 工作流、`.github/labeler.yml`、`CONTRIBUTING.md`、`AGENTS.md` 不再引用旧模块;Dashboard 的 Playwright 用例在 W4b 前不阻塞 CI。
- **不在范围**:`dashboard/`、`wiki/`、README(仍是已发布的 6.x 坐标,W4d 重写)、`k8s/`(W4a 重写)。
- **验收**:`CI=true ./gradlew build` 通过;上述文件中没有旧模块引用。

### W1b `api/`

- **范围**:§4.1、§4.3 的 proto,protobuf + grpc-kotlin 代码生成;§4.2、§7.1 的命名与大小校验;§4.4 错误码与 status detail 定义;OpenAPI 混合生成(D18,供 Dashboard)。
- **不在范围**:任何 server / client / store 逻辑。
- **验收**:`CI=true ./gradlew build` 通过;校验规则有完整的边界测试;proto 通过 `buf lint`(或等价检查)。

### W2a `store/` + `conformance-test/`

- **范围**:§5.0 Store SPI;内存实现;conformance 抽象测试类,覆盖:
  CAS 三种模式、批写原子性(任一失败全部不生效)、无变化不推进 revision(labels 顺序不同也算无变化)、`changes` 续传 / 窗口外 `RevisionGoneException`、
  epoch(删除重建后更换;钩子 `loseWritesAfter` 模拟丢写后 `changes` 抛 `TimelineDivergedException`)、只读元数据随读取 / 事件 / 历史完整返回、
  租约授予 / 批量保活 / 到期未回收可续期 / 到期 / 撤销 / 挂载实例随租约删除 / 迁移到新租约后旧租约到期不影响实例、`leaseStats`、`leaseOwners`、`versions`、
  Config 历史截断(A → B → A 保留三条)、namespace 生命周期(创建、删除前判空、`terminating` 后写入被拒、`dropNamespace` 可重入、重建换 epoch)、`tryLock` 互斥与过期。
- **验收**:内存实现通过全部 conformance 用例;测试类可被 `store-redis` 直接继承复用。

### W2b `client/` + `spring-boot-starter/`

- **范围**:§6.5、§7.5 客户端全部行为;§7.7 starter 全部集成点;Java `CompletableFuture` 门面推迟到 8.1(见 §6 Q5)。
- **测试**:以测试内的 gRPC 假服务(grpc-kotlin in-process)模拟 server,覆盖断线续传(带 `from_epoch`)、`gone` 重新 List、`Snapshot` / `Events` 分块、低频重建、
  租约丢失重注册、`UNAVAILABLE` 不触发重注册、重连后立即续期、快照启动。
- **验收**:不依赖 Spring 的 `client` 模块无 `reactor` 依赖;starter 有 Spring Boot 集成测试(ConfigData 加载、刷新、注册、发现)。

### W2c 贯通验证

追尾(§6.1)、回收保护(§6.2)和分叉检测(§6.4)目前只有 Lua 原型验证,如果到 W4a 才第一次用真实 Redis 联调,出问题时返工最大。W2a 合入后先打通最小链路,代码合适就留给 W3a、W3b 继续用,不合适就丢弃。

- **范围**:`store-redis` 中 `apply.lua` → Stream → `tail.lua` → `Store.changes` 的最小实现;`server` 中最小的 `NamespaceCache`、gRPC Watch 和 Reaper 保护。不含鉴权、REST、Views。
- **验收**(Testcontainers,多节点 Redis Cluster):
  - 多 namespace 跨 slot 追尾,`PUBLISH` 提示跨节点送达。
  - 真实主从切换丢写,revision 回退并涨回后,watcher 收到 `gone` 并收敛。
  - Redis 或 server 不可用超过 TTL 后恢复,存活实例不被删除。
  - Spring Boot 4 + Spring gRPC + grpc-kotlin 在 JDK 25 上最小可运行。
  - 粗测 §7.10 的扇出与内存目标,比较扇出路径上虚拟线程与 `Dispatchers.Default` 的开销(D28),把结果和偏差写回设计文档;默认值(§7.6)按结果调整。

### W3a `server/`

- **范围**:§6.1–§6.4、§7.2–§7.4、§7.6、§7.8、§7.9;运行在内存 Store 上。
- **验收**:client 驱动的协议级 conformance 用例全部通过;REST 与 gRPC 行为一致的对照测试;鉴权矩阵测试(含 `Rule.names`、RoleBinding namespace、租约所有者);List 分页与消息字节上限;
  回收保护三层(恢复宽限、大比例到期暂停、单轮上限)测试;epoch 变化时 watcher 收到 `gone`;反熵发现不一致时按分叉处理。

### W3b `store-redis/`

- **范围**:§5.1–§5.3 键布局与脚本;§6.1 追尾(提示 + `tail.lua` + 兜底轮询);Lettuce 协程桥接;Standalone / Cluster 均需验证。
- **验收**:继承 W2a conformance 用例并全部通过(Testcontainers Redis);Cluster 模式下所有脚本键均显式声明,多 namespace 跨 slot 追尾通过;
  提示丢失时靠轮询仍能收敛。

### W4a 端到端 + 示例 + 部署

- 全链路测试(server + store-redis + client);Toxiproxy 故障注入(§7.11,含超过 TTL 后恢复与丢写分叉);重写 provider / consumer 示例;
  发布流程支持从 `v8` 发布预发布版本;
  Dockerfile(JDK 25)、k8s 清单 / Helm;JMH 与压测达成 §7.10 目标。

### W4b Dashboard

- OpenAPI 生成客户端;OIDC(PKCE)与 token 登录;SSE 实时刷新;页面对应新资源模型(Config、Instance、ServiceAccount、Role、RoleBinding、Views)。

### W4c 迁移工具

- 读取 6.x Redis 键结构中的 Config(含当前版本,可选历史),通过 8.0 REST API 导入;实例不迁移(重新注册)。
  用户与角色不迁移(身份模型已变),输出一份对照报告供手工重建。

### W4d 文档

- wiki 中英文按新架构重写;README;`AGENTS.md`;6.x → 8.0 迁移指南。

## 4. 协作方式

- **主线分支 `v8`**:8.0 的全部工作汇入 `v8`,`main` 在 8.0 GA 前继续承载 6.x。`main` 的修复按需移植到 `v8`;旧模块删除后不再整体合入 `main`。
- **任务分支**:每个任务由一个**独立子会话**在自己的 worktree 中完成,从 `v8` 最新提交切出,提交到自己的分支
  (`claude/<任务>-…`)。
- **一个任务一个 PR**:任务完成后向 `v8` 提 PR,标题遵循 Conventional Commits,squash 合并;合并前由协调会话审查并独立重跑
  `CI=true ./gradlew build`,PR 的 CI 必须通过。审查意见在同一任务分支上修改,不另开 PR。
- **文档变更**同样经 PR 进入 `v8`;只改文档的 PR 可以不等 6.x 流水线跑完。
- **协调会话**负责:编写任务说明、审查 PR(对照设计文档)、合并到 `v8`、更新设计文档 / 本路线图 / `AGENTS.md`、
  与维护者讨论设计变更。
- **过渡**:W1 已在本地分支 `claude/project-architecture-redesign-181c01` 上完成审查。它的提交历史含 merge 和 7.0 改名过程,
  所以不变基,而是取相对分叉点的差异拆成 W1a、W1b 两个 PR;两个 PR 都合入后不再使用该本地集成分支。
- 实现中发现设计需要调整时,子会话**停下来说明**,由协调会话与维护者讨论并更新设计文档,不在代码中悄悄偏离。
- **通用完成标准**:`./gradlew build` 通过(Detekt、license header、覆盖率 90% 行 / 70% 分支);新增行为有测试;
  不修改任务范围外的模块;提交信息遵循 Conventional Commits。

## 5. 进度

| 任务 | 子会话 | 分支 | 状态 |
|---|---|---|---|
| M0 设计定稿 | 协调会话 | `v8`(#1001、#1004) | 重新打开:D19–D22 已确认;其余问题待决(§6) |
| M1 契约 | 协调会话 | — | ✅ 完成(W1a、W1b 合并) |
| W1a 构建骨架 | 协调会话 | #1005 | ✅ 已合并 |
| W1b api | 协调会话 | #1006 | ✅ 已合并,含 D19、D23、D24 的契约改动 |
| W2a store + 一致性 | 协调会话 | #1008 | ✅ 已合并 |
| W2b client + starter | — | — | 未开始 |
| W2c 贯通验证 | 协调会话 | — | 进行中:`store-redis` 已完成,最小 server 未开始 |
| W3a server | — | — | 未开始 |
| W3b store-redis | 协调会话 | — | Store 实现已随 W2c 完成,通过一致性套件;还差 R8 |
| W4a 端到端 + 示例 + 部署 | — | — | 未开始 |
| W4b Dashboard | — | — | 未开始 |
| W4c 迁移工具 | — | — | 未开始 |
| W4d 文档 | — | — | 未开始 |

## 6. 设计问题

### 已关闭

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

### 架构审查(2026-10-30)

P0 已写入设计文档:Watch 时间线(D19)、回收保护(D20)、Redis 追尾(D21)、键布局(D22)。
R1 分页与分块(D23)、R2 授权范围(D24)、R3 namespace 生命周期(D25)、R4 本地快照(D26)已关闭;R7 冷启动读取由 `read_batch.lua` 解决(§5.2)。以下问题尚未决定,须在"最晚决定"列的时间点之前关闭:

| # | 问题 | 涉及章节 | 最晚决定 |
|---|---|---|---|
| R5 | 副本滞后被误判为 `gone`;`MAXLEN ~` 截断同一 revision 的批次;窗口单位不一致,也小于滚动重启的事件量 | §6.1、§7.3 | W3a 开工前 |
| R6 | 拓扑视图在多副本下只有局部数据 | §4.3、§6.3 | W3a 开工前 |
| R8 | Redis 前提未写明(淘汰策略、最低版本、`WAIT`);丢写后租约还在而 Instance 已丢失时不会重新注册 | §5.3、§6.5 | W3b 开工前 |
| R9 | 6.x 与 8.0 没有共存迁移方案,存量集群只能整体切换 | 本文 W4c | W4c 开工前 |

## 7. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| W3a server 体量最大,成为关键路径 | M3 延期 | W3a 内部再拆:先 ResourceService + WatchService,后 LeaseService + 鉴权 + ViewService |
| Spring Boot 4 / Spring gRPC 版本兼容 | 构建与集成受阻 | W2c 做最小可运行验证 |
| Redis Cluster 下 Stream 与脚本的行为差异 | W3b 返工 | W2c 先验证;conformance 同时跑 Standalone 与 Cluster |
| namespace 很多时兜底轮询的负载或传播延迟不达标 | W3b 返工 | W3b 压测;`tail-poll-interval` 可调 |
| 8.0 不兼容导致用户迁移成本高 | 采用受阻 | W4c 迁移工具 + 迁移指南;6.x 维护期 |
