# CoSky 目标架构重设计(不考虑兼容性)

> 基于 `main@bc6e873`(v6.2.0)代码审查。目标是回答:**如果今天从零开始做 CoSky,它应该长什么样?**
>
> 状态:方案已确认(见 §1 决策记录),详细设计见 §7;实施计划与进度见 [roadmap.md](roadmap.md)。

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
| D2 | 存储 | **Store SPI;只实现 Redis(+ 测试用内存实现),不做内嵌 Raft** | "直接用已有 Redis"是 CoSky 唯一差异点;Redis 持久性风险以文档 + revision 回退检测缓解(§6.4),PostgreSQL 实现作为未来选项 |
| D3 | 协议 | **gRPC 数据面 + REST/JSON 管理面,同一份 `.proto`** | 租约保活与 Watch 天然是长连接双向流;REST 由 proto 映射,保证只有一套契约 |
| D4 | 分片 | **namespace = 分片单位(一个 Redis slot),写为显式约束** | 控制面数据小、写少;读与扇出由 server Watch Cache 承担,Redis 负载与 server 副本数成正比而非业务进程数 |
| D5 | 身份 | **内置最小集(bootstrap admin + ServiceAccount + Role/RoleBinding),人员走 OIDC** | 删除自建用户/密码/锁定/改密;权限对象本身是资源,复用存储、Watch、审计 |
| D6 | 直连 Redis 模式 | **不保留** | 双模式会让两套语义长期共存 |
| D7 | 不做的事 | 自研一致性协议、多数据中心同步、服务端托管业务规则 | 控制面只存数据、推送变更 |
| D8 | 模块目录 | **去掉 `cosky-` 前缀**(`api/`、`server/` …);发布的 Maven artifactId 仍为 `cosky-*` | 目录简洁;Maven 坐标需全局可辨识,由 Gradle `archivesName` 统一加前缀 |
| D9 | 异步模型 | **Kotlin 协程优先**(`suspend` / `Flow` / `StateFlow`);Reactor 只出现在适配层 | 协程是 Kotlin 原生模型,代码顺序可读、取消语义结构化;grpc-kotlin 原生协程;Reactor 仅用于对接只提供 Reactor API 的外部框架(§3.2) |
| D10 | Spring 配置加载 | **Spring Boot ConfigData API**(`spring.config.import=cosky:…`),不再使用 bootstrap 上下文 | Spring Boot 推荐方式,无需 Spring Cloud bootstrap(§7.7) |
| D11 | Dashboard 部署 | **server 托管静态资源(单镜像)**,同时支持独立部署 | 部署最简;Dashboard 只依赖公开 REST API |
| D12 | 审计 | **结构化审计日志**,server 不存储审计数据 | 交给运维日志系统;变更追溯由 `updated_by` + Config 历史提供(§7.8) |
| D13 | 删除 namespace | **只允许删除空 namespace** | 防止误删整片数据(§7.2) |
| D14 | Java 门面 | **推迟到 7.1** | Spring 用户经 starter 使用,7.0 聚焦核心 |
| D15 | server gRPC 接入 | **Spring gRPC** | 与 Spring Boot 生命周期、配置、可观测性统一(§7.4) |
| D16 | 性能目标 | **采纳 §7.10** | 作为 W4a 验收标准 |
| D17 | proto 命名 | **遵循 buf STANDARD**(枚举值前缀、服务 `Service` 后缀),仅豁免 `Get` 直接返回资源 | proto 枚举值在包内共享命名空间,无前缀易冲突;Get 返回资源符合 AIP-131(§4.2) |
| D18 | OpenAPI | **混合生成**:schemas 由 proto 生成,paths 手写,构建时合并 | REST 路由无法由 RPC 自动推导,但 schema 必须与 proto 同步(§4.5) |

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

`settings.gradle.kts` 中统一处理 artifactId:

```kotlin
subprojects { base { archivesName.set("cosky-${project.name}") } }
```

### 3.2 异步模型与适配层边界

**核心层**(`api`、`store`、`client`、`server` 的领域与服务代码)只使用协程:单值 `suspend fun`,
流式 `Flow`,可观察状态 `StateFlow`。**禁止依赖 `reactor-core`。**

| 适配点 | 位置 | 方式 |
|---|---|---|
| Redis 驱动 | `store-redis` | 直接使用 Lettuce;Lettuce 的 Reactive API 仅在本模块内部,通过 `kotlinx-coroutines-reactive`(`awaitSingle` / `asFlow`)桥接,对外只暴露 `Store` 的协程接口 |
| gRPC | `server`、`client` | grpc-kotlin 生成协程 stub,天然无 Reactor |
| REST / SSE | `server` | Spring WebFlux 的协程支持(`suspend` handler、返回 `Flow` 的 SSE),业务代码不出现 `Mono` / `Flux` |
| Spring Cloud 发现 | `spring-boot-starter` | `ReactiveDiscoveryClient` 要求 `Flux`:在适配类中用 `flow.asFlux()` 转换 |
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
}
message RoleSpec { string description = 1; repeated Rule rules = 2; }

message Subject {
  enum Type { TYPE_UNSPECIFIED = 0; TYPE_SERVICE_ACCOUNT = 1; TYPE_USER = 2; TYPE_GROUP = 3; }
  Type type = 1;
  string name = 2;                            // USER/GROUP 来自 OIDC claim
}
message RoleBindingSpec { string role = 1; repeated Subject subjects = 2; }
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
}
message ListResponse { repeated Resource items = 1; int64 revision = 2; }

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

message HistoryRequest  { ResourceKey key = 1; int32 limit = 2; }   // 0 或超过 history-size 时按 history-size 截断
message HistoryResponse { repeated Resource revisions = 1; }       // 新 → 旧

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
  }
  message Cancel { int64 watch_id = 1; }
}

message WatchResponse {
  int64 watch_id = 1;
  oneof payload {
    Snapshot snapshot = 2;              // from_revision = 0 时的初始全量
    Events events = 3;                  // 同一 revision 的事件成批到达
    int64 progress = 4;                 // 进度书签:当前已追到的 revision,空闲时周期发送
    int64 gone = 5;                     // from_revision 已超出窗口;值为最早可续传 revision,客户端需重新 List
  }
  message Snapshot { repeated Resource items = 1; int64 revision = 2; }
  message Events { int64 revision = 1; repeated Event events = 2; }
  message Event {
    enum Type { TYPE_UNSPECIFIED = 0; TYPE_PUT = 1; TYPE_DELETE = 2; }
    Type type = 1;
    Resource resource = 2;              // DELETE 时为删除前的最后状态
  }
}

service LeaseService {
  rpc Grant(GrantRequest) returns (GrantResponse);
  rpc KeepAlive(stream KeepAliveRequest) returns (stream KeepAliveResponse);
  rpc Revoke(RevokeRequest) returns (RevokeResponse);
}

message GrantRequest  { string namespace = 1; int32 ttl_seconds = 2; }   // 默认 30,范围 [5, 300]
message GrantResponse { int64 lease = 1; int32 ttl_seconds = 2; }
message KeepAliveRequest  { string namespace = 1; int64 lease = 2; }
message KeepAliveResponse { int64 lease = 1; int32 ttl_seconds = 2; }    // ttl_seconds = 0 表示租约已不存在
message RevokeRequest  { string namespace = 1; int64 lease = 2; }
message RevokeResponse {}

service ViewService {                                                 // 派生只读视图,不落库
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
| GET | `/v1/namespaces/{ns}/{kind}` | List(`?labelSelector=&service=&minRevision=`) |
| GET | `/v1/namespaces/{ns}/{kind}/{name}` | Get |
| PUT | `/v1/namespaces/{ns}/{kind}/{name}` | Put;`If-Match: <revision>`、`If-None-Match: *` |
| DELETE | `/v1/namespaces/{ns}/{kind}/{name}` | Delete;`If-Match` |
| GET | `/v1/namespaces/{ns}/configs/{name}/history` | History |
| POST | `/v1/namespaces/{ns}/configs/{name}:rollback` | `{ "toRevision": n }`,server 读历史后 Put |
| GET | `/v1/namespaces/{ns}/watch?kind=&fromRevision=` | Watch,SSE,供 Dashboard 实时刷新 |
| GET | `/v1/namespaces/{ns}/views/{services\|topology\|stats}` | Views |
| POST / GET | `/v1/namespaces/{ns}/configs:import` / `:export` | zip 批量导入导出(保留现有能力) |
| GET | `/v1/me` | 当前身份与有效权限 |

`{kind}` 取 `configs` / `instances`;全局对象走 `/v1/{namespaces|serviceaccounts|roles|rolebindings}/{name}`。
REST 与 gRPC 由同一 server 进程提供,JSON 编码使用 proto3 JSON 映射。
路径中的 `{name}` 是单个路径段:Config 名中的 `/` 必须编码为 `%2F`(WebFlux 的 `PathPattern` 按段匹配后再解码)。

**OpenAPI**(`api/` 构建产物 `cosky.yaml`)采用混合生成:`components.schemas` 由 proto 描述符按 proto3 JSON 映射自动生成,
保证与 proto 同步;`paths` 在 `api/src/main/openapi/paths.yaml` 中手写(本节路由无法从 RPC 自动推导:
`{kind}` 复数段、`If-Match` 头、SSE、`:rollback`、导入导出、token 端点);构建时合并并校验引用完整。

---

## 5. 存储设计

### 5.0 Store SPI(`store/`,纯协程)

```kotlin
interface Store {
    /** 原子批写;全部未变化时返回当前 revision 且 changed = 0。 */
    suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult

    suspend fun currentRevision(namespace: String): Long
    fun readAll(namespace: String): Flow<StoredObject>                       // 全量加载
    fun changes(namespace: String, fromRevision: Long): Flow<ChangeBatch>    // 有序、可续传;超出窗口抛 RevisionGoneException
    suspend fun history(key: ResourceKey, limit: Int): List<StoredObject>

    suspend fun grantLease(namespace: String, ttl: Duration): Lease
    suspend fun keepAlive(namespace: String, leases: Collection<Long>): Map<Long, Duration?>   // 批量;null = 已不存在
    suspend fun expireLease(namespace: String, lease: Long, force: Boolean): Boolean          // force = Revoke
    suspend fun expiredLeases(namespace: String, limit: Int): List<Long>

    /** 删除 namespace 的全部数据(rev、log、对象、历史、租约);调用方保证其已为空。 */
    suspend fun dropNamespace(namespace: String)

    /** 带过期的互斥锁,用于 Reaper 选主;owner 相同时为续约。 */
    suspend fun tryLock(name: String, owner: String, ttl: Duration): Boolean
    suspend fun unlock(name: String, owner: String)
}

sealed interface ApplyResult {
    data class Applied(val revision: Long, val changed: Int) : ApplyResult
    data class Conflict(val index: Int, val currentRevision: Long?) : ApplyResult
    data class LeaseNotFound(val index: Int, val lease: Long) : ApplyResult
}
```

用 sealed 结果表达业务失败,异常只用于基础设施故障;`changes` 是冷 `Flow`,取消收集即释放 Redis 连接。

### 5.1 Redis 键布局(`store-redis/`)

所有键形如 `cosky:{<ns>}:…`,`{<ns>}` 是哈希标签,一个 namespace 的全部键落在同一 slot。全局对象使用 `ns = _system`。

| 键 | 类型 | 内容 |
|---|---|---|
| `cosky:{ns}:rev` | String | namespace revision,INCR 递增 |
| `cosky:{ns}:obj:<kind>` | Hash | name → 资源 protobuf 字节(meta 中只读字段除外) |
| `cosky:{ns}:ver:<kind>` | Hash | name → `<revision>:<create_revision>:<updated_at_ms>` |
| `cosky:{ns}:log` | Stream | 变更日志;ID = `<revision>-<seq>`;字段 `t`(P/D)、`k`(kind)、`n`(name)、`o`(对象字节);`MAXLEN ~ 10000` |
| `cosky:{ns}:hist:config:<name>` | Sorted Set | score = revision,member = 对象字节;保留最近 20 个 |
| `cosky:{ns}:lease` | Sorted Set | lease id → 到期时间(ms,Redis `TIME`) |
| `cosky:{ns}:lease:<id>` | Set | 挂在该租约上的 Instance name |
| `cosky:{ns}:lease:seq` | String | 租约 id 生成器 |
| `cosky:leader:reaper` | String | 回收 Leader 锁,`SET NX PX` |

设计要点:
- **revision 与对象分开存**:Lua 无法改写 protobuf,所以把只读元数据(revision、时间)放进 `ver:<kind>`,
  server 读取时把两者组装成完整 `Resource`。CAS 只需比较 `ver:<kind>` 中的 revision。
- **Stream ID 即 revision**:`XRANGE log <rev+1>-0 +` 就是"从 revision 续传";一次原子批写共享同一 revision,用 `seq` 区分。
- 时钟统一使用 Redis `TIME`,server 副本之间不依赖本机时钟。

### 5.2 脚本(共 4 个,所有键都在 `KEYS` 中显式声明)

| 脚本 | 作用 |
|---|---|
| `apply.lua` | 通用原子批写:逐项校验 CAS 与租约 → 内容不变的项跳过 → 若有变化则 `INCR rev` → `HSET/HDEL obj/ver` → 维护租约挂载集合 → Config 写历史 → `XADD log`(每项一条,`MAXLEN ~`)→ 返回新 revision 或冲突明细 |
| `lease_grant.lua` | 生成 id,`ZADD lease <TIME+ttl> id` |
| `lease_keepalive.lua` | `ZADD lease XX CH`;不存在返回 0;**不改 revision、不写日志** |
| `lease_expire.lua` | 对单个租约:确认 `score ≤ TIME` → 取挂载集合 → 等价于一次 apply 删除这些 Instance → `DEL lease:<id>`、`ZREM`。Revoke 复用此脚本(跳过到期检查) |

`apply.lua` 伪代码:

```lua
-- KEYS: rev, log, [obj, ver, hist?, leaseZset?, leaseSet?, oldLeaseSet?] per op
-- ARGV: now_ms 由脚本内 TIME 取得; actor, n_ops, 然后每个 op: type, kind, name, expected_rev, bytes, lease
local changed = {}
for each op do
  local cur = HGET ver name                         -- "<rev>:<create_rev>:<ts>" 或 nil
  check expected_rev against cur                    -- 失败 → return {"CONFLICT", index, cur_rev}
  if op.lease ~= 0 and not ZSCORE(leaseZset, lease) > now then return {"LEASE_NOT_FOUND", index} end
  if op.type == PUT and HGET(obj, name) == op.bytes then skip end     -- 无变化
  table.insert(changed, op)
end
if #changed == 0 then return {"OK", GET rev, 0} end
local rev = INCR rev
for i, op in ipairs(changed) do
  if PUT:    HSET obj name bytes; HSET ver name rev..":"..(create_rev or rev)..":"..now
             维护 lease 集合 (SREM 旧 / SADD 新); Config 时 ZADD hist rev bytes + ZREMRANGEBYRANK 截断
  if DELETE: old = HGET obj name; HDEL obj/ver name; SREM lease 集合
  XADD log MAXLEN ~ 10000 (rev.."-"..(i-1)) t P|D k kind n name o (bytes|old)
end
return {"OK", rev, #changed}
```

### 5.3 持久性与 Redis 部署建议

- 必须开启 AOF(`appendfsync everysec`);主从切换存在丢失最后约 1 秒写入的风险,在文档中明示。
- 支持 Standalone / Sentinel / Cluster;Cluster 下按 namespace 自然分布到各分片。
- server 检测 revision 回退(§6.4),发现后强制所有 watcher 重新 List,避免客户端停留在"未来"的视图上。

---

## 6. 核心流程

### 6.1 Watch Cache(server 内)

```
启动 / 新 namespace:
  R0 = GET rev
  HGETALL obj:* 与 ver:*                  -- 读到的对象可能比 R0 新
  XRANGE log (R0+1)-0 +                   -- 回放,按 ver 中 revision 比较做幂等应用
  进入追尾:XREAD BLOCK 多个 namespace 的 log(每副本一条连接)

内存结构(每 namespace):
  objects: kind → name → Resource
  window:  最近 N 个 revision 的事件环形缓冲(N 与 Stream MAXLEN 对齐)
  revision: 已应用的最大 revision
```

- 所有 Get/List/Watch 由 Watch Cache 服务,只有写操作打到 Redis。
- `min_revision`:缓存 revision 未达到时挂起等待(默认最多 3 秒),实现跨副本的"读己之写"。
- 客户端 `from_revision` 早于窗口 → 返回 `gone`,客户端重新 List 后从新 revision Watch。
- 空闲 watcher 每 10 秒收到一次 `progress`,保证其记录的 revision 持续前进,重连时不易落出窗口。

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
- 一个进程在一个 namespace 只持有一个租约,心跳成本 O(进程数),与实例数无关。
- 持久实例 = 不挂租约的 Instance,无特殊分支。
- Reaper 由 `cosky:leader:reaper` 锁选主(PX 15s,每 5s 续约);Leader 失效最多延迟一个锁周期,只影响回收时效,不影响正确性。

### 6.3 认证与授权

- **ServiceAccount**:token 为 32 字节随机数,格式 `cosky_<id>_<secret>`;server 只存 SHA-256,
  校验时查 Watch Cache(`_system` namespace),不访问 Redis。gRPC metadata / HTTP 头均为 `Authorization: Bearer`。
- **人员**:配置 OIDC 时,Dashboard 走授权码 + PKCE,server 用 JWKS 校验 ID Token,
  `sub` → `USER`、`groups` claim → `GROUP`。未配置 OIDC 时,Dashboard 使用 ServiceAccount token 登录。
- **Bootstrap**:首次启动若 `_system` 中无任何 RoleBinding,读取环境变量 `COSKY_BOOTSTRAP_TOKEN`
  创建 `admin` ServiceAccount 并绑定内置 `admin` 角色。
- **内置角色**:`admin`(全部)、`viewer`(GET/WATCH 全部)、`client`(对绑定 namespace:Config GET/WATCH,Instance 全部)。
- **审计**:每个 revision 的 `updated_by` + Config 历史即变更追溯;每次写入和认证事件另输出一行结构化审计日志(§7.8),
  由运维的日志系统收集,server 不存储审计数据。
- 拓扑视图:server 记录"已认证主体 × Watch 的服务",即 consumer → producer 边,无需客户端额外写入。

### 6.4 revision 回退检测

追尾时若读到的 `rev` 小于 Watch Cache 已应用的 revision(Redis 主从切换丢写),server:

1. 将该 namespace 标记为 `diverged`,暂停服务读请求(返回 `UNAVAILABLE`);
2. 重新执行启动流程加载全量;
3. 向该 namespace 所有 watcher 发送 `gone`,客户端重新 List;
4. 记录 ERROR 日志与指标 `cosky_revision_regression_total`。

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

- 同一 key 的多个 `watch` 调用共享一个底层 Watch(`stateIn` + 引用计数),最后一个订阅者离开后延迟取消。
- 热路径(`choose()`、`config.value`)只读 `StateFlow.value`,不挂起、不分配。
- 一条 gRPC 连接承载全部 Watch 与 KeepAlive;多个 server 端点轮换重连,重连带 `from_revision`。
- 快照落盘:`~/.cosky/<ns>/snapshot.pb`(含 revision);启动时先加载快照立即可用,连接成功后从快照 revision 续传。
- 负载均衡:一个 `LoadBalancer` SPI,内置加权随机(前缀和 + 二分)与轮询。

---

## 7. 详细设计

### 7.1 资源语义

- **Put 是整体替换**,不支持局部 patch。请求中的只读字段(`revision`、`create_revision`、时间、`updated_by`、`sha256`)被忽略,由 server 填写。
- **创建 / 更新**由 `expected_revision` 区分:`-1` 仅创建,`>0` CAS 更新,`0` 不校验(upsert)。
- **无变化写入**:内容(对象字节)与当前一致时返回 `changed = false`,不推进 revision,不产生事件。
- **删除后重建**:`create_revision` 重新开始;Config 历史在删除后**保留**,可通过 rollback 从历史恢复(恢复即重建)。
- **Instance 与租约**:租约必须属于同一 namespace,且在写入时有效;允许把实例从一个租约迁到另一个,或改为不挂租约。
- **labels 选择器**:仅支持等值匹配,多个条件为 AND。

| 限制 | 默认值 |
|---|---|
| Config 内容 | ≤ 1 MiB |
| Instance metadata | ≤ 64 项;key ≤ 63 字符,value ≤ 1024 字符 |
| labels | ≤ 32 项;key ≤ 63,value ≤ 253;key 字符集见 §4.2 |
| description | ≤ 1024 字符 |
| History `limit` | 0 或超过 `history-size` 时按 `history-size` 截断,负数非法 |
| 一次批写(apply)的操作数 | ≤ 100 |
| 单 namespace 对象数 | 软上限 100,000(超过告警,不拒绝) |
| Config 历史 | 每个 Config 最近 20 个 revision |

### 7.2 Namespace 生命周期

- `_system` 在 server 首次启动时自动创建;`default` namespace 在 bootstrap 时创建。
- 向不存在的 namespace 写入返回 `NOT_FOUND`(detail `NAMESPACE_NOT_FOUND`)。
- **只允许删除空 namespace**(无 Config、无 Instance),否则返回 `FAILED_PRECONDITION` / HTTP 409(`NAMESPACE_NOT_EMPTY`),防止误删整片数据。
  删除成功后 server 调用 `Store.dropNamespace` 清理全部键,该 namespace 上的所有 watcher 以 `NOT_FOUND` 结束。
- 删除后重建的 namespace revision 从 1 重新开始;客户端携带的 `from_revision` **大于** server 当前 revision 时一律返回 `gone`
  (同一规则也覆盖 Redis 丢写导致的 revision 回退)。

### 7.3 Watch 语义

- **顺序**:同一 namespace 内按 revision 全序;同一 revision 的多条事件在一个 `Events` 消息中原子送达(过滤后为空则不发送)。
- **投递**:单条流内恰好一次;跨重连至少一次,客户端丢弃 `revision ≤ 已应用 revision` 的批次即可去重。
- **起点**:`from_revision = 0` 先发 `Snapshot` 再发增量;`from_revision = r` 发送 `revision > r` 的事件;
  `r` 早于窗口或大于当前 revision → `gone`。
- **慢消费者**:每个 watcher 一个有界缓冲(默认 1,000 批),溢出时以 `gone` 结束该 watch,客户端重新 List,
  不阻塞其他 watcher,也不让 server 内存无界增长。
- **进度书签**:空闲 watch 每 10 秒收到 `progress`。
- **权限**:创建时校验;`_system` 中的 Role / RoleBinding 变化后重新评估存量 watch,失去权限的以 `PERMISSION_DENIED` 结束。
- **配额**:单条流最多 1,000 个 watch,超出时该 Create 以 `RESOURCE_EXHAUSTED`(`WATCH_QUOTA_EXCEEDED`)拒绝,流本身不中断。

### 7.4 Server 内部结构

技术栈:Spring Boot 4 + WebFlux(协程)+ Spring gRPC(grpc-kotlin stub)。两个端口:`8080` HTTP(REST、SSE、
Dashboard 静态资源、Actuator),`9090` gRPC。

```
me.ahoo.cosky.server
├─ resource/   ResourceService:校验 → 鉴权 → Store.apply → 等待 min_revision
├─ watch/      NamespaceCache、WatchCache(管理全部 namespace)、WatchHub(扇出)
├─ lease/      LeaseService、LeaseReaper、LeaderElection
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
- 事件窗口是固定大小的环形数组,只由该协程写入。
- 扇出:每个 watcher 一个 `Channel(capacity)`,生产者用 `trySend`,失败即判定为慢消费者(§7.3)。
- `min_revision` 等待:`NamespaceCache.revision` 是 `StateFlow<Long>`,`withTimeout { revision.first { it >= r } }`。
- `Authorizer` 根据 `_system` 缓存把每个主体的规则预编译为查找表,`_system` 变化时整体重建。

### 7.5 客户端细则

- **连接状态** `StateFlow<ConnectionState>`:`Connecting` → `Synced` ⇄ `Degraded`(断线,用本地视图服务)→ `Closed`。
- **快照文件**:`<snapshotDir>/<serverId>/<namespace>/<kind>[-<filter>].pb`,内容为 `ListResponse`(含 revision);
  变化后 1 秒防抖写入,先写临时文件再原子 rename。`snapshotDir` 默认 `~/.cosky`,可关闭。
- **启动**:有快照则立即以快照初始化 `StateFlow`(状态 `Degraded`),连接成功后从快照 revision 续传。
- **重连**:指数退避 100 ms → 10 s,带抖动,端点轮换。
- **注册**:客户端本地记住全部注册;租约丢失(KeepAlive 返回 0 或 `LEASE_NOT_FOUND`)时重新 Grant 并重新 Put 全部实例。
- **关闭**:`close()` 在 3 秒内 Revoke 租约,超时则放弃,由 server 到期回收。
- **负载均衡**:`weight = 0` 的实例不参与选择;无可用实例时 `choose()` 返回 `null`。

### 7.6 Server 配置项

| 属性 | 默认 | 说明 |
|---|---|---|
| `cosky.store.type` | `redis` | `redis` / `memory`(仅开发) |
| `cosky.store.redis.uri` | `redis://localhost:6379` | 支持 Sentinel / Cluster URI |
| `cosky.auth.bootstrap-token` | — | 首次启动创建 admin ServiceAccount |
| `cosky.auth.oidc.issuer` / `client-id` / `audience` / `groups-claim` | — / — / — / `groups` | 不配置则不启用 OIDC |
| `cosky.watch.window` | `10000` | 事件窗口,与 Stream `MAXLEN` 一致 |
| `cosky.watch.buffer` | `1000` | 单 watcher 缓冲 |
| `cosky.watch.progress-interval` | `10s` | |
| `cosky.watch.max-per-stream` | `1000` | |
| `cosky.lease.default-ttl` / `min-ttl` / `max-ttl` | `30s` / `5s` / `300s` | |
| `cosky.lease.reaper-interval` | `1s` | |
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
  registry:
    enabled: true          # 默认 true;service = spring.application.name
    weight: 1
    metadata: { zone: az1 }
  discovery.enabled: true
  config.refresh.enabled: true
```

| 集成点 | 实现 |
|---|---|
| 配置加载 | `ConfigDataLocationResolver` + `ConfigDataLoader`,前缀 `cosky:`;按 `ConfigSpec.format` 选择解析器 |
| 配置刷新 | Config 变化时发布 Spring Cloud `RefreshEvent`(classpath 有 `spring-cloud-context` 时生效) |
| 服务发现 | `DiscoveryClient` 读 `StateFlow.value`;`ReactiveDiscoveryClient` 用 `asFlux()` |
| 负载均衡 | 提供 Spring Cloud LoadBalancer 的 `ServiceInstanceListSupplier`,LB 算法交给 Spring Cloud |
| 注册 | `ServiceRegistry` + `AbstractAutoServiceRegistration`,Web 服务器就绪后注册,关闭时 Revoke |
| 健康检查 | `HealthIndicator` 映射 `ConnectionState` |

### 7.8 鉴权细则

- **Token 格式**:`cosky_<tokenId>_<secret>`,`secret` 为 32 字节随机数的 base62。按 `tokenId` 查找,常量时间比较 `sha256(secret)`。
- **Token 管理**:`POST /v1/serviceaccounts/{name}/tokens` 创建并**只返回一次明文**;`DELETE …/tokens/{id}` 吊销。每个账号最多 2 个有效 token,用于轮换。
- **OIDC**:server 作为资源服务器,校验 Bearer JWT(JWKS 缓存,`iss` / `aud` / `exp`);Dashboard 在浏览器内完成授权码 + PKCE。
- **授权判定**:请求 `(subject, verb, kind, namespace)` 命中任一绑定角色的任一规则即放行;全局对象的 namespace 为 `_system`;
  Views 需要对应 namespace 的 `INSTANCE` `GET` 权限。
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
| `cosky_watch_gone_total{reason}` | Counter | `compacted` / `slow` / `regression` |
| `cosky_lease_active` / `cosky_lease_expired_total` | Gauge / Counter | |
| `cosky_revision_regression_total` | Counter | §6.4 |
| `cosky_auth_failures_total{reason}` | Counter | |

- 就绪探针:全部 namespace 完成初始加载后才就绪。
- 链路追踪:Micrometer Tracing(OpenTelemetry)覆盖 gRPC 与 HTTP。
- 客户端暴露连接状态、watch 延迟、快照年龄指标。

### 7.10 性能目标

| 指标 | 目标 |
|---|---|
| 单 namespace 规模 | 50,000 实例、10,000 Config |
| 单 server 副本 | 20,000 客户端连接、100,000 watch |
| 写入延迟 | p99 < 20 ms(同机房) |
| 变更传播 | 写入确认 → 客户端收到事件 p99 < 500 ms |
| 客户端热路径 | `choose()` / 读配置零分配、不挂起 |
| server 内存 | 约 1 KB / 实例(50,000 实例约 50 MB) |

### 7.11 测试策略

- `conformance-test` 提供抽象测试类,内存 Store 与 Redis Store 必须通过同一套用例;协议层用例通过 client 驱动 server。
- Redis 测试改用 **Testcontainers**,不再要求本地运行 Redis。
- 端到端故障注入(Toxiproxy):Redis 重启、server 实例被杀、网络分区、主从切换丢写。
- 性能:JMH 覆盖客户端热路径与 server 扇出;压测工具在 W4 交付。
- 覆盖率门槛保持 90% 行 / 70% 分支。

---

## 8. 现有组件去向

| 现有 | 去向 |
|---|---|
| 18 个业务 Lua 脚本 + 2 个 lib、用户/RBAC/审计 6 个脚本 | `store-redis` 中 4 个通用脚本 |
| `DiscoveryKeyGenerator` / `ConfigKeyGenerator` + Lua 同步测试 | 删除;键规则仅在 `store-redis` 定义一次 |
| Redis Pub/Sub + `RedisEventListenerContainer` 系列 | Redis Stream + server Watch Cache |
| `ReactiveWatchCache`(Reactor Sinks + resync + 回读) | 删除;client 以 `StateFlow` + 按 revision 应用事件实现 |
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
| server 成为关键路径 | server 全挂时不能注册 / 改配置 | 快照落盘,运行中进程与新启动进程仍可用旧视图 |
| Pub/Sub → Stream | Stream 占内存 | `MAXLEN ~ 10000` + `gone` 重新 List |
| namespace 绑定单分片 | 单 namespace 吞吐受限于单 Redis 分片 | 显式约束;读压力由 Watch Cache 吸收 |
| Redis 持久性 | 主从切换可能丢约 1 秒写入 | AOF + revision 回退检测;未来可加 PostgreSQL Store |
| 去掉内置用户密码 | 无 OIDC 的小团队要用 token 登录 Dashboard | bootstrap token + ServiceAccount 足够覆盖 |

---

## 10. 实施计划

里程碑、任务波次、验收标准、进度与待讨论问题见 [roadmap.md](roadmap.md)。
