# CoSky 8.0 重构路线图

> 设计依据:[architecture-redesign.md](architecture-redesign.md)(下文 §x 均指该文档章节)。
> 本文档跟踪**怎么做、做到哪**;设计本身的变更只改设计文档,并在这里记录影响。

---

## 1. 目标与范围

- 交付 **CoSky 8.0.0**:按目标架构完整重写,不兼容 6.x 的 API、Redis 键结构和客户端。
- 6.x 进入维护期:只修安全问题和严重缺陷,不再加功能。
- 版本序列:`8.0.0-alpha.N`(开发预览,API 可变)→ `8.0.0-beta.N`(功能完整,API 仍可调整)→ `8.0.0-rc.N`(API 冻结)→ `8.0.0`。起始版本为 `8.0.0-alpha.1`。
- M4 之后开始发布,从 `8.0.0-alpha.1` 起:RC 之前(alpha 与 beta)不作兼容性承诺,API、协议、配置和 Redis 键都可能变化,发版说明写明怎么升级。预发布版本只有版本号标签,`latest` 仍指向 6.x。
- `8.0.0-rc.1` 起冻结,到 8.0.0 只增不改:gRPC API(`cosky.v1`,含错误原因)、REST API、client 与 starter 的公开 API 和配置项、server 的配置项、Deployment 列出的指标、Redis 中的数据(之后的版本不经迁移就能读)。不在其内:server 各模块作为库的 API、Dashboard 界面、日志内容。维护者更正(2026-11-14):冻结从 RC 开始,不是 beta;beta.1、beta.2 的发版说明写的是 beta 冻结。beta 期间的不兼容变更写进当版发版说明。
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
| W4a 端到端 + 示例 + 部署 | 协调会话 | #1023–#1029 与本 PR | ✅ 已合并;压测结果见设计文档 §7.10:20,000 连接已验证,传播 p99 为 154–189 ms |
| W4b Dashboard | 协调会话 | #1032 与本 PR | ✅ 已合并;OIDC 已用 Keycloak 26 实测(D37),其他提供方没有实测 |
| W4c 迁移工具 | 协调会话 | 本 PR | ✅ 已合并;只支持整体切换(D33);每个 Release 附 `cosky-migrate` zip;已用 6.2.0 server 写出的数据验证 |
| W4d 文档 | 协调会话 | 本 PR、#1034、#1035 | ✅ 已合并;Mermaid 渲染与 CoCache 一致;写文档时发现并修复了客户端不支持 TLS、Spring 下摘流无效 |
| 发布 `8.0.0-alpha.1`–`beta.3` | 协调会话 | alpha.2 #1052,alpha.3 #1055,alpha.4 #1152,alpha.5 #1174,beta.1 #1181,beta.2 #1188,beta.3 本 PR | ✅ alpha.1–beta.2 已发布到 Maven Central、GitHub Packages、Docker Hub、GHCR、阿里云;beta.1、beta.2(2026-11-14),beta.2 修复 Sentinel 主节点挂住时不跟随切换,两版发布后都已用公开制品跑通 Getting Started;beta.3 的 REST 错误改为 problem+json,并提供 OpenAPI 文档与 Swagger UI;请用最新一版 |

### 到 8.0.0 的剩余工作(`8.0.0-alpha.3` 之后)

M0–M4 已完成,没有未决的设计问题。维护者决定先在本机模拟 beta 前的验证(2026-11-08),结果标明是单机模拟,最终仍需维护者的环境。三项都在本机完成后,维护者决定发布 `8.0.0-beta.1`(2026-11-14)。

已验证:

- 6.x 迁移:用 6.2.0 server 自己的接口写入 Config(多个版本、回滚、删除、中文与 emoji、216 KB 的大文件)、非法的 namespace 和服务名、固定实例、用户和角色,再导入 8.0。内容逐字节一致,历史顺序正确;非法 namespace 被拦下要求映射,无法导入的内容进报告;重跑不改动,两边各改一个 Config 时分别更新和报告冲突。8.0 的公开镜像与 6.x 共用一个 Redis 时结果相同。
- 20,000 连接的传播长尾:来自压测端的 gRPC 客户端线程池,不是 server(设计文档 §7.10)。
- 多节点 `kubectl drain`:k3s 1 个服务节点加 3 个 agent,Redis 固定在服务节点,CoSky 的 3 个副本在 agent 上。依次 drain 每个 agent,再同时 drain 两个,同时每 200 ms 写一次 Config、让一个用 starter 的应用读取它并检查它的注册:1,880 次写入、3,782 次读取、799 次注册检查全部成功,应用读到新值最慢 0.14 秒。同时 drain 时 PDB 拒绝了 3 次驱逐,让副本一个接一个离开。应用所在的副本被驱逐两次,应用都自动重连,租约没有中断。

维护者决定不再验证其他 OIDC 提供方,只实测过 Keycloak 26(D37)。

**beta 前的验证**

| 验证项 | 现状 | 环境 |
|---|---|---|
| Redis Cluster 故障切换 | 本机 Docker 集群(3 主 3 从,节点超时 5 秒)上停掉一个主节点:alpha.5 上该主节点所在槽位的 namespace 在 `docker kill` 后 53 秒不可用,主节点挂住时则一直不可用,而 Redis 约 5 秒就提升了副本。原因是 Cluster 客户端没开拓扑刷新,Lettuce 读拓扑又用起始 URI 的超时(默认 60 秒)等挂住的节点。#1177 打开拓扑刷新(变化迹象触发,另每 5 秒一次),Cluster 超时降到 5 秒:`docker kill` 和 `docker pause` 都降到约 16 秒,期间请求 5 秒后返回 `STORE_UNAVAILABLE`;`RedisStoreClusterFailoverTest` 在 CI 上覆盖 | 本机 Docker 模拟;维护者环境再验 |
| Redis Cluster 大规模 | 本机模拟(2026-11-14):三个 server 副本跟随 1,000 和 5,000 个 namespace,连单个 Redis 和三主三从 Cluster 对比。Cluster 把空闲轮询和写入的负载均摊到各主节点(5,000 个 namespace 时单个 Redis 空闲 CPU 30%,Cluster 每个主节点 11%);写入吞吐受笔记本限制,两者相近;单 namespace 的 §7.10 目标在 Cluster 上与单个 Redis 一致。顺带发现跟随者每次写入多读一次日志,#1178 去掉后每次写入的 Redis 命令少约 35%。数字见 wiki 性能页 | 本机 Docker 模拟;维护者环境再验 |
| 真实网络 | 本机模拟(2026-11-14):在 Docker 虚拟机里的 Linux 容器中,用 Toxiproxy 中继加 netem 给 server 到 Redis、客户端到 server 两段链路加延迟、抖动和丢包,5,000 个连接 watch。Redis 和客户端各相距 6 ms 时单个写入者 p99 15 ms,传播 p99 127 ms;丢包 0.1% 时传播 p99 254 ms,各段丢包 1% 时单个写入者 p99 232 ms(TCP 重发超时),传播 p99 361 ms;客户端相距 58 ms、丢包 0.5% 时传播 p99 348 ms。100 个客户端经往返约 200 ms、丢包 5% 的链路 300 秒,看到的 Instance 始终 100 个。§7.10 的目标除丢包 1% 时的写入外都达到。macOS 经虚拟机端口转发会自己丢包,所以必须在 Linux 里跑。数字见 wiki 性能页 | 本机模拟;维护者环境再验 |

**beta.1 之后的验证**

| 验证项 | 现状 | 环境 |
|---|---|---|
| Redis Sentinel 故障切换 | 本机 Docker(一主一从、三个 Sentinel,5 秒判下线)上停掉主节点:beta.1 在主节点被冻结时一直不可用(240 秒内未恢复),每个请求 60 秒后失败,因为普通连接只在建立时向 Sentinel 问主节点。改为跟随 Sentinel 公布的切换(Lettuce MasterReplica),命令超时统一默认 5 秒:冻结约 16 秒、被杀约 15 秒恢复,主动交接立即;Lettuce 关闭旧连接时取消的命令不再冒充协程取消。`RedisStoreSentinelFailoverTest` 和 Sentinel 上的 Store 一致性套件在 CI 上覆盖 | 本机 Docker 模拟;维护者环境再验 |

**发版**

`8.0.0-beta.1` → `8.0.0-rc.1` → 真实环境试运行两周且无 P0/P1 → `8.0.0`。发 `rc.1` 时把 CI 的 Proto Compatibility 设回 `main` 的必需检查:beta 期间它只报告不阻塞(维护者决定,2026-11-14),RC 起 proto 只增不改。版本号合入 `main` 就会触发发布且不可撤销,每次发版都要维护者确认。正式版发布时 `latest` 才切到 8.0,工作流已写好,还没有实际运行过。

**依赖和杂项**

- Renovate 有一批大版本升级等批准(如 TypeScript 7、Node 24、pnpm 12、GitHub Actions)。#931 里的 simba、cosec、cosid `main` 已不用,可能来自 v6 分支。
- Dashboard 已从弃用的 `@ahoo-wang/fetcher-generator` 迁到 `@ahoo-wang/wow-generator` 9.9,fetcher 各包随之从 3.x 升到 6.0(2026-11-14)。生成的客户端改为按参数传路径、查询和请求头,返回解析好的对象;`src/services` 随之改写,单元、UI(含覆盖率)和真实后端测试都通过。wow-generator 包里的迁移指南链接是 404。
- #5(2021 年询问掉线告警)还开着。
- 依赖告警已核对:19 个都在 Dashboard 的锁文件里,没有一个进入生产包。Monaco 0.55.1 自带的 DOMPurify 3.2.7 不在严重告警的范围内,更新的 Monaco 自带 3.4.x 反而在,所以先不升级 Monaco。`deepmerge` 还没有修复版本。

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

用 Keycloak 实测 OIDC 后修复(D37):server 不再依赖服务商才能启动,Dashboard 续期 ID token,退出时结束服务商的会话。

维护者决定:Redis 日志也加字节上限(D38),不再使用 `MAXLEN`,在 `8.0.0-alpha.2` 之后生效。

### main issue 修复(2026-11-07)

维护者在 `8.0.0-alpha.3` 之后提交的 [main] issue 全部修复(#1067、#1082、#1093–#1095、#1099、#1102–#1115、#1117、#1118、#1124–#1129、#1134–#1142)。其中维护者决定:

- Namespace 删除先在 `_system` 中 CAS 标记 `terminating`,再做不可逆清理(D39,#1109)。
- 重建的 namespace 沿用原来的 revision 继续递增(D40,#1134)。
- starter 每个应用一个客户端(D41,#1139、#1140)。
- 管理员摘流持续到应用重新注册,只写进文档(D42,#1093)。

修复中顺带发现并修复:默认实例名的拼接歧义改为 `<service>_<host>_<port>`(#1126),续约流静默挂起时重连(测试偶发失败的根因),以及几处端到端测试的端口争用和读写时序。

### main issue 修复(2026-11-08)

维护者在 `8.0.0-alpha.4` 之后提交的 #1155–#1164 全部修复:starter 跟进刷新新增的导入、保留 endpoint 的 scheme、非 Web 应用不自动注册、遵守 Spring Cloud 的发现开关(#1168);client 的周期重建不再与流接管竞争,续传的 Watch 不再无限等待冷缓存(#1169);Dashboard 的读取带上 Watch 已见的 revision,迟到的令牌不覆盖正在编辑的抽屉,前导零端口不再产生第二个实例名(#1170)。维护者决定:Namespace 删除在标记时记下 epoch,终止与清理都按它原子校验,迟到的删除不越过重建边界(D43,#1155)。

### 代码结构审查(2026-11-07)

按职责逐层审查 server 与存储,不改协议、存储格式与设计决策,只把放错层的规则放回去:

- Store SPI 用自己的 `StoreUnavailableException` 表示后端不可达,`RedisStore` 在边界转换 Lettuce 的异常,并拥有自己创建的客户端(`RedisStore.open`);server 只在装配处知道 Redis(#1154、#1166)。
- Watch 的校验、授权、配额、进度书签与权限复核只在 `WatchService` 实现一次,gRPC 与 REST 只做映射;`TopologyRecorder` 移到 `watch` 包,避免 `watch` 与 `view` 互相依赖(#1165)。
- 租约规则从 gRPC 适配层移入 `LeaseService`,租约归属只检查一处;D39 的 namespace 删除流程收拢到 `NamespaceLifecycle`,请求与 reconciler 共用;服务视图直接读按服务的索引(#1167)。

没有发现需要改动架构决策的问题。

## 7. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| W3a server 体量最大,成为关键路径 | M3 延期 | W3a 内部再拆:先 ResourceService + WatchService,后 LeaseService + 鉴权 + ViewService |
| Spring Boot 4 / Spring gRPC 版本兼容 | 构建与集成受阻 | W2c 已验证;Spring gRPC 1.1 依赖 gRPC 1.81 而 Boot 4.1 管理 1.80,版本目录须手动对齐,否则运行时报 `AbstractMethodError` |
| Redis Cluster 下 Stream 与脚本的行为差异 | W3b 返工 | W2c 先验证;conformance 同时跑 Standalone 与 Cluster |
| namespace 很多时兜底轮询的负载或传播延迟不达标 | W3b 返工 | W3b 压测;`tail-poll-interval` 可调 |
| 8.0 不兼容导致用户迁移成本高 | 采用受阻 | 整体切换(D33);W4c 迁移工具 + 迁移指南;6.x 维护期 |
