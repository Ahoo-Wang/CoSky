# CoSky 目标架构重设计(不考虑兼容性)

> 基于 `main@bc6e873`(v6.2.0)代码审查。目标是回答:**如果今天从零开始做 CoSky,它应该长什么样?**
>
> 状态:方案已确认(见 §1 决策记录),详细设计见 §7;实施计划与进度见 [roadmap.md](roadmap.md)。
> 2026-10-30 架构审查后新增 D19–D26,其后新增 D27–D31;审查遗留问题见 [roadmap.md §6](roadmap.md#6-设计问题)。

---

## 0. 结论先行

当前 CoSky 的本质是 **"Redis 就是服务端,Lua 脚本就是协议,每个业务进程都是一个胖客户端"**。
`cosky-rest-api` 只是挂在同一个 Redis 上的管理后台。这个选择换来了"零部署、极低延迟",
但它把控制面最核心的四件事——**协议、一致性、存活判定、权限**——摊到了每个客户端和 Redis 键结构上。

目标架构:

> **在 Redis 之上加一层轻量控制面服务。CoSky 是一个"带版本号的资源存储 + 可续传 Watch + 租约"的服务,
> 客户端只说协议,不碰存储。**

保留"用现成 Redis 就能跑"的轻量定位;不自研一致性协议,不做第二个 Nacos / etcd。

```
业务进程 (client, 本地快照落盘)
      │  gRPC 双向流: Watch + 租约保活
      ▼
server × N (无状态, 水平扩容)
  ├─ 统一鉴权 (ServiceAccount token / OIDC)
  ├─ Resource Service (CAS 写)
  ├─ Watch Cache (内存全量 + 事件窗口)
  └─ Lease Reaper (Redis 锁选主)
      │  通用 apply 脚本 + Redis Stream
      ▼
Redis (只有 server 可访问)
```

---

## 1. 决策记录

| # | 决策 | 结论 | 理由 |
|---|---|---|---|
| D1 | 是否引入独立服务端 | **引入** | 数据面鉴权、多语言、可续传 Watch、服务端回收都以此为前提;写多一跳,读走本地快照不受影响 |
| D2 | 存储 | **Store SPI;只实现 Redis(+ 测试用内存实现),不做内嵌 Raft** | "直接用已有 Redis"是 CoSky 唯一差异点;Redis 持久性风险以文档 + 时间线分叉检测缓解(§6.4),PostgreSQL 实现作为未来选项 |
| D3 | 协议 | **gRPC 数据面 + REST/JSON 管理面,同一份 `.proto`** | 租约保活与 Watch 天然是长连接双向流;REST 由 proto 映射,保证只有一套契约 |
| D4 | 分片 | **namespace = 分片单位(一个 Redis slot),写为显式约束** | 控制面数据小、写少;读与扇出由 server Watch Cache 承担,Redis 负载与 server 副本数成正比而非业务进程数 |
| D5 | 身份 | **内置最小集(bootstrap admin + ServiceAccount + Role/RoleBinding),人员走 OIDC** | 删除自建用户/密码/锁定/改密;权限对象本身是资源,复用存储、Watch、审计 |
| D6 | 直连 Redis 模式 | **不保留** | 双模式会让两套语义长期共存 |
| D7 | 不做的事 | 自研一致性协议、多数据中心同步、服务端托管业务规则 | 控制面只存数据、推送变更 |
| D8 | 模块目录 | **去掉 `cosky-` 前缀**(`api/`、`server/` …);发布的 Maven artifactId 仍为 `cosky-*` | 目录简洁;Maven 坐标需全局可辨识,由根构建脚本统一加前缀(§3.1) |
| D9 | 异步模型 | **Kotlin 协程优先**(`suspend` / `Flow` / `StateFlow`);Reactor 只出现在适配层 | 协程是 Kotlin 原生模型,代码顺序可读、取消语义结构化;grpc-kotlin 原生协程;Reactor 仅用于对接只提供 Reactor API 的外部框架(§3.2) |
| D10 | Spring 配置加载 | **Spring Boot ConfigData API**(`spring.config.import=cosky:…`),不再使用 bootstrap 上下文 | Spring Boot 推荐方式,无需 Spring Cloud bootstrap(§7.7) |
| D11 | Dashboard 部署 | **server 托管静态资源(单镜像)**,同时支持独立部署 | 部署最简;Dashboard 只依赖公开 REST API |
| D12 | 审计 | **结构化审计日志**,server 不存储审计数据 | 交给运维日志系统;变更追溯由 `updated_by` + Config 历史提供(§7.8) |
| D13 | 删除 namespace | **只允许删除空 namespace** | 防止误删整片数据(§7.2) |
| D14 | Java 门面 | **推迟到 8.1** | Spring 用户经 starter 使用,8.0 聚焦核心 |
| D15 | server gRPC 接入 | **Spring gRPC** | 与 Spring Boot 生命周期、配置、可观测性统一(§7.4) |
| D16 | 性能目标 | **采纳 §7.10** | 作为 W4a 验收标准 |
| D17 | proto 命名 | **遵循 buf STANDARD**(枚举值前缀、服务 `Service` 后缀),仅豁免 `Get` 直接返回资源 | proto 枚举值在包内共享命名空间,无前缀易冲突;Get 返回资源符合 AIP-131(§4.2) |
| D18 | OpenAPI | **混合生成**:schemas 由 proto 生成,paths 手写,构建时合并 | REST 路由无法由 RPC 自动推导,但 schema 必须与 proto 同步(§4.5) |
| D19 | Watch 时间线 | **`(epoch, revision)` 才是 Watch 位置**;epoch 是每 namespace 一个随机值,删除重建或丢写后更换,`from_epoch` 不一致即 `gone`;server 与客户端保留低频反熵 | 丢写后 revision 会被复用,namespace 重建后 revision 从 1 重来,只比较数字会让视图静默且永久分叉(§6.4) |
| D20 | 租约回收保护 | **恢复宽限 + 大比例到期暂停 + 单轮回收上限**;存储故障不等于租约丢失 | 控制面故障超过 TTL 后恢复时,到期的主要是存活进程的租约,不能把控制面故障放大成数据面故障(§6.2) |
| D21 | Redis 追尾 | **`PUBLISH` 提示 + 按 namespace 执行非阻塞 `tail.lua` + 兜底轮询**;`store-redis` 不使用阻塞命令 | Cluster 下跨 slot 的 `XREAD` 报 `CROSSSLOT`;每 namespace 一条阻塞连接则连接数随 namespace 增长;提示丢失只影响延迟(§6.1) |
| D22 | 键布局 | **只读元数据随对象持久化**(`ver`、Stream、历史);租约 TTL 单独保存,实例挂载集中在一个 Sorted Set;"无变化"基于确定性序列化 | Lua 无法解析 protobuf,脚本和事件必须能直接拿到审计与租约数据(§5.1) |
| D23 | 消息大小 | **单条消息按字节分开**:List / History 分页,Snapshot / Events 用 `more` 分块,上限 2 MiB | 5 万实例的全量约 50 MB,超过 gRPC 默认 4 MiB;批写事件和 Config 历史同样可能超限(§4.6) |
| D24 | 授权范围 | **RoleBinding 必须声明 namespace;`Rule.names` 按名称或服务收窄;租约只能由授予者续期和撤销** | 否则内置 `client` 无法限定 namespace,同 namespace 的账号能覆盖其他服务的 Instance、撤销其租约,D1 的数据面鉴权形同虚设(§7.8) |
| D25 | Namespace 生命周期 | **写入必须看到 slot 内的 `state = active`**;删除时原子判空并置 `terminating`;`hist:idx` 索引历史键 | `_system` 与业务 namespace 不在同一 slot,依赖缓存判空会丢数据,迟到的写入还会让已删除的 namespace 复活(§7.2) |
| D26 | 本地快照 | **定位为同盘重启的兜底**,k8s 中须挂持久卷才有效;路径含 `serverKey`;不加密,权限 `0600`,可关闭 Config 落盘 | 容器默认没有持久卷,不能承诺新进程可用旧视图;客户端没有地方安全存放密钥(§7.5、§9) |
| D27 | JVM 版本 | **基线 Java 21**,所有模块编译为 Java 21 字节码;**server 运行在 JDK 25** | 库的基线决定用户必须升级到哪个版本;server 是我们自己发布的镜像,可以直接用最新 LTS,紧凑对象头(JEP 519)等改进正好能减少 Watch Cache 的内存。server 不使用 Java 21 之后的 API,以便与其他模块保持同一工具链 |
| D28 | 虚拟线程 | **协程仍是编程模型(D9),虚拟线程是默认执行载体**:server 的请求处理与业务协程、所有阻塞操作都跑在虚拟线程上,不用 `Dispatchers.IO` 和平台线程池 | 协程负责结构化并发和取消,虚拟线程让偶发的阻塞不再占用平台线程,不必区分哪段代码可能阻塞(§3.2、§7.4)。Lettuce 的 Netty 事件循环不变,它本身不阻塞 |
| D29 | Watch 窗口 | **副本落后不是 `gone`**:同一 epoch 下 `from_revision` 大于副本 revision 时先挂起等它追上,最多 `read.min-revision-timeout`,超时以 `UNAVAILABLE` 结束流;副本加载时用 Stream 预填窗口;内存窗口与 Stream 都按事件计数,默认 10,000 | 客户端的位置本身有效,发 `gone` 会把滚动重启变成一次集中重新 List。窗口只需覆盖重连间隙:5 万实例 10 分钟滚动约 170 事件/秒,窗口能撑约 60 秒(估算,W4a 实测);扩大到 10 万每个 namespace 约多占 100 MB,不值得(§6.1、§7.3) |
| D30 | 拓扑 | **边作为软状态写入 Redis**:每个副本每 30 秒写一次,不经过 `rev` 和日志,不产生 Watch 事件,90 秒未刷新即失效;consumer 优先取客户端名,其次取认证主体 | 每个副本只见到自己的 watcher。向各副本扇出查询需要成员发现,只显示本副本数据会误导,推迟到 8.1 又比 6.x 少一项功能;多个应用共用 token 时,只靠主体分不开它们(§5.1、§6.3) |
| D31 | Redis 前提 | **启动时检查,不满足则拒绝启动**:淘汰策略必须是 `noeviction`,版本不低于 7.0;**不使用 `WAIT`**,改为建议 `min-replicas-to-write`;`KeepAliveResponse.attached_count` 让客户端发现丢失的注册并重新 Put | 淘汰会悄悄删数据。`WAIT` 在 Cluster 下要路由到键所在节点,也消除不了丢写。租约还在而实例已丢时续期一直成功,客户端需要一个信号;相比每个实例 Watch 自己(2 万注册就多 2 万 watcher),这个计数不增加扇出负担(§5.3、§6.2) |

---

## 2. 现状问题诊断

### 2.1 没有服务端:协议 = Redis 键结构 + Lua

- `RedisServiceRegistry` / `RedisConfigService` 在**业务进程内**执行 `registry_register.lua`、`config_set.lua` 等脚本。
- 键规则同时存在于 Kotlin(`DiscoveryKeyGenerator`、`ConfigKeyGenerator`)和 Lua(`discovery_lib.lua`、`config_lib.lua`),
  需要 `DiscoveryLuaKeysTest` 保证两边同步——"协议没有唯一定义"的直接症状。
- 结果:只能有 JVM 客户端;任何 schema 演进都是全体客户端的破坏性升级;每个业务进程持有 Redis 凭证,
  **数据面没有鉴权**,`cosky-rest-api` 的 RBAC / 审计只保护了 Dashboard。

### 2.2 事件模型:无版本、可丢、需要回读

- Redis Pub/Sub 是 at-most-once,断线期间事件丢失,`ReactiveWatchCache` 因此带 5 分钟全量 resync 兜底。
- 事件只携带操作名,`applyInstanceChanged` 必须回读 Redis:一次变更 × N 个订阅者 = N 次回读。
- `Snapshot.version` 是本地计数器,不是全局 revision,无法续传、无法比较视图一致性。
- 实例事件用 `PSUBSCRIBE`,再从频道名反解 `svc@schema#host#port`——身份编码进字符串再解析回来。

### 2.3 存活模型:心跳被当作状态变更

- 每实例一个 TTL key、每实例独立续期,写放大 O(实例数)。
- 续期改变 `ttlAt` 于是成了事件,又引入 `__last_renew_pub_ttl_at` 节流,客户端再按本地时钟过滤过期实例。
- 过期回收由 `RenewInstanceService` 在客户端续期后顺手触发,依赖"恰好有活着的租户"。

### 2.4 手工维护的派生索引

一次注册同时维护 `svc_idx`、`svc_stat`、`svc_itc_idx`、`svc_itc`、`svc_itc_exp`;`svc_stat` 由
`StatServiceScheduler`(带分布式锁)定时重算并持久化;拓扑由客户端在发现时顺手写入。派生数据被当成了事实。

### 2.5 扩展性的隐性上限

默认 namespace `cosky-{default}` 的哈希标签把整个 namespace 钉在一个 slot;`registry_reap.lua` 等脚本
拼接未声明在 `KEYS` 的键,只因哈希标签才在 Cluster 下"碰巧可用"。Pub/Sub 订阅数与业务进程数线性相关。

### 2.6 领域模型与 API 形态

`ServiceInstance` 接口 + 委托 + `NOT_FOUND` 哨兵 + `withXxx` 扩展是一个被拆开的 data class;元数据与系统字段
靠 `_`/`__` 前缀混在同一 Hash;每个方法传 `namespace: String`;`Mono<Boolean>` 混淆"失败/无变化";
配置格式由 configId 扩展名推断。

### 2.7 模块边界

`cosky-core` 混合领域、存储工具和缓存原语;`cosky-config` / `cosky-discovery` 接口与 Redis 实现、
3 种负载均衡变体放在一起;`cosky-rest-api` 混合服务端、IAM、审计、统计调度和 Dashboard 静态资源,
用户/角色/审计各有一套 Lua。

---

## 3. 目标架构总览

### 3.1 模块

| 目录 | Maven artifactId | 职责 | 依赖 |
|---|---|---|---|
| `api/` | `cosky-api` | `.proto`、生成的 Kotlin/gRPC 代码、资源校验规则、错误码 | 无 |
| `client/` | `cosky-client` | 连接、认证、List/Watch、租约保活、快照落盘、负载均衡;无 Spring、无 Redis | `api` |
| `spring-boot-starter/` | `cosky-spring-boot-starter` | 配置 PropertySource + 刷新、Spring Cloud Discovery/Registry,合并为一个 starter | `client` |
| `server/` | `cosky-server` | gRPC + REST、AuthN/Z、Resource Service、Watch Cache、Lease Reaper、派生视图 | `api`、`store` |
| `store/` | `cosky-store` | Store SPI + 内存实现(测试与 `--dev` 模式) | `api` |
| `store-redis/` | `cosky-store-redis` | Redis 实现,**唯一含 Lua 的模块** | `store` |
| `conformance-test/` | `cosky-conformance-test` | Store SPI 与协议一致性测试套件 | `store`、`client` |
| `bom/`、`dependencies/`、`test/` | `cosky-bom` … | 同现状,去前缀 | — |
| `dashboard/`、`examples/`、`wiki/` | — | Dashboard 只调用 server 公开 REST API,客户端由 OpenAPI 生成 | — |

删除:`cosky-core`、`cosky-config`、`cosky-discovery`、`cosky-spring-cloud-core`、两个独立 starter、`cosky-rest-api`。

根 `build.gradle.kts` 统一处理前缀。`archivesName` 只影响 jar 文件名,不影响 `maven-publish` 的坐标,所以 `artifactId` 要显式设置:

```kotlin
configure(publishProjects) {
    val coskyArtifactId = "cosky-${project.name}"
    configure<BasePluginExtension> { archivesName = coskyArtifactId }
    // publishing.publications:create<MavenPublication>(...) { artifactId = coskyArtifactId }
}
```

### 3.2 异步模型与适配层边界

**核心层**(`api`、`store`、`client`、`server` 的领域与服务代码)只使用协程:单值 `suspend fun`,
流式 `Flow`,可观察状态 `StateFlow`。**禁止依赖 `reactor-core`。**

| 适配点 | 位置 | 方式 |
|---|---|---|
| Redis 驱动 | `store-redis` | 直接使用 Lettuce 的 async API,通过 `kotlinx.coroutines.future.await` 挂起;我们的代码不出现 Reactor,对外只暴露 `Store` 的协程接口 |
| gRPC | `server`、`client` | grpc-kotlin 生成协程 stub,天然无 Reactor |
| REST / SSE | `server` | Spring WebFlux 的协程支持(`suspend` handler、返回 `Flow` 的 SSE),业务代码不出现 `Mono` / `Flux` |
| Spring Cloud 发现 | `spring-boot-starter` | `ReactiveDiscoveryClient` 要求 `Flux`:在适配类中用 `flow.asFlux()` 转换 |
| 虚拟线程(D28)| `server`、`client` | 协程运行在 `Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()` 上,替代 `Dispatchers.IO` 和平台线程池 |
| Spring 配置加载 | `spring-boot-starter` | Spring Boot ConfigData API(`spring.config.import=cosky:…`,§7.7)是阻塞接口:优先读本地快照,否则 `runBlocking { withTimeout(..) }` |
| Java 调用方 | `client` 可选门面 | `CoSkyJavaClient` 以 `CompletableFuture`(`future {}`)暴露;Spring 用户无需使用 |

边界由构建保证:Detekt `ForbiddenImport` 规则禁止 `reactor.*` 出现在 `store-redis`、`spring-boot-starter`
以及 `server` 的 `..adapter..` 包之外。

### 3.3 资源模型

所有东西都是资源,共享一套元数据、存储和 Watch 机制:

| Kind | 作用域 | 说明 |
|---|---|---|
| `Namespace` | 全局(存于 `_system`) | 分片单位 |
| `Config` | namespace | 期望状态;保留历史 |
| `Instance` | namespace | 可挂租约;租约过期即删除 |
| `ServiceAccount` | 全局 | SDK 身份,存 token 哈希 |
| `Role` / `RoleBinding` | 全局 | 授权规则与绑定 |

**Service 不再是资源**:服务 = 实例按 `spec.service` 分组得到的派生视图,`svc_idx` 消失。

---

## 4. API 设计(`api/`)

### 4.1 资源定义

```protobuf
syntax = "proto3";
package cosky.v1;

import "google/protobuf/timestamp.proto";

message ObjectMeta {
  string namespace = 1;                       // Namespace/ServiceAccount/Role/RoleBinding 为 "_system"
  string name = 2;
  int64 revision = 3;                         // 最后修改它的 namespace revision(只读)
  int64 create_revision = 4;                  // 只读
  map<string, string> labels = 5;
  google.protobuf.Timestamp created_at = 6;   // 只读
  google.protobuf.Timestamp updated_at = 7;   // 只读
  string updated_by = 8;                      // 只读,写入者身份 = 审计链
  int64 lease = 9;                            // 仅 Instance 可设置
}

message Resource {
  ObjectMeta meta = 1;
  oneof spec {                                // kind 由 oneof 决定
    NamespaceSpec namespace = 10;
    ConfigSpec config = 11;
    InstanceSpec instance = 12;
    ServiceAccountSpec service_account = 13;
    RoleSpec role = 14;
    RoleBindingSpec role_binding = 15;
  }
}

enum Kind {
  KIND_UNSPECIFIED = 0;
  KIND_NAMESPACE = 1;
  KIND_CONFIG = 2;
  KIND_INSTANCE = 3;
  KIND_SERVICE_ACCOUNT = 4;
  KIND_ROLE = 5;
  KIND_ROLE_BINDING = 6;
}

message NamespaceSpec { string description = 1; }

enum ConfigFormat {
  CONFIG_FORMAT_UNSPECIFIED = 0;
  CONFIG_FORMAT_YAML = 1;
  CONFIG_FORMAT_PROPERTIES = 2;
  CONFIG_FORMAT_JSON = 3;
  CONFIG_FORMAT_TEXT = 4;
}

message ConfigSpec {
  ConfigFormat format = 1;                    // 显式声明,不再由名称扩展名推断
  string content = 2;                         // ≤ 1 MiB
  string sha256 = 3;                          // server 计算,只读
}

message Endpoint {
  string scheme = 1;                          // http / https / grpc ...
  string host = 2;
  int32 port = 3;
}

message InstanceSpec {
  string service = 1;
  Endpoint endpoint = 2;
  optional int32 weight = 3;                  // 未设置 = 1(由 server 填充);0 = 摘流量但保留注册
  map<string, string> metadata = 4;           // 用户数据,与系统字段物理隔离;≤ 64 项
}

message ServiceAccountSpec {
  string description = 1;
  repeated TokenRef tokens = 2;               // 最多 2 个,支持轮换
}
message TokenRef {
  string id = 1;
  string sha256 = 2;                          // 只存哈希;明文仅在创建时返回一次
  google.protobuf.Timestamp expires_at = 3;
}

enum Verb {
  VERB_UNSPECIFIED = 0;
  VERB_GET = 1;                               // 含 List
  VERB_WATCH = 2;
  VERB_PUT = 3;
  VERB_DELETE = 4;
}
message Rule {
  repeated Kind kinds = 1;
  repeated string namespaces = 2;             // 支持 "*"
  repeated Verb verbs = 3;
  repeated string names = 4;                  // 空 = 全部;否则为精确值或以 * 结尾的前缀(§7.8)
}
message RoleSpec { string description = 1; repeated Rule rules = 2; }

message Subject {
  enum Type { TYPE_UNSPECIFIED = 0; TYPE_SERVICE_ACCOUNT = 1; TYPE_USER = 2; TYPE_GROUP = 3; }
  Type type = 1;
  string name = 2;                            // USER/GROUP 来自 OIDC claim
}
message RoleBindingSpec {
  string role = 1;
  repeated Subject subjects = 2;
  repeated string namespaces = 3;             // 必填;与 Rule.namespaces 取交集,"*" 不增加限制
}
```

### 4.2 命名约束

| 对象 | 规则 | 说明 |
|---|---|---|
| namespace | `^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$` | 不含 `{ } :`,哈希标签安全;`_system` 为保留名 |
| Config name | `^[A-Za-z0-9._/-]{1,253}$`,且按 `/` 切分后**不得有空段、`.` 或 `..`**(即不得以 `/` 开头或结尾、不得含 `//`) | 允许路径式命名;REST 路径中的 `/` 须编码为 `%2F`(§4.5) |
| service | `^[a-z0-9]([-a-z0-9.]{0,61}[a-z0-9])?$` | DNS 风格 |
| Instance name | `^[A-Za-z0-9._-]{1,253}$`,且不得为 `.` 或 `..` | 默认 `<service>-<host>-<port>`(非法字符替换为 `-`),客户端可自定义 |
| ServiceAccount / Role / RoleBinding name | 同 service:`^[a-z0-9]([-a-z0-9.]{0,61}[a-z0-9])?$` | DNS 风格,≤ 63 |
| label / metadata key | `^[A-Za-z0-9]([-A-Za-z0-9_.]*[A-Za-z0-9])?$` | 长度见 §7.1 |
| Endpoint host | 合法 RFC 1123 主机名、IPv4 或 IPv6 字面量 | IPv6 不带方括号 |
| description(各 Spec) | ≤ 1024 字符 | |

proto 命名遵循 buf `STANDARD` 规则:枚举值带类型前缀(`KIND_CONFIG`、`VERB_GET`),服务名带 `Service` 后缀。
唯一例外是 `RPC_RESPONSE_STANDARD_NAME`:`ResourceService.Get` 直接返回 `Resource`(Google AIP-131)。

### 4.3 gRPC 服务

```protobuf
service ResourceService {
  rpc Get(GetRequest) returns (Resource);
  rpc List(ListRequest) returns (ListResponse);
  rpc Put(PutRequest) returns (PutResponse);
  rpc Delete(DeleteRequest) returns (DeleteResponse);
  rpc History(HistoryRequest) returns (HistoryResponse);   // 仅 Config
}

message ResourceKey { string namespace = 1; Kind kind = 2; string name = 3; }

message GetRequest  { ResourceKey key = 1; int64 min_revision = 2; }   // min_revision: 读己之写
message ListRequest {
  string namespace = 1; Kind kind = 2;
  map<string, string> label_selector = 3;
  string service = 4;                                                  // Instance 过滤
  int64 min_revision = 5;
  int32 page_size = 6;                                                 // 0 = 默认 500,最大 1000
  string page_token = 7;                                               // 上一页的 next_page_token;空 = 第一页
}
message ListResponse {
  repeated Resource items = 1;
  int64 revision = 2;                                                  // 每一页都是第一页的 revision(§4.6)
  string epoch = 3;                                                    // revision 只在同一 epoch 内可比较
  string next_page_token = 4;                                          // 空 = 最后一页
}

message PutRequest {
  Resource resource = 1;
  int64 expected_revision = 2;   // 0 = 不校验;-1 = 必须不存在;>0 = CAS
}
message PutResponse {
  int64 revision = 1;
  bool changed = 2;              // 内容未变时 false,不产生新 revision、不发事件
}

message DeleteRequest  { ResourceKey key = 1; int64 expected_revision = 2; }
message DeleteResponse { int64 revision = 1; bool deleted = 2; }

message HistoryRequest  { ResourceKey key = 1; int32 limit = 2; string page_token = 3; }   // limit 为 0 或超过 history-size 时按 history-size 截断
message HistoryResponse { repeated Resource revisions = 1; string next_page_token = 2; }  // 新 → 旧

service WatchService {
  rpc Watch(stream WatchRequest) returns (stream WatchResponse);   // 一条流复用多个 watch
}

message WatchRequest {
  oneof request {
    Create create = 1;
    Cancel cancel = 2;
  }
  message Create {
    int64 watch_id = 1;                 // 客户端分配
    string namespace = 2;
    Kind kind = 3;
    string name = 4;                    // 为空 = 该 kind 全部
    string service = 5;                 // Instance 按服务过滤
    int64 from_revision = 6;            // 0 = 先推送当前全量,再推增量
    string from_epoch = 7;              // from_revision > 0 时必填;与当前 epoch 不同则返回 gone
  }
  message Cancel { int64 watch_id = 1; }
}

message WatchResponse {
  int64 watch_id = 1;
  oneof payload {
    Snapshot snapshot = 2;              // from_revision = 0 时的初始全量
    Events events = 3;                  // 同一 revision 的事件,可能分多条消息
    int64 progress = 4;                 // 进度书签:当前已追到的 revision,空闲时周期发送
    int64 gone = 5;                     // (from_epoch, from_revision) 无法续传;值为最早可续传 revision,客户端需重新 List
    Canceled canceled = 6;              // server 结束单个 watch,流与其他 watch 不受影响
  }
  // Create 被拒(WATCH_QUOTA_EXCEEDED)、namespace 被删除(NAMESPACE_NOT_FOUND)或失去权限(PERMISSION_DENIED),§7.2 / §7.3
  message Canceled { string message = 1; ErrorDetail detail = 2; }
  message Snapshot { repeated Resource items = 1; int64 revision = 2; string epoch = 3; bool more = 4; }   // more:还有分块(§4.6)
  message Events { int64 revision = 1; repeated Event events = 2; bool more = 3; }                       // more:同一 revision 还有事件
  message Event {
    enum Type { TYPE_UNSPECIFIED = 0; TYPE_PUT = 1; TYPE_DELETE = 2; }
    Type type = 1;
    Resource resource = 2;              // DELETE 时 spec 为删除前的最后状态,revision / updated_at / updated_by 为删除操作
  }
}

service LeaseService {                                                // 授权见 §7.8
  rpc Grant(GrantRequest) returns (GrantResponse);
  rpc KeepAlive(stream KeepAliveRequest) returns (stream KeepAliveResponse);
  rpc Revoke(RevokeRequest) returns (RevokeResponse);
}

message GrantRequest  { string namespace = 1; int32 ttl_seconds = 2; }   // 默认 30,范围 [5, 300]
message GrantResponse { int64 lease = 1; int32 ttl_seconds = 2; }
message KeepAliveRequest  { string namespace = 1; int64 lease = 2; }
message KeepAliveResponse { int64 lease = 1; int32 ttl_seconds = 2; int32 attached_count = 3; }    // ttl_seconds = 0 表示租约已不存在;流以 UNAVAILABLE 结束不代表租约丢失
message RevokeRequest  { string namespace = 1; int64 lease = 2; }
message RevokeResponse {}

service ViewService {                                                 // 派生只读视图;只有拓扑的边落库,且是软状态(D30)
  rpc Services(ServicesRequest) returns (ServicesResponse);        // 服务列表 + 实例数
  rpc Topology(TopologyRequest) returns (TopologyResponse);        // consumer → producer 边
  rpc Stats(StatsRequest) returns (StatsResponse);
}
```

### 4.4 错误码(gRPC status ↔ HTTP)

| 场景 | gRPC | HTTP |
|---|---|---|
| 不存在 | `NOT_FOUND` | 404 |
| revision 不匹配 | `ABORTED`(detail `REVISION_CONFLICT`,带当前 revision) | 409 |
| 租约不存在/已过期 | `FAILED_PRECONDITION`(detail `LEASE_NOT_FOUND`) | 412 |
| 删除非空 namespace | `FAILED_PRECONDITION`(detail `NAMESPACE_NOT_EMPTY`) | 409 |
| 单条流 watch 数超限 | `RESOURCE_EXHAUSTED`(detail `WATCH_QUOTA_EXCEEDED`) | 429 |
| 未认证 / 无权限 | `UNAUTHENTICATED` / `PERMISSION_DENIED` | 401 / 403 |
| 参数非法 | `INVALID_ARGUMENT` | 400 |
| `min_revision` 等待超时 | `DEADLINE_EXCEEDED` | 504 |
| 存储不可用 / revision 回退 | `UNAVAILABLE` | 503 |

HTTP 状态按**错误原因**映射,而不是按 gRPC code 一刀切;未带 CoSky 原因的 gRPC code 按 `google.rpc.Code` 的标准映射。

### 4.5 REST 映射

| 方法 | 路径 | 对应 |
|---|---|---|
| GET | `/v1/namespaces/{ns}/{kind}` | List(`?labelSelector=&service=&minRevision=&pageSize=&pageToken=`) |
| GET | `/v1/namespaces/{ns}/{kind}/{name}` | Get |
| PUT | `/v1/namespaces/{ns}/{kind}/{name}` | Put;`If-Match: <revision>`、`If-None-Match: *` |
| DELETE | `/v1/namespaces/{ns}/{kind}/{name}` | Delete;`If-Match` |
| GET | `/v1/namespaces/{ns}/configs/{name}/history` | History(`?limit=&pageToken=`) |
| POST | `/v1/namespaces/{ns}/configs/{name}:rollback` | `{ "toRevision": n }`,server 读历史后 Put |
| GET | `/v1/namespaces/{ns}/watch?kind=&fromRevision=&fromEpoch=` | Watch,SSE,供 Dashboard 实时刷新 |
| GET | `/v1/namespaces/{ns}/views/{services\|topology\|stats}` | Views |
| POST / GET | `/v1/namespaces/{ns}/configs:import` / `:export` | zip 批量导入导出(保留现有能力) |
| GET | `/v1/me` | 当前身份与有效权限 |

`{kind}` 取 `configs` / `instances`;全局对象走 `/v1/{namespaces|serviceaccounts|roles|rolebindings}/{name}`。
REST 与 gRPC 由同一 server 进程提供,JSON 编码使用 proto3 JSON 映射。
路径中的 `{name}` 是单个路径段:Config 名中的 `/` 必须编码为 `%2F`(WebFlux 的 `PathPattern` 按段匹配后再解码)。

**OpenAPI**(`api/` 构建产物 `cosky.yaml`)采用混合生成:`components.schemas` 由 proto 描述符按 proto3 JSON 映射自动生成,
保证与 proto 同步;`paths` 在 `api/src/main/openapi/paths.yaml` 中手写(本节路由无法从 RPC 自动推导:
`{kind}` 复数段、`If-Match` 头、SSE、`:rollback`、导入导出、token 端点);构建时合并并校验引用完整。

### 4.6 消息大小与分页(D23)

gRPC 默认单条消息上限 4 MiB,而一个 namespace 的全量可达 50 MB。所有可能变大的响应都按字节分开:
单条消息的资源总量不超过 `max-message-bytes`(默认 2 MiB)。单个资源最大约 1 MiB,所以每条消息至少放得下一个。

| 响应 | 方式 | 一致性 |
|---|---|---|
| List | `page_size` + `page_token`;一页可能因字节上限少于 `page_size` | 见下 |
| History | `page_token`;`limit` 仍是总条数 | 按 revision 倒序,游标是 revision |
| Watch `Snapshot` | `more = true` 表示还有分块,最后一块之前不发事件 | 所有分块来自同一个不可变快照 |
| Watch `Events` | `more = true` 表示同一 revision 还有事件,客户端收齐后原子应用 | 同一 revision |

- **List 的一致性**:server 无状态,不为翻页保留快照。token 携带 epoch、第一页的 revision 和游标(上一页最后一个 name)。
  每页读取当时的缓存,所以对象都不早于第一页的 revision。客户端从这个 revision 开始 Watch,只应用比对象 revision 更新的事件,即可收敛。
  需要一次拿到一致视图时用 Watch 的 `Snapshot`。
- **token 失效**:epoch 变化后 token 作废,返回 `INVALID_ARGUMENT`(字段 `page_token`),客户端从第一页重新 List。

---

## 5. 存储设计

### 5.0 Store SPI(`store/`,纯协程)

```kotlin
/** namespace 时间线上的位置;revision 只在同一 epoch 内可比较。 */
data class Position(val epoch: String, val revision: Long)

interface Store {
    /** 原子批写;全部未变化时返回当前 revision 且 changed = 0;一批内同一 key 只能出现一次。 */
    suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult

    /** 当前位置;namespace 不是 active 时为 null。 */
    suspend fun position(namespace: String): Position?
    fun readAll(namespace: String): Flow<Resource>                           // 全量加载,含完整只读元数据
    fun versions(namespace: String): Flow<ObjectVersion>                     // 只读 name → revision,用于反熵
    /** 有序、可续传;超出窗口抛 RevisionGoneException,`from` 不在当前时间线上抛 TimelineDivergedException;namespace 不再是 active 时结束。 */
    fun changes(namespace: String, from: Position): Flow<ChangeBatch>
    /** 日志中截至 `until` 的最新完整 revision,最多 `limit` 个事件,用于预填窗口(D29)。 */
    suspend fun recentChanges(namespace: String, until: Long, limit: Int): List<ChangeBatch>
    suspend fun history(key: ResourceKey, limit: Int): List<Resource>

    suspend fun grantLease(namespace: String, ttl: Duration, owner: String): GrantResult
    suspend fun leaseOwners(namespace: String, leases: Collection<Long>): Map<Long, String?>   // 用于校验所有者(§7.8);null = 已不存在
    suspend fun keepAlive(namespace: String, leases: Collection<Long>): Map<Long, LeaseRenewal?>   // 批量,TTL 由存储保存;null = 已不存在
    suspend fun expireLease(namespace: String, lease: Long, actor: String, force: Boolean): Boolean   // force = Revoke
    suspend fun expiredLeases(namespace: String, limit: Int): List<Long>
    suspend fun leaseStats(namespace: String): LeaseStats                    // 租约总数与已到期数,用于回收保护

    /** 刷新拓扑边,保留 `retention`;软状态,不经过 revision(D30)。 */
    suspend fun recordTopology(namespace: String, edges: Collection<TopologyEdge>, retention: Duration)
    suspend fun topology(namespace: String): Set<TopologyEdge>               // 未过期的边

    /** state = active,新 epoch;已是 active 时不变;还有待清理的数据时先清理。 */
    suspend fun createNamespace(namespace: String): Position
    suspend fun namespaceState(namespace: String): NamespaceState?           // ACTIVE / TERMINATING / null
    /** 原子判空并置为 terminating;此后写入返回 NamespaceNotActive(§7.2)。 */
    suspend fun terminateNamespace(namespace: String): TerminateResult
    /** 清理 terminating namespace 的全部数据(epoch、rev、log、对象、历史、租约、拓扑),只留 state 墓碑;可重入。 */
    suspend fun dropNamespace(namespace: String)

    /** 带过期的互斥锁,用于 Reaper 选主;owner 相同时为续约。 */
    suspend fun tryLock(name: String, owner: String, ttl: Duration): Boolean
    suspend fun unlock(name: String, owner: String)
}

/** 续期结果;`attached` 是挂在该租约上的实例数(D31)。 */
data class LeaseRenewal(val ttl: Duration, val attached: Int)

sealed interface ApplyResult {
    data class Applied(val revision: Long, val changed: Int) : ApplyResult
    data class Conflict(val index: Int, val currentRevision: Long?) : ApplyResult
    data class LeaseNotFound(val index: Int, val lease: Long) : ApplyResult
    data object NamespaceNotActive : ApplyResult
    data object BatchTooLarge : ApplyResult                                  // 超过操作数或总字节上限(§7.1)
}
```

用 sealed 结果表达业务失败,异常只用于基础设施故障;`changes` 是冷 `Flow`,取消收集即停止读取。
发现时间线分叉时由 Store 实现更换 epoch 并抛 `TimelineDivergedException`,调用方只需重新加载(§6.4)。

### 5.1 Redis 键布局(`store-redis/`)

所有键形如 `cosky:{<ns>}:…`,`{<ns>}` 是哈希标签,一个 namespace 的全部键落在同一 slot。全局对象使用 `ns = _system`。

| 键 | 类型 | 内容 |
|---|---|---|
| `cosky:{ns}:state` | String | `active` / `terminating`;写入前必须是 `active`(§7.2) |
| `cosky:{ns}:epoch` | String | 时间线标识,128 位随机值;`createNamespace` 创建,分叉时比较并更换 |
| `cosky:{ns}:rev` | String | namespace revision,INCR 递增 |
| `cosky:{ns}:obj:<kind>` | Hash | name → 资源 protobuf 字节(确定性序列化,meta 中只读字段除外) |
| `cosky:{ns}:ver:<kind>` | Hash | name → 元数据 `<revision>:<create_revision>:<created_ms>:<updated_ms>:<actor>` |
| `cosky:{ns}:log` | Stream | 变更日志;ID = `<revision>-<seq>`;字段 `t`(P/D)、`k`(kind)、`n`(name)、`m`(元数据)、`o`(对象字节);`MAXLEN ~ 10000` |
| `cosky:{ns}:hist:config:<name>` | Sorted Set | score = revision,member = `<元数据>\0<对象字节>`;保留最近 20 个 |
| `cosky:{ns}:hist:idx` | Set | 有历史的 Config name,`dropNamespace` 据此枚举,不需要 SCAN |
| `cosky:{ns}:lease` | Sorted Set | lease id → 到期时间(ms,Redis `TIME`) |
| `cosky:{ns}:lease:ttl` | Hash | lease id → TTL(ms),续期时据此计算新的到期时间 |
| `cosky:{ns}:lease:owner` | Hash | lease id → 授予它的主体(§7.8) |
| `cosky:{ns}:lease:attach` | Sorted Set | member = Instance name,score = lease id |
| `cosky:{ns}:lease:seq` | String | 租约 id 生成器;id 小于 2^53,作为 score 不丢精度 |
| `cosky:{ns}:topology` | Sorted Set | member = `<consumer>\0<producer>`,score = 到期时间(ms,Redis `TIME`);软状态(D30) |
| `cosky:leader:reaper` | String | 回收 Leader 锁,`SET NX PX` |

另有 Pub/Sub 频道 `cosky:hint`,只用作追尾的唤醒提示(§6.1)。

设计要点:
- **元数据与对象分开存**:Lua 无法改写 protobuf,所以把只读元数据放进 `ver:<kind>`,
  server 读取时把两者组装成完整 `Resource`。CAS 只需比较其中的 revision。
- **事件和历史带元数据**:Stream 的 `m` 字段与历史成员都包含元数据,不回读也能还原完整 `Resource`,
  `updated_by` + Config 历史的审计链(D12)才成立。`actor` 放在最后,可以包含 `:`,不允许包含控制字符。
- **历史成员唯一**:成员包含 revision,内容相同的版本不会互相覆盖,A → B → A 保留三条。
- **实例挂载集中存**:`ZADD lease:attach` 天然把实例从旧租约迁到新租约,一个实例不可能同时挂两个租约。
  脚本不需要知道旧租约,也不需要动态键;Lua 读不了 protobuf 中的 `lease`,由 server 传入又可能已过期。
- **无变化判断**:`obj` 的字节由确定性序列化产生(map 按 key 排序),否则 labels / metadata 顺序不同就会产生新 revision。
  内存实现按消息语义比较,行为相同。
- **Stream ID 即 revision**:`XRANGE log <rev+1>-0 +` 就是"从 revision 续传";一次原子批写共享同一 revision,用 `seq` 区分。
- 时钟统一使用 Redis `TIME`,server 副本之间不依赖本机时钟。

### 5.2 脚本(所有键都在 `KEYS` 中显式声明)

| 脚本 | 作用 |
|---|---|
| `apply.lua` | 通用原子批写:校验 `state = active` → 逐项校验 CAS 与租约 → 内容不变的项跳过 → 若有变化则 `INCR rev` → `HSET/HDEL obj/ver` → 维护 `lease:attach` → Config 写历史并 `SADD hist:idx` → `XADD log`(每项一条,`MAXLEN ~`)→ `PUBLISH cosky:hint` → 返回新 revision 或冲突明细 |
| `lease_grant.lua` | 校验 `state = active` → 生成 id,`ZADD lease <TIME+ttl> id`,`HSET lease:ttl id ttl`,`HSET lease:owner id owner` |
| `lease_keepalive.lua` | 批量:读 `lease:ttl`,存在则 `ZADD lease <TIME+ttl> id` 并用 `ZCOUNT lease:attach id id` 返回挂载实例数(D31),不存在返回 0;已到期但未回收的租约同样续期(到期只表示可以回收);**不改 revision、不写日志** |
| `lease_expire.lua` | 对单个租约:确认 `score ≤ TIME` → `ZRANGEBYSCORE lease:attach id id` 取挂载实例 → 有实例则等价于一次 apply 删除它们 → 清理 `lease:attach`、`lease:ttl`、`lease:owner`、`lease`。Revoke 复用此脚本(跳过到期检查);`actor` 到期回收时为 `system`,Revoke 时为调用者 |
| `tail.lua` | 一次原子读:比较 epoch → 比较 `rev` 与已应用 revision → 比较已应用 revision 的日志条目 → 返回后续事件(§6.1、§6.4) |
| `read_batch.lua` | 全量读取的一批:`HSCAN ver` 后在同一脚本内 `HGET obj`,对象与元数据总是匹配,也避免大 Hash 的 `HGETALL` 阻塞 Redis |
| `epoch_rotate.lua` | 比较并更换 epoch,多个副本同时发现分叉时只有一个生效 |
| `lease_query.lua` | 用 Redis `TIME` 统计租约总数与到期数,并列出到期租约 |
| `lock_acquire.lua` / `lock_release.lua` | `tryLock` / `unlock`:比较 owner 后续约或释放 |
| `topology_record.lua` | 校验 `state = active` → `ZADD topology <TIME+retention> edge` → `ZREMRANGEBYSCORE` 清理已到期的边;**不改 revision、不写日志** |
| `topology_read.lua` | 用 Redis `TIME` 读取未到期的边 |
| `namespace_create.lua` | 已是 `active` 时返回当前位置;否则 `SET state active`,写入新 epoch |
| `namespace_terminate.lua` | `HLEN obj:config` 与 `HLEN obj:instance` 都为 0 时 `SET state terminating`,否则返回不为空 |

`dropNamespace` 不是单个脚本:先读 `hist:idx` 和 `lease`,再分批 `UNLINK` 各键,只留 `state`。任何一步中断都可以重新执行。

`apply.lua` 伪代码:

```lua
-- KEYS: state, rev, log, lease, lease:attach, hist:idx, 然后每个 op: obj, ver, hist(仅 Config)
-- ARGV: namespace, actor, n_ops, 然后每个 op: type, kind, name, expected_rev, bytes, lease;now 由脚本内 TIME 取得
if GET state ~= "active" then return {"NAMESPACE_NOT_ACTIVE"} end
local changed = {}
for each op do
  local cur = parse(HGET ver name)                  -- {rev, create_rev, created_ms, ...} 或 nil
  check expected_rev against cur.rev                -- 失败 → return {"CONFLICT", index, cur.rev}
  if op.lease ~= 0 and not ZSCORE(lease, op.lease) > now then return {"LEASE_NOT_FOUND", index} end
  if op.type == PUT and HGET(obj, name) == op.bytes then skip end     -- 无变化
  if op.type == DELETE and cur == nil then skip end                   -- 无变化
  table.insert(changed, op)
end
if #changed == 0 then return {"OK", GET rev, 0} end
local rev = INCR rev
for i, op in ipairs(changed) do
  local m = rev..":"..(cur.create_rev or rev)..":"..(cur.created_ms or now)..":"..now..":"..actor
  if PUT:    o = op.bytes; HSET obj name o; HSET ver name m
             if op.lease ~= 0 then ZADD lease:attach op.lease name else ZREM lease:attach name end
             Config 时 ZADD hist rev (m.."\0"..o) + ZREMRANGEBYRANK 截断 + SADD hist:idx name
  if DELETE: o = HGET obj name; HDEL obj/ver name; ZREM lease:attach name
  XADD log MAXLEN ~ 10000 (rev.."-"..(i-1)) t P|D k kind n name m m o o
end
PUBLISH cosky:hint namespace                        -- 只是提示,丢失由轮询兜底
return {"OK", rev, #changed}
```

DELETE 的 `m` 描述删除操作本身(revision、时间、操作者),`o` 是删除前的对象。

`tail.lua` 伪代码(`applied_m` 是已应用 revision 首条日志的 `m`):

```lua
-- KEYS: epoch, rev, log
-- ARGV: known_epoch, applied, applied_m, count
if GET epoch ~= known_epoch then return {"EPOCH", GET epoch} end
if (GET rev or 0) < applied then return {"DIVERGED"} end            -- 回退,尚未涨回
if applied > 0 then
  local e = XRANGE log applied-0 applied-0
  if #e == 0 then return {"GONE"} end                               -- 落后超出窗口
  if e.m ~= applied_m then return {"DIVERGED"} end                  -- 回退后已涨回,同一 revision 内容不同
end
return {"OK", GET rev, XRANGE log (applied+1)-0 + COUNT count}      -- COUNT 截断时调用方丢弃末尾不完整的 revision
```

### 5.3 持久性与 Redis 部署建议

- 必须开启 AOF(`appendfsync everysec`);主从切换存在丢失最后约 1 秒写入的风险,在文档中明示。
- 支持 Standalone / Sentinel / Cluster;Cluster 下按 namespace 自然分布到各分片,任何命令都不跨 namespace(§6.1)。
- server 检测时间线分叉(§6.4),发现后更换 epoch 并让所有 watcher 重新 List,避免客户端停留在已被丢弃的视图上。
- **启动检查(D31)**:server 连接后读取 `INFO`(Cluster 下检查每个 master):`maxmemory_policy` 不是 `noeviction`,或版本低于 7.0 时拒绝启动;`INFO` 被限制而读不到时只记 WARN。CI 矩阵覆盖 Redis 7.0、7.4 和 Valkey。
- **不使用 `WAIT`(D31)**:建议配置 `min-replicas-to-write 1` 和 `min-replicas-max-lag 10` 缩小丢写窗口。残余风险:主从切换时,已确认的 Config 写入和 token 吊销仍可能回滚,只能通过 ERROR 日志和 `cosky_revision_regression_total` 发现。

---

## 6. 核心流程

### 6.1 Watch Cache(server 内)

```
启动 / 新 namespace:
  E0 = GET epoch(由 createNamespace 创建)
  R0 = GET rev
  分批 read_batch.lua                     -- HSCAN ver + HGET obj,每个对象与其元数据原子读出;可能比 R0 新
  XRANGE log - +                          -- 预填窗口(D29);比对象 revision 更新的事件同时回放
  进入追尾:收到提示或轮询到期时执行 tail.lua

内存结构(每 namespace):
  objects: kind → name → Resource
  window:  最近 N 个事件的环形缓冲(N 与 Stream MAXLEN 相同;按事件计数,整批淘汰)
  epoch:   当前时间线
  revision: 已应用的最大 revision
```

- 所有 Get/List/Watch 由 Watch Cache 服务,只有写操作打到 Redis。
- `min_revision`:缓存 revision 未达到时挂起等待(默认最多 3 秒),实现跨副本的"读己之写"。
- 客户端 `from_epoch` 不一致或 `from_revision` 早于窗口 → 返回 `gone`,客户端重新 List 后从新位置 Watch。
- `from_revision` 大于副本 revision(D29):副本落后于客户端,位置仍然有效。该 watch 挂起等副本追上,不影响同一条流上的其他 watch;其间 epoch 变化则发 `gone`。超过 `read.min-revision-timeout` 后读一次 Store:Store 有这个位置,说明只是本副本落后,以 `UNAVAILABLE` 结束整条流,客户端换端点重连;Store 也没有,说明这个位置来自无人发现的被丢弃时间线(§6.4 的残余风险),发 `gone`,否则客户端会一直重连。
- 重新加载期间 watcher 只是等待,不会被结束。新视图的 epoch 不变且位置仍在窗口内就继续,所以一次 Store 读取失败不会让所有客户端重新 List。namespace 不再是 active 时,watcher 收到 `canceled`(`NAMESPACE_NOT_FOUND`)。
- `MAXLEN ~` 可能截断最旧 revision 的批次,这个 revision 就不完整,不能从它之前续传。可续传下限由最旧条目决定:序号为 0 时是该 revision 减 1,否则是该 revision 本身。
- 空闲 watcher 每 10 秒收到一次 `progress`,保证其记录的 revision 持续前进,重连时不易落出窗口。

**追尾(D21)**:不使用 `XREAD`。Cluster 下跨 slot 的多键 `XREAD` 直接报 `CROSSSLOT`,每 namespace 一条阻塞连接又会让连接数随 namespace 增长。

- **提示**:写脚本最后执行 `PUBLISH cosky:hint <ns>`,每个 server 副本只持有一条订阅连接。普通 Pub/Sub 在 Cluster 中广播到所有节点,
  订阅任一节点即可,三种部署方式行为一致。提示丢失只影响延迟,不影响正确性。
- **读取**:`NamespaceCache` 收到提示(连续提示合并)或距上次读取超过 `tail-poll-interval`(默认 1 秒)时执行 `tail.lua`;
  订阅连接重建后立即读取全部 namespace。所有命令都不阻塞,可以共用一条复用连接。
- **负载**:兜底轮询约为 副本数 × namespace 数 ÷ 间隔 次/秒。单节点 Redis 7.4 容器实测空轮询约 15 万次/秒,namespace 很多时可调大间隔。

`tail.lua` 一次原子读取返回以下结果之一:

| 结果 | 含义 | 处理 |
|---|---|---|
| `OK` | 时间线连续 | 应用后续事件 |
| `EPOCH` | 其他副本已更换 epoch,或 epoch 已丢失 | 重新加载,向 watcher 发送 `gone` |
| `DIVERGED` | 本副本发现时间线分叉 | 按 §6.4 处理 |
| `GONE` | 落后超出 Stream 窗口 | 重新加载,落在新窗口之外的 watcher 收到 `gone` |

**反熵(D19)**:增量路径上的任何缺陷都不应永久生效。server 每 `anti-entropy-interval`(默认 5 分钟)扫描 `ver`,与缓存比较 name → revision,
扫描期间有变更的 name 跳过,其余不一致按分叉处理(§6.4)。客户端的低频重建见 §7.5。

### 6.2 租约与实例生命周期

```
client                         server                         Redis
  │ Grant(ns, ttl=30) ───────▶ │ lease_grant.lua ───────────▶ │
  │ Put Instance{lease=L} ───▶ │ apply.lua ─────────────────▶ │  PUT 事件
  │ KeepAlive(L) 每 10s ─────▶ │ lease_keepalive.lua(批量)─▶ │  无事件
  │                            │                              │
  │ 进程正常退出:Revoke(L) ──▶ │ lease_expire.lua ──────────▶ │  DELETE 事件
  │                            │                              │
  │ 进程崩溃,租约到期           │ Reaper Leader 每 1s:          │
  │                            │  ZRANGEBYSCORE lease ≤ now   │
  │                            │  逐个 lease_expire.lua ─────▶ │  DELETE 事件
```

- KeepAlive 返回 `ttl_seconds = 0`(租约已过期被回收)时,客户端重新 Grant 并重新注册其全部实例。
- KeepAlive 响应带 `attached_count`,即挂在该租约上的实例数(D31)。主从切换可能丢失最近的 Put,而更早的租约还在,续期一直成功。客户端发现它少于本地注册数时重新 Put 全部实例,不重新 Grant;内容不变的项会被跳过,所以并发注册造成的误判无害。
  这也意味着手工删除存活进程的 Instance 会被恢复:要摘除仍在运行的实例,把 `weight` 设为 0。
- 存储不可用时 KeepAlive 流以 `UNAVAILABLE` 结束,**不代表租约丢失**:客户端保留注册信息,重连成功后立即续期,不等下一周期。
- 一个进程在一个 namespace 只持有一个租约,心跳成本 O(进程数),与实例数无关。
- 持久实例 = 不挂租约的 Instance,无特殊分支。
- Reaper 由 `cosky:leader:reaper` 锁选主(PX 15s,每 5s 续约);Leader 失效最多延迟一个锁周期加一个恢复宽限,只影响回收时效,不影响正确性。

**回收保护(D20)**:控制面故障超过 TTL 后恢复时,到期的主要是仍然存活的进程。如果立即回收,所有实例都会被删除,
消费者读到空列表,客户端再集中重新注册。Reaper 每轮依次判断:

1. **恢复宽限**:成为 Leader 或访问存储失败后,`reaper-grace`(默认 30 秒)内不回收。这段时间足够客户端走完最长 10 秒的重连退避并立即续期;
   到期但未回收的租约仍可续期,所以存活进程不需要重新注册。存储挂起而不报错时同样算访问失败:两轮之间超过 5 个 `reaper-interval` 就重新开始宽限。
2. **大比例到期暂停**:按 namespace 读取 `leaseStats`。租约数不少于 `protection-min-leases`(默认 10)且到期比例超过 `protection-threshold`(默认 50%)时暂停回收,
   记录 ERROR 日志并置位 `cosky_lease_protection_active`。这一层覆盖宽限检测不到的情况,例如客户端与 server 之间分区、Reaper 到 Redis 却正常。
   比例回落后自动恢复;持续超过 `protection-max-duration`(默认 5 分钟)后不再暂停,避免死实例一直留存。
3. **单轮上限**:每个 namespace 每轮最多回收 `reaper-max-per-tick`(默认 100)个租约,让删除事件平滑扩散。

代价是真实的大面积崩溃时,死实例最多多保留 `protection-max-duration`,调用方要靠重试和熔断应对。正常退出走 Revoke,不计入到期比例。

### 6.3 认证与授权

- **ServiceAccount**:token 为 32 字节随机数,格式 `cosky_<id>_<secret>`;server 只存 SHA-256,
  校验时查 Watch Cache(`_system` namespace),不访问 Redis。gRPC metadata / HTTP 头均为 `Authorization: Bearer`。
- **人员**:配置 OIDC 时,Dashboard 走授权码 + PKCE,server 用 JWKS 校验 ID Token,
  `sub` → `USER`、`groups` claim → `GROUP`。未配置 OIDC 时,Dashboard 使用 ServiceAccount token 登录。
- **Bootstrap**:首次启动若 `_system` 中无任何 RoleBinding,读取环境变量 `COSKY_BOOTSTRAP_TOKEN`
  创建 `admin` ServiceAccount 并绑定内置 `admin` 角色(`namespaces = ["*"]`)。
- **内置角色**:`admin`(全部)、`viewer`(GET/WATCH 全部)、`client`(Config GET/WATCH,Instance 全部)。
  内置角色的规则对所有 namespace 生效,由 RoleBinding 的 `namespaces` 限定范围;按服务隔离时用自定义 Role(§7.8)。
- **审计**:每个 revision 的 `updated_by` + Config 历史即变更追溯;每次写入和认证事件另输出一行结构化审计日志(§7.8),
  由运维的日志系统收集,server 不存储审计数据。
- **拓扑视图(D30)**:server 记录"consumer × Watch 的服务",即 consumer → producer 边,无需客户端额外写入。只记录按 service 过滤的 Instance watch:watch 整个 namespace 不代表依赖全部服务。
  consumer 优先取 gRPC metadata `cosky-client-name`(starter 填 `spring.application.name`;只是标签,不参与鉴权),其次取已认证主体,都没有时不记录。
  每个副本每 `view.topology-flush-interval` 把当前 watcher 的边写入 `topology`,保留 3 倍间隔;`Topology` 直接读 Store。

### 6.4 时间线分叉检测(D19)

Redis 主从切换或重启可能丢失最后约 1 秒的写入。此后 `rev` 先回退,新写入又复用这些 revision,内容却不同。
只比较数字发现不了这种情况:从已应用 revision 往后读,读不到也不会报错;revision 一旦涨回原值,客户端的 `from_revision` 又显得合法。

所以 revision 只在同一 **epoch** 内可比较。`tail.lua` 每次读取时在同一原子操作里检查:

- `rev` 小于已应用 revision:发生了回退,尚未涨回;
- 已应用 revision 的日志条目与记录的 `m` 不同:回退后已经涨回。`m` 包含写入时间,不同时间线上几乎不可能相同。

任一成立即为分叉,发现分叉的 server:

1. 将该 namespace 标记为 `diverged`,暂停服务读请求(返回 `UNAVAILABLE`,原因 `REVISION_REGRESSION`);
2. 用 `epoch_rotate.lua` 比较并更换 epoch,多个副本同时发现时只有一个生效;
3. 重新执行启动流程加载全量;
4. 向该 namespace 所有 watcher 发送 `gone`,客户端重新 List;
5. 记录 ERROR 日志与指标 `cosky_revision_regression_total`。

其他副本下次读取时得到 `EPOCH`,执行第 1、3、4 步。离线客户端重连时带旧 `from_epoch`,同样收到 `gone`。
`_system` 适用同样的规则,token 与权限缓存随重新加载重建。

残余风险:

- 分叉发生后、epoch 更换前(通常不超过一个轮询间隔)连上的 watcher 可能按旧 epoch 续传;epoch 更换后它们也会收到 `gone`,视图自愈。
- 所有 server 副本与 Redis 同时重启时,没有副本记得旧位置,发现不了分叉;客户端若带旧 epoch 且 revision 已涨回,由客户端低频重建兜底(§7.5)。
- `expected_revision` 与 `min_revision` 不带 epoch,切换后的短窗口内可能匹配到被复用的 revision,接受此风险。

### 6.5 客户端

```kotlin
// CoSkyClient 持有一个 CoroutineScope(SupervisorJob),close() 时取消全部 Watch 与保活
val cosky = CoSkyClient(endpoints = listOf("cosky:9090"), token = System.getenv("COSKY_TOKEN"))
val ns = cosky.namespace("prod")

val config: StateFlow<Resource?> = ns.configs.watch("order-service.yaml")          // 热流,多处订阅共享一个 Watch
val current = config.first { it != null }                                         // suspend 等待首个值
val registration = ns.instances.register(service = "order", endpoint = Endpoint("http", host, 8080))  // suspend
val payment: StateFlow<List<Resource>> = ns.instances.watch(service = "payment")
val target = ns.loadBalancer("payment").choose()                                  // 非 suspend:只读本地快照
registration.close()                                                              // suspend:Revoke 租约
```

- 同一 key 的多个 `watch` 调用共享一个底层 Watch,它存活到 client 关闭。热路径只读 `StateFlow.value`,不算订阅,所以订阅数无法判断何时可以取消;一个进程关心的 key 也是有限的。
- 热路径(`choose()`、`config.value`)只读 `StateFlow.value`,不挂起、不分配。
- 一条 gRPC 连接承载全部 Watch 与 KeepAlive;多个 server 端点轮换重连,重连带 `from_revision` 和 `from_epoch`。
- 快照落盘(§7.5):有快照时启动先加载,立即可用,连接成功后从快照位置续传。
- 负载均衡:一个 `LoadBalancer` SPI,内置加权随机(前缀和 + 二分)与轮询。

---

## 7. 详细设计

### 7.1 资源语义

- **Put 是整体替换**,不支持局部 patch。请求中的只读字段(`revision`、`create_revision`、时间、`updated_by`、`sha256`)被忽略,由 server 填写。
- **创建 / 更新**由 `expected_revision` 区分:`-1` 仅创建,`>0` CAS 更新,`0` 不校验(upsert)。
- **无变化写入**:内容(确定性序列化后的对象字节)与当前一致时返回 `changed = false`,不推进 revision,不产生事件。
- **删除后重建**:`create_revision` 重新开始;Config 历史在删除后**保留**到 Config 被重建或 namespace 被删除,可通过 rollback 从历史恢复(恢复即重建)。8.0 不做 TTL,历史量受"出现过的 name 数 × `history-size`"限制。
- **Instance 与租约**:租约必须属于同一 namespace,且在写入时未到期;允许把实例从一个租约迁到另一个,或改为不挂租约,旧租约随后到期不影响该实例。
- **labels 选择器**:仅支持等值匹配,多个条件为 AND。

| 限制 | 默认值 |
|---|---|
| Config 内容 | ≤ 1 MiB |
| Instance metadata | ≤ 64 项;key ≤ 63 字符,value ≤ 1024 字符 |
| labels | ≤ 32 项;key ≤ 63,value ≤ 253;key 字符集见 §4.2 |
| description | ≤ 1024 字符 |
| History `limit` | 0 或超过 `history-size` 时按 `history-size` 截断,负数非法 |
| 一次批写(apply)的操作数 | ≤ 100 |
| 一次批写(apply)的总字节 | ≤ 8 MiB |
| 单条消息的资源总量 | ≤ 2 MiB(§4.6) |
| List `page_size` | 0 = 默认 500,最大 1000 |
| `Rule.names` | ≤ 64 项 |
| 单 namespace 对象数 | 软上限 100,000(超过告警,不拒绝) |
| Config 历史 | 每个 Config 最近 20 个 revision |

### 7.2 Namespace 生命周期

- `_system` 在 server 首次启动时自动创建;`default` namespace 在 bootstrap 时创建。
- **创建**:先 `Store.createNamespace`(`state = active`,新 epoch),再 Put `_system` 中的 Namespace 对象。
- **写入(D25)**:`apply.lua`、`lease_grant.lua` 要求 `state = active`,否则返回 `NOT_FOUND`(detail `NAMESPACE_NOT_FOUND`)。判断在脚本内完成,不依赖缓存,所以迟到的写入不会让已删除的 namespace 复活。
- **只允许删除空 namespace**(无 Config、无 Instance),否则返回 `FAILED_PRECONDITION` / HTTP 409(`NAMESPACE_NOT_EMPTY`),防止误删整片数据。删除分三步,每步都可重入:
  1. `Store.terminateNamespace`:原子判空并置 `terminating`,此后写入都被拒绝;
  2. `Store.dropNamespace`:清理全部键,只留 `state` 作为墓碑;
  3. 删除 `_system` 中的 Namespace 对象,该 namespace 上的所有 watch 收到 `canceled`(`NAMESPACE_NOT_FOUND`)。
- **中断恢复**:Reaper Leader 发现 `_system` 中有对象而 `state = terminating` 时,从第 2 步继续。
- **数据丢失**:`_system` 中有对象而 `state` 不存在,说明该分片丢了数据。server 记录 ERROR 日志并执行 `createNamespace`,新 epoch 让客户端重新 List。
- 删除后重建的 namespace revision 从 1 重新开始,epoch 也是新的随机值;客户端携带旧 `from_epoch` 时一律返回 `gone`,
  即使 `from_revision` 落在新 namespace 的范围之内。

### 7.3 Watch 语义

- **顺序**:同一 namespace 内按 revision 全序;同一 revision 的事件作为一个整体应用,过大时分成多条 `Events`,以 `more` 标记(过滤后为空则不发送)。
- **投递**:单条流内恰好一次;跨重连至少一次,客户端丢弃 `revision ≤ 已应用 revision` 的批次即可去重。
- **起点**:`from_revision = 0` 先发 `Snapshot` 再发增量,`Snapshot` 携带 epoch;`from_revision = r` 必须携带 `from_epoch`(否则 `INVALID_ARGUMENT`),发送 `revision > r` 的事件;
  `from_epoch` 与当前 epoch 不同或 `r` 早于窗口 → `gone`;`r` 大于当前 revision → 等副本追上,超时后 Store 有这个位置则以 `UNAVAILABLE` 结束流,否则 `gone`(D29)。
- **慢消费者**:跟不上的 watcher 只是错过中间视图,再从事件窗口补齐,不占额外内存,也不阻塞其他 watcher;掉出窗口时以 `gone` 结束该 watch,客户端重新 List。
- **进度书签**:空闲 watch 每 10 秒收到 `progress`。
- **权限**:创建时校验;`_system` 中的 Role / RoleBinding 变化后重新评估存量 watch,失去权限的收到 `canceled`(`PERMISSION_DENIED`)。
- **配额**:单条流最多 1,000 个 watch,超出时该 Create 收到 `canceled`(`WATCH_QUOTA_EXCEEDED`;REST 侧为 429),流本身不中断。

### 7.4 Server 内部结构

技术栈:Spring Boot 4 + WebFlux(协程)+ Spring gRPC(grpc-kotlin stub),运行在 JDK 25(D27)。两个端口:`8080` HTTP(REST、SSE、
Dashboard 静态资源、Actuator),`9090` gRPC。

```
me.ahoo.cosky.server
├─ resource/   ResourceService:校验 → 鉴权 → Store.apply → 等待 min_revision
├─ watch/      NamespaceCache、WatchCache(管理全部 namespace)、WatchSession(单个 watch)、AntiEntropy
├─ lease/      LeaseService、LeaseReaper(含回收保护)、LeaderElection
├─ auth/       TokenAuthenticator、OidcAuthenticator、Authorizer、Bootstrap
├─ view/       ServicesView、TopologyView、StatsView
├─ audit/      AuditLogger(结构化日志)
├─ adapter/
│  ├─ grpc/    gRPC 服务实现,仅协议映射
│  └─ rest/    REST / SSE 控制器,仅协议映射
└─ config/     Spring 配置与 @ConfigurationProperties
```

并发模型:
- 每个 `NamespaceCache` 由**一个协程**独占消费 `Store.changes`,状态以不可变持久化集合
  (`kotlinx.collections.immutable`)发布到 `AtomicReference`,读请求无锁、读到的总是某个完整 revision 的一致快照。
- 事件窗口是视图的一部分,与对象一起发布。
- 扇出:所有 watcher 收集同一个 `StateFlow<NamespaceView>`,每个 watcher 按自己的游标从窗口取事件。不需要每个 watcher 一个缓冲,慢消费者也无需特别判定(§7.3)。
- `min_revision` 等待:`NamespaceCache.revision` 是 `StateFlow<Long>`,`withTimeout { revision.first { it >= r } }`。
- `Authorizer` 根据 `_system` 缓存把每个主体的规则预编译为查找表,`_system` 变化时整体重建。
- **虚拟线程(D28)**:grpc-kotlin 服务和 REST handler 的协程都跑在虚拟线程 dispatcher 上,gRPC `ServerBuilder.executor` 和 `spring.threads.virtual.enabled=true` 同样使用虚拟线程。JWKS 拉取这类偶发阻塞因此不占用平台线程。
  扇出是纯计算的热路径,W2c 实测(§7.10)中位数没有差别,只有尾部比 `Dispatchers.Default` 高约一倍,且仍在目标之内,所以保留虚拟线程。

### 7.5 客户端细则

- **连接状态** `StateFlow<ConnectionState>`:`Connecting` → `Synced` ⇄ `Degraded`(断线,用本地视图服务)→ `Closed`。
- **快照定位(D26)**:用于同一磁盘上的进程重启。k8s 中只有挂了持久卷才有效;server 全挂且没有快照时,非 `optional` 的配置让新进程启动失败(fail fast),靠 server 多副本降低风险。
- **快照文件**:`<snapshotDir>/<serverKey>/<namespace>/<kind>[-<filter>].pb`,内容为当前视图(含 epoch 和 revision);变化后 1 秒防抖写入,先写临时文件再原子 rename。
  `serverKey` 是端点列表和 token ID 的哈希,不需要新增 RPC,还能隔离同一主机上不同身份的进程。`snapshotDir` 默认 `~/.cosky`,可关闭。
- **快照安全**:不加密,客户端没有地方安全存放密钥。目录权限 `0700`、文件权限 `0600`;`snapshot-include-config = false` 时 Config 不落盘。
- **启动**:有快照则立即以快照初始化 `StateFlow`(状态 `Degraded`),连接成功后从快照位置续传。
- **重连**:指数退避 100 ms → 10 s,带抖动,端点轮换;连接成功后立即发送 KeepAlive,不等下一周期。
- **注册**:客户端本地记住全部注册;租约丢失(KeepAlive 返回 0 或 `LEASE_NOT_FOUND`)时等待 0–5 秒随机抖动,再重新 Grant 并重新 Put 全部实例;`attached_count` 少于本地注册数时只重新 Put(D31)。
  KeepAlive 流因 `UNAVAILABLE` 等错误中断不算租约丢失。
- **分块**:收齐 `Snapshot` 与 `Events` 的全部分块后再更新 `StateFlow`,订阅者不会看到半个 revision。
- **低频重建**:每 `resync-interval`(默认 30 分钟,±20% 抖动)以 `from_revision = 0` 重建 Watch,用新快照替换视图,发现差异时记录指标。
- **关闭**:`close()` 在 3 秒内 Revoke 租约,超时则放弃,由 server 到期回收。
- **负载均衡**:`weight = 0` 的实例不参与选择;无可用实例时 `choose()` 返回 `null`。
- **虚拟线程(D28)**:快照写入等阻塞操作跑在虚拟线程上。客户端可能运行在 JDK 21–23,这些版本中 `synchronized` 内阻塞会占住载体线程(JEP 491 在 JDK 24 解决),所以客户端不在 `synchronized` 内阻塞;server 运行在 JDK 25,没有这个限制。

### 7.6 Server 配置项

| 属性 | 默认 | 说明 |
|---|---|---|
| `cosky.store.type` | `redis` | `redis` / `memory`(仅开发) |
| `cosky.api.max-message-bytes` | `2MiB` | 单条消息的资源总量(§4.6) |
| `cosky.store.redis.uri` | `redis://localhost:6379` | 支持 Sentinel / Cluster URI |
| `cosky.store.redis.tail-poll-interval` | `1s` | 没有提示时每个 namespace 的兜底读取间隔(§6.1) |
| `cosky.auth.bootstrap-token` | — | 首次启动创建 admin ServiceAccount |
| `cosky.auth.oidc.issuer` / `client-id` / `audience` / `groups-claim` | — / — / — / `groups` | 不配置则不启用 OIDC |
| `cosky.watch.window` | `10000` | 事件窗口,按事件计数,与 Stream `MAXLEN` 相同;应不小于 峰值事件速率 × 最长重连间隙(D29) |
| `cosky.view.topology-flush-interval` | `30s` | 拓扑边的写入间隔,保留 3 倍间隔(D30) |
| `cosky.watch.progress-interval` | `10s` | |
| `cosky.watch.max-per-stream` | `1000` | |
| `cosky.watch.anti-entropy-interval` | `5m` | server 反熵周期(§6.1) |
| `cosky.lease.default-ttl` / `min-ttl` / `max-ttl` | `30s` / `5s` / `300s` | |
| `cosky.lease.reaper-interval` | `1s` | |
| `cosky.lease.reaper-grace` | `30s` | 成为 Leader 或访问存储失败后的回收宽限(§6.2) |
| `cosky.lease.reaper-max-per-tick` | `100` | 每个 namespace 每轮最多回收的租约数 |
| `cosky.lease.protection-threshold` / `protection-min-leases` / `protection-max-duration` | `0.5` / `10` / `5m` | 大比例到期暂停(§6.2) |
| `cosky.config.history-size` | `20` | |
| `cosky.read.min-revision-timeout` | `3s` | |

### 7.7 Spring Boot Starter

配置加载改用 Spring Boot **ConfigData API**,不再依赖 Spring Cloud bootstrap 上下文:

```yaml
spring:
  application.name: order-service
  config.import: "optional:cosky:order-service.yaml,cosky:shared/datasource.yaml"
cosky:
  endpoints: cosky.internal:9090
  token: ${COSKY_TOKEN}
  namespace: prod
  snapshot:
    enabled: true          # 默认 true;目录默认 ~/.cosky
    include-config: true
  registry:
    enabled: true          # 默认 true;service = spring.application.name
    weight: 1
    metadata: { zone: az1 }
  discovery:
    enabled: true
    load-timeout: 5s       # 首次读取一个 service 时等待视图可用的上限
  config:
    refresh.enabled: true
    load-timeout: 5s       # 导入一个 Config 时等待视图可用的上限
```

| 集成点 | 实现 |
|---|---|
| 配置加载 | `ConfigDataLocationResolver` + `ConfigDataLoader`,前缀 `cosky:`;按 `ConfigSpec.format` 选择解析器:YAML 和 JSON 用 YAML 解析器,PROPERTIES 用 properties 解析器,TEXT 整体成为一个以配置名命名的属性。Config 不存在,或超过 `config.load-timeout` 仍无法确定时,没有 `optional:` 的导入使启动失败 |
| 配置刷新 | Config 变化时发布 Spring Cloud `RefreshEvent`(classpath 有 `spring-cloud-context` 时生效)。以加载时看到的 revision 为基准,所以加载后、开始监听前的变化不会漏;启动时不存在的 `optional:` Config 出现后同样触发。刷新失败(如内容无法解析)只记日志,旧值不变,下次变化继续刷新 |
| 服务发现 | `DiscoveryClient` 和 `ReactiveDiscoveryClient` 读本地视图;首次读取一个 service 时最多等待 `discovery.load-timeout`,避免把未知当成空。`getServices` 会 watch 整个 namespace 的 Instance,只在确实需要全量列表时才有这个开销 |
| 负载均衡 | 提供 Spring Cloud LoadBalancer 的 `ServiceInstanceListSupplier`,LB 算法交给 Spring Cloud |
| 注册 | `ServiceRegistry` + `AbstractAutoServiceRegistration`,Web 服务器就绪后注册,关闭时 Revoke;遵守 `spring.cloud.service-registry.auto-registration.enabled`;独立的管理端口不算服务实例,不注册 |
| 健康检查 | `HealthIndicator` 映射 `ConnectionState`:只有 `CLOSED` 为 DOWN,`DEGRADED` 仍为 UP(应用靠本地视图继续工作,重启无益);状态写入 `connection` 明细 |

刷新事件在专用的平台线程上发布,这是 D28 的例外:`ContextRefresher.refresh` 是 `synchronized` 的,导入又在其中阻塞,JDK 21–23 上放在虚拟线程会占住 client 自己也需要的载体线程。

### 7.8 鉴权细则

- **Token 格式**:`cosky_<tokenId>_<secret>`,`secret` 为 32 字节随机数的 base62。按 `tokenId` 查找,常量时间比较 `sha256(secret)`。
- **Token 管理**:`POST /v1/serviceaccounts/{name}/tokens` 创建并**只返回一次明文**;`DELETE …/tokens/{id}` 吊销。每个账号最多 2 个有效 token,用于轮换。
- **OIDC**:server 作为资源服务器,校验 Bearer JWT(JWKS 缓存,`iss` / `aud` / `exp`);Dashboard 在浏览器内完成授权码 + PKCE。
- **授权判定(D24)**:请求 `(subject, verb, kind, namespace, name)` 命中任一绑定角色的任一规则即放行。
  规则生效的 namespace 是 `Rule.namespaces` 与 `RoleBinding.namespaces` 的交集;全局对象的 namespace 为 `_system`。
- **`Rule.names`**:为空匹配全部;否则是精确值或以 `*` 结尾的前缀,比较 Config 名、Instance 的 `spec.service`、其他 kind 的对象名。
  Instance 按服务授权,否则账号可以冒充其他服务注册:Put 校验新对象的 `spec.service`,覆盖已有对象时还校验旧对象的;Delete 校验现有对象。
  List / Watch 整个 kind 时按规则过滤结果,不拒绝请求。
- **Views**:需要对应 namespace 的 `INSTANCE` `GET` 权限,且规则的 `names` 为空,因为视图是全量数据。
- **租约**:Lease 不是资源。Grant 需要该 namespace 的 `INSTANCE` `PUT` 权限,调用者记为所有者;KeepAlive、Revoke,以及把 Instance 挂到租约上都只允许所有者,否则 `PERMISSION_DENIED`。
  `ObjectMeta.lease` 对读者可见,所以安全性不依赖 id 保密。管理员清理实例用 Delete。server 在一条 KeepAlive 流内缓存校验结果,只有首次访问存储。

  按服务隔离的自定义 Role 示例(RoleBinding 再用 `namespaces = ["prod"]` 限定范围):

  ```yaml
  rules:
    - { kinds: [INSTANCE], namespaces: ["*"], verbs: [PUT, DELETE], names: [order] }
    - { kinds: [INSTANCE], namespaces: ["*"], verbs: [GET, WATCH] }
    - { kinds: [CONFIG], namespaces: ["*"], verbs: [GET, WATCH], names: [order-service.yaml, "shared/*"] }
  ```
- **TLS**:两个端口均支持 TLS,生产环境建议开启;mTLS 留作后续。
- **审计日志**:logger `cosky.audit`,JSON 一行一事件:`time, subject, verb, kind, namespace, name, revision, result, remote`。

### 7.9 可观测性

| 指标 | 类型 | 说明 |
|---|---|---|
| `cosky_store_apply_seconds` | Timer | 写入耗时 |
| `cosky_cache_revision{namespace}` | Gauge | 已应用 revision |
| `cosky_cache_lag_seconds{namespace}` | Gauge | 写入 → 进入缓存的延迟 |
| `cosky_watch_active` | Gauge | 活跃 watch 数 |
| `cosky_watch_events_sent_total` | Counter | |
| `cosky_watch_gone_total{reason}` | Counter | `compacted` / `slow` / `regression` / `epoch`;滚动重启后看这个指标判断窗口是否够大(D29) |
| `cosky_lease_active` / `cosky_lease_expired_total` | Gauge / Counter | |
| `cosky_lease_protection_active{namespace}` | Gauge | 大比例到期暂停中(§6.2) |
| `cosky_revision_regression_total` | Counter | §6.4 |
| `cosky_anti_entropy_drift_total{namespace}` | Counter | 反熵发现不一致(§6.1) |
| `cosky_auth_failures_total{reason}` | Counter | |

- 就绪探针:全部 namespace 完成初始加载后才就绪。
- 链路追踪:Micrometer Tracing(OpenTelemetry)覆盖 gRPC 与 HTTP。
- 客户端暴露连接状态、watch 延迟、快照年龄、低频重建发现差异的指标。

### 7.10 性能目标

| 指标 | 目标 |
|---|---|
| 单 namespace 规模 | 50,000 实例、10,000 Config |
| 单 server 副本 | 20,000 客户端连接、100,000 watch |
| 写入延迟 | p99 < 20 ms(同机房) |
| 变更传播 | 写入确认 → 客户端收到事件 p99 < 500 ms |
| 客户端热路径 | `choose()` / 读配置零分配、不挂起 |
| server 内存 | 约 1 KB / 实例(50,000 实例约 50 MB) |

W2c 粗测,JDK 25、10 核,单进程内,**不含网络和序列化**,事件样本只有 50 个:

| 项目 | 默认对象头 | 紧凑对象头 |
|---|---|---|
| 单实例内存 | 1,115 B | 1,012 B |
| 单 watch 内存(不含连接和 gRPC 流) | 847 B | 802 B |

| 把一个事件扇出到 100,000 个 watch | p50 | 最大值 | 建立这些 watch |
|---|---|---|---|
| 虚拟线程 | 111 ms | 298 ms | 0.8 s |
| `Dispatchers.Default` | 119 ms | 152 ms | 0.37 s |

每个 watch 都收到每个事件,是最坏情况。实例内存符合目标,紧凑对象头(JEP 519)再省约 9%,server 镜像应开启。20,000 连接、网络传播和写入延迟的目标还没有验证。

### 7.11 测试策略

- `conformance-test` 提供抽象测试类,内存 Store 与 Redis Store 必须通过同一套用例;协议层用例通过 client 驱动 server。
  各实现提供钩子 `loseWritesAfter(namespace, revision)` 模拟丢写,让分叉检测进入同一套用例。
- Redis 测试改用 **Testcontainers**,不再要求本地运行 Redis;Cluster 下必须覆盖多 namespace 跨 slot 追尾。
- 端到端故障注入(Toxiproxy):Redis 重启、server 实例被杀、网络分区、主从切换丢写。必须通过:
  - Redis 或全部 server 不可用超过 TTL 后恢复、客户端与 server 分区超过 TTL:存活实例不被删除,不产生 DELETE 事件。
  - 丢写后 revision 涨回原值、namespace 删除重建:客户端收到 `gone` 并收敛到新视图。
- 性能:JMH 覆盖客户端热路径与 server 扇出;压测工具在 W4 交付。
- 覆盖率门槛保持 90% 行 / 70% 分支。

---

## 8. 现有组件去向

| 现有 | 去向 |
|---|---|
| 18 个业务 Lua 脚本 + 2 个 lib、用户/RBAC/审计 6 个脚本 | `store-redis` 中少量通用脚本(§5.2) |
| `DiscoveryKeyGenerator` / `ConfigKeyGenerator` + Lua 同步测试 | 删除;键规则仅在 `store-redis` 定义一次 |
| Redis Pub/Sub + `RedisEventListenerContainer` 系列 | Redis Stream + server Watch Cache;Pub/Sub 只保留为追尾的唤醒提示 |
| `ReactiveWatchCache`(Reactor Sinks + resync + 回读) | 删除;client 以 `StateFlow` + 按 revision 应用事件实现,resync 由低频反熵替代(§6.1、§7.5) |
| `RenewInstanceService` + `registry_reap.lua` | client 租约保活 + server Reaper |
| `ConsistencyRedisServiceDiscovery` 的过期过滤 | 删除,过期以 DELETE 事件表达 |
| `RedisServiceStatistic` + `StatServiceScheduler` + 互斥锁 | Views(Watch Cache 内存计算) |
| `TopologyRecordingServiceDiscovery` / `RedisServiceTopology` | server 基于 Watch 订阅派生 |
| `UserService` / `RbacService` / `AuditLogService` / `CoSkyPolicy` | ServiceAccount / Role / RoleBinding 资源 + OIDC + revision 审计 |
| `ServiceInstance` 接口 + 委托 + `withXxx` | proto 生成的 `Resource` / `InstanceSpec` |
| 3 种加权随机 LB | 加权随机 + 轮询 |
| `setService` / 空服务 | 删除,服务是实例的派生视图 |

---

## 9. 关键权衡

| 取舍 | 代价 | 缓解 |
|---|---|---|
| 引入服务端,多一跳 | 写延迟多一个 RTT,部署多一个组件 | 读走本地快照;server 无状态可水平扩容;提供单镜像 + Helm |
| server 成为关键路径 | server 全挂时不能注册 / 改配置;没有持久快照的新进程启动失败 | 运行中进程继续用内存视图;快照目录持久化时新进程可用旧视图(§7.5);server 多副本;恢复时由回收保护避免清空实例(§6.2) |
| 回收保护 | 真实的大面积崩溃时,死实例最多多保留 5 分钟 | 调用方重试与熔断;指标告警;阈值可配置 |
| Pub/Sub → Stream | Stream 占内存 | `MAXLEN ~ 10000` + `gone` 重新 List |
| 提示 + 兜底轮询 | Redis 负载随 副本数 × namespace 数 增长 | 正常靠提示驱动;轮询是非阻塞小脚本,间隔可调 |
| namespace 绑定单分片 | 单 namespace 吞吐受限于单 Redis 分片 | 显式约束;读压力由 Watch Cache 吸收 |
| Redis 持久性 | 主从切换可能丢约 1 秒写入 | AOF + epoch 与分叉检测 + 低频反熵;未来可加 PostgreSQL Store |
| 去掉内置用户密码 | 无 OIDC 的小团队要用 token 登录 Dashboard | bootstrap token + ServiceAccount 足够覆盖 |

---

## 10. 实施计划

里程碑、任务波次、验收标准、进度与待讨论问题见 [roadmap.md](roadmap.md)。
