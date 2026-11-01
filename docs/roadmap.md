# CoSky 8.0 重构路线图

> 设计依据:[architecture-redesign.md](architecture-redesign.md)(下文 §x 均指该文档章节)。
> 本文档跟踪**怎么做、做到哪**;设计本身的变更只改设计文档,并在这里记录影响。

---

## 1. 目标与范围

- 交付 **CoSky 8.0.0**:按目标架构完整重写,不兼容 6.x 的 API、Redis 键结构和客户端。
- 6.x 进入维护期:只修安全问题和严重缺陷,不再加功能。
- 版本序列:`8.0.0-alpha.N`(开发预览,API 可变)→ `8.0.0-beta.N`(API 冻结)→ `8.0.0-rc.N` → `8.0.0`。起始版本为 `8.0.0-alpha.1`。
- M4 之后开始发布,从 `8.0.0-alpha.1` 起:alpha 不作兼容性承诺,API、协议和 Redis 键都可能变化。预发布版本只有版本号标签,`latest` 仍指向 6.x。
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

同一波次内的任务互不修改相同文件,可并行;后一波次从 `main` 上前一波次的合并结果切出。

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
- **测试**:用真实的 server 代码(内存 Store,gRPC in-process),比假服务更能发现协议问题,覆盖断线续传(带 `from_epoch`)、`gone` 重新 List、`Snapshot` / `Events` 分块、低频重建、
  租约丢失重注册、`UNAVAILABLE` 不触发重注册、重连后立即续期、快照启动。
- **验收**:不依赖 Spring 的 `client` 模块无 `reactor` 依赖;starter 有 Spring Boot 集成测试(ConfigData 加载、刷新、注册、发现),走真实端口和默认传输。

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
  回收保护三层(恢复宽限、大比例到期暂停、单轮上限)测试;epoch 变化时 watcher 收到 `gone`;反熵发现不一致时按分叉处理;
  副本落后时 watcher 挂起后续传,超时以 `UNAVAILABLE` 结束而不是 `gone`;副本加载后可从窗口续传;窗口按事件淘汰(D29);两个副本的拓扑边合并,watcher 消失后边过期(D30);实例丢失后客户端靠 `attached_count` 重新 Put(D31)。

### W3b `store-redis/`

- **范围**:§5.1–§5.3 键布局与脚本;§6.1 追尾(提示 + `tail.lua` + 兜底轮询);Lettuce 协程桥接;Standalone / Cluster 均需验证。
- **验收**:继承 W2a conformance 用例并全部通过(Testcontainers Redis);Cluster 模式下所有脚本键均显式声明,多 namespace 跨 slot 追尾通过;
  提示丢失时靠轮询仍能收敛;启动检查拒绝 `noeviction` 以外的淘汰策略和低于 7.0 的版本,`INFO` 读不到时只告警;CI 矩阵覆盖 Redis 7.0、7.4 和 Valkey(D31)。

### W4a 端到端 + 示例 + 部署

- 链路追踪接入(§7.9);全链路测试(server + store-redis + client);Toxiproxy 故障注入(§7.11,含超过 TTL 后恢复与丢写分叉);重写 provider / consumer 示例;
  发布流程支持发布预发布版本;
  Dockerfile(JDK 25,开启紧凑对象头)、k8s 清单 / Helm;JMH 与压测达成 §7.10 目标;实测 D29 的窗口默认值,不够就调整并写回设计文档。

### W4b Dashboard

- OpenAPI 生成客户端;OIDC(PKCE)与 token 登录;SSE 实时刷新;页面对应新资源模型(Config、Instance、ServiceAccount、Role、RoleBinding、Views)。

### W4c 迁移工具

- 整体切换(D33):读取 6.x Redis 键结构中的 Config(含当前版本,可选历史),通过 8.0 REST API 导入;实例不迁移(重新注册)。
  用户与角色不迁移(身份模型已变),输出一份对照报告供手工重建。报告同时列出不会自己回来的固定实例,和 8.0 不接受的服务名。

### W4d 文档

- wiki 中英文按新架构重写;README;`AGENTS.md`;6.x → 8.0 迁移指南。

## 4. 协作方式

- **主线分支 `main`**:8.x 的全部工作汇入 `main`(原 `v8` 分支已快进为 `main`)。
- **维护分支 `v6`**:从原 `main` 切出,承载 6.x,只接受 BUG 修复。同样存在于 8.x 的 BUG 也要在 `main` 上修复。
- **站点**:cosky.ahoo.me 由 `main` 部署,描述 8.0。6.x 的文档留在 `v6` 分支的 `wiki/`。
  8.0 稳定之前,README 和快速开始页标明当前是预发布版本,不作兼容性承诺,GA 时删除这两处说明。
- **任务分支**:每个任务由一个**独立子会话**在自己的 worktree 中完成,从 `main` 最新提交切出,提交到自己的分支
  (`claude/<任务>-…`)。
- **一个任务一个 PR**:任务完成后向 `main` 提 PR,标题遵循 Conventional Commits,squash 合并;合并前由协调会话审查并独立重跑
  `CI=true ./gradlew build`,PR 的 CI 必须通过。审查意见在同一任务分支上修改,不另开 PR。
- **文档变更**同样经 PR 进入 `main`。
- **协调会话**负责:编写任务说明、审查 PR(对照设计文档)、合并到 `main`、更新设计文档 / 本路线图 / `AGENTS.md`、
  与维护者讨论设计变更。
- **过渡**:W1 已在本地分支 `claude/project-architecture-redesign-181c01` 上完成审查。它的提交历史含 merge 和 7.0 改名过程,
  所以不变基,而是取相对分叉点的差异拆成 W1a、W1b 两个 PR;两个 PR 都合入后不再使用该本地集成分支。
- 实现中发现设计需要调整时,子会话**停下来说明**,由协调会话与维护者讨论并更新设计文档,不在代码中悄悄偏离。
- **通用完成标准**:`./gradlew build` 通过(Detekt、license header、覆盖率 90% 行 / 70% 分支);新增行为有测试;
  不修改任务范围外的模块;提交信息遵循 Conventional Commits。

## 5. 进度

| 任务 | 子会话 | 分支 | 状态 |
|---|---|---|---|
| M0 设计定稿 | 协调会话 | #1001、#1004 | D19–D33 已确认,开放问题全部关闭(§6) |
| M1 契约 | 协调会话 | — | ✅ 完成(W1a、W1b 合并) |
| W1a 构建骨架 | 协调会话 | #1005 | ✅ 已合并 |
| W1b api | 协调会话 | #1006 | ✅ 已合并,含 D19、D23、D24 的契约改动 |
| W2a store + 一致性 | 协调会话 | #1008 | ✅ 已合并 |
| W2b client + starter | 协调会话 | #1013 与本 PR | ✅ 已合并 |
| W2c 贯通验证 | 协调会话 | #1010 与本 PR | ✅ 已合并;结果见设计文档 §7.10 |
| W3a server | 协调会话 | #1016、#1018–#1021 与本 PR | ✅ 已合并;链路追踪移到 W4a |
| W3b store-redis | 协调会话 | 本 PR | ✅ 已合并:D31 的启动检查与兼容性矩阵 |
| W4a 端到端 + 示例 + 部署 | 协调会话 | #1023–#1029 与本 PR | ✅ 已合并;压测结果见设计文档 §7.10:20,000 连接没有验证,约 15,000 连接时的传播未达标 |
| W4b Dashboard | 协调会话 | #1032 与本 PR | ✅ 已合并;OIDC 只用模拟的提供方验证过,没有接过真实提供方 |
| W4c 迁移工具 | 协调会话 | 本 PR | ✅ 已合并;只支持整体切换(D33);暂时要从源码构建,分发方式由 W4d 的迁移指南确定 |
| W4d 文档 | 协调会话 | 本 PR、#1034、#1035 | ✅ 已合并;Mermaid 渲染与 CoCache 一致;写文档时发现并修复了客户端不支持 TLS、Spring 下摘流无效 |

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
R1 分页与分块(D23)、R2 授权范围(D24)、R3 namespace 生命周期(D25)、R4 本地快照(D26)、R5 Watch 窗口(D29)、R6 拓扑(D30)、R8 Redis 前提(D31)、R9 迁移方式(D33:整体切换,不做共存)已关闭;R7 冷启动读取由 `read_batch.lua` 解决(§5.2)。没有未决问题。

### 发布前审查(2026-11-01)

维护者决定:Instance 的 `spec.service` 创建后不可改(D35),在 `8.0.0-alpha.1` 之后生效。

维护者决定:Watch 窗口加字节上限(D36),在 `8.0.0-alpha.1` 之后生效。

beta 前要设计:Redis Stream 仍只按事件数限制,大 Config 频繁写入时占用的 Redis 内存可达 `MAXLEN` × Config 大小。

## 7. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| W3a server 体量最大,成为关键路径 | M3 延期 | W3a 内部再拆:先 ResourceService + WatchService,后 LeaseService + 鉴权 + ViewService |
| Spring Boot 4 / Spring gRPC 版本兼容 | 构建与集成受阻 | W2c 已验证;Spring gRPC 1.1 依赖 gRPC 1.81 而 Boot 4.1 管理 1.80,版本目录须手动对齐,否则运行时报 `AbstractMethodError` |
| Redis Cluster 下 Stream 与脚本的行为差异 | W3b 返工 | W2c 先验证;conformance 同时跑 Standalone 与 Cluster |
| namespace 很多时兜底轮询的负载或传播延迟不达标 | W3b 返工 | W3b 压测;`tail-poll-interval` 可调 |
| 8.0 不兼容导致用户迁移成本高 | 采用受阻 | 整体切换(D33);W4c 迁移工具 + 迁移指南;6.x 维护期 |
