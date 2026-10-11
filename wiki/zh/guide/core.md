---
title: "Core Module"
---

# 核心模块

`cosky-core` 模块是整个 CoSky 平台的基础。所有其他模块——配置、发现、Spring Cloud Starter 和 REST API——都直接或传递地依赖它。核心模块提供三个基本抽象：用于多租户资源隔离的**命名空间模型**、用于一致键命名和集群兼容的 **Redis 键生成**框架，以及用于响应式 Pub/Sub 消费的**事件监听器容器**接口。该模块对服务发现或配置语义一无所知；它纯粹在基础设施层面运行。

## 概览

| 组件 | 类型 | 职责 | 关键文件 |
|-----------|------|---------------|----------|
| `CoSky` | Object | 品牌常量：`COSKY = "cosky"`、`KEY_SEPARATOR = ":"`、`VERSION` | [CoSky.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/CoSky.kt) |
| `Namespaced` | Interface | 在命名空间内操作的组件契约 | [Namespaced.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/Namespaced.kt) |
| `requireValidNamespace` | Function | 唯一的命名空间校验规则：非空、不含 `:` | [Namespaced.kt:35](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/Namespaced.kt#L35) |
| `NamespaceService` | Interface | 命名空间的 CRUD 操作（响应式 Flux/Mono） | [NamespaceService.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/NamespaceService.kt) |
| `RedisNamespaceService` | Class | 基于 Redis SET 的 `NamespaceService` 实现 | [RedisNamespaceService.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/redis/RedisNamespaceService.kt) |
| `RedisKeys` | Object | Hashtag 包装和 Redis 集群键工具 | [RedisKeys.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/util/RedisKeys.kt) |
| `EventListenerContainer` | Interface | 响应式事件订阅契约（`listen(topic): Mono<Flux<E>>`，订阅生效后才发出） | [EventListenerContainer.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/EventListenerContainer.kt) |
| `RedisEventListenerContainer` | Abstract Class | 带错误处理和日志的 Redis Pub/Sub 基础实现 | [RedisEventListenerContainer.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/redis/RedisEventListenerContainer.kt) |
| `ReactiveWatchCache` | Class | 所有一致性缓存背后的事件驱动本地缓存原语 | [ReactiveWatchCache.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/cache/ReactiveWatchCache.kt) |

## 命名空间模型

CoSky 中的每个资源——服务实例、配置、统计信息——都作用于一个**命名空间**。这在单个 Redis 实例中提供了逻辑多租户。命名空间模型由三个协作组件组成。

### `Namespaced` 接口

[`Namespaced`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/Namespaced.kt) 接口声明了一个属性：

```kotlin
interface Namespaced {
    val namespace: String

    companion object {
        const val DEFAULT: String = "${CoSky.COSKY}-{default}"   // "cosky-{default}"
        const val SYSTEM: String = "${CoSky.COSKY}-{system}"     // "cosky-{system}"
    }
}
```

`DEFAULT` 命名空间是常规用途的默认命名空间。`SYSTEM` 命名空间（`cosky-{system}`）保留给平台内部数据，如命名空间索引本身。不存在隐式命名空间：每个 `Namespaced` 组件都明确声明自己的命名空间。

### 显式命名空间

不存在进程级的全局命名空间。`ConfigService`、`ServiceRegistry` 和 `ServiceDiscovery` 的每个方法都把命名空间作为显式参数。在 Spring Cloud 应用中，组件从 `CoSkyProperties`（`spring.cloud.cosky.namespace`）获取命名空间，必要时它会被包装为 Redis Cluster 的 hashtag。

### 命名空间校验

[`requireValidNamespace`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/Namespaced.kt#L35) 是唯一的校验规则，所有基于 Redis 的服务以及带命名空间的标识符（`NamespacedConfigId`、`NamespacedServiceId`、`NamespacedInstanceId`）在构造时都会应用它：

- 命名空间不能为空；
- 不能包含键分隔符 `:`，它在每个 Redis 键中用于分隔命名空间（例如 `DiscoveryKeyGenerator.getNamespaceOfKey` 按第一个 `:` 切分）。

### `NamespaceService` -- 命名空间 CRUD

[`NamespaceService`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/NamespaceService.kt) 接口提供管理命名空间的响应式操作：

```mermaid
classDiagram
    class NamespaceService {
        <<interface>>
        +namespaces: Flux~String~
        +setNamespace(namespace: String): Mono~Boolean~
        +removeNamespace(namespace: String): Mono~Boolean~
    }

    class RedisNamespaceService {
        -redisTemplate: ReactiveStringRedisTemplate
        +namespaces: Flux~String~
        +setNamespace(namespace: String): Mono~Boolean~
        +removeNamespace(namespace: String): Mono~Boolean~
    }

    class Namespaced {
        <<interface>>
        +namespace: String
    }

    NamespaceService <|.. RedisNamespaceService

    style NamespaceService fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style RedisNamespaceService fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style Namespaced fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
```

<!-- Sources: cosky-core/src/main/kotlin/me/ahoo/cosky/core/NamespaceService.kt:23-27, cosky-core/src/main/kotlin/me/ahoo/cosky/core/redis/RedisNamespaceService.kt:27-56, cosky-core/src/main/kotlin/me/ahoo/cosky/core/Namespaced.kt:20-41 -->

`RedisNamespaceService` 将所有命名空间名称存储在键为 `cosky-{system}:ns_idx` 的 Redis SET 中：

```kotlin
companion object {
    const val NAMESPACE_IDX_KEY = "${Namespaced.SYSTEM}:ns_idx"
}
```

- `setNamespace(namespace)` -- `SADD` 将命名空间添加到 SET，如果是新添加的则返回 `true`
- `removeNamespace(namespace)` -- `SREM` 从 SET 中移除命名空间，如果存在则返回 `true`
- `namespaces` -- `SMEMBERS` 列出所有已注册的命名空间

## 键生成

CoSky 中所有 Redis 键遵循一致的模式：`{namespace}:{domain_segment}:{identifier}`。冒号（`:`）分隔符定义在 [`CoSky.KEY_SEPARATOR`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/CoSky.kt#L22)。

### `RedisKeys` 工具

[`RedisKeys`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/util/RedisKeys.kt) 对象提供集群感知的键包装。在 Redis 集群中，共享相同 hashtag `{...}` 的键保证落在同一个哈希槽上，从而支持多键 Lua 脚本：

```kotlin
object RedisKeys {
    fun ofKey(isCluster: Boolean, key: String): String {
        return if (!isCluster) key else hashTag(key)
    }

    fun hasWrap(key: String): Boolean { /* 检查 { } */ }
    fun wrap(key: String): String = "{$key}"
    fun unwrap(key: String): String { /* 去除 { } */ }
    fun hashTag(key: String): String { /* 如果尚未包装则包装 */ }
}
```

`RedisKeys` 是显式工具，而不是全局 key 拦截器。`NamespaceService`、`ConfigKeyGenerator`、`DiscoveryKeyGenerator` 和 REST API 都会保留调用方传入的 namespace 字符串。无论 Redis 拓扑如何，`CoSkyProperties` 都会对 Spring Cloud 客户端配置的 namespace 调用 `RedisKeys.ofKey(true, namespace)`，因此其实际生效值必须与 REST API 和 RBAC 绑定使用的 namespace 完全一致。

所有部署都应在写入数据前确定包含非空 hashtag 的命名空间，例如 `{dev}`、`{prod}` 或 `cosky-{dev}`。Redis Cluster 的多 key Lua 操作要求这样做，同时也能避免 Redis Standalone 下 REST 客户端与 Spring 实际生效的 namespace 不一致。普通 `dev` 与带 hashtag 的 `{dev}` 会生成不同的物理 key。转换既有命名空间必须制定明确的数据迁移与回滚方案；只修改配置会使旧数据表现为“丢失”。

### 键模式参考

下表展示了所有领域中键的结构方式。每个模块都有自己的键生成器（`DiscoveryKeyGenerator`、`ConfigKeyGenerator`），使用相同的 `{namespace}:` 前缀约定：

```mermaid
flowchart LR
    subgraph Namespace Keys["命名空间键"]
        NK["cosky-{system}:ns_idx<br><i>SET - 所有命名空间</i>"]
    end

    subgraph Discovery Keys["发现键"]
        SK["{ns}:svc_idx<br><i>SET - 服务 ID</i>"]
        IK["{ns}:svc_itc_idx:{svcId}<br><i>SET - 实例 ID</i>"]
        ID["{ns}:svc_itc:{instanceId}<br><i>STRING+TTL - 实例数据</i>"]
        ST["{ns}:svc_stat<br><i>HASH - 统计信息</i>"]
    end

    subgraph Config Keys["配置键"]
        CK["{ns}:cfg_idx<br><i>SET - 配置 ID</i>"]
        CD["{ns}:cfg:{configId}<br><i>HASH - 配置数据</i>"]
        HI["{ns}:cfg_htr_idx:{configId}<br><i>ZSET - 版本索引</i>"]
        CH["{ns}:cfg_htr:{configId}:{ver}<br><i>HASH - 历史快照</i>"]
    end

    NK -.->|存储命名空间| SK
    NK -.->|存储命名空间| CK
    SK -->|serviceId 关联| IK
    IK -->|instanceId 关联| ID
    CK -->|configId 关联| CD
    CD -->|version 关联| HI
    HI -->|version 映射| CH

    style NK fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style SK fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style IK fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style ID fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style ST fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CK fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CD fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style HI fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CH fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
```

<!-- Sources: cosky-core/src/main/kotlin/me/ahoo/cosky/core/redis/RedisNamespaceService.kt:29, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/DiscoveryKeyGenerator.kt:22-90, cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigKeyGenerator.kt:22-82 -->

| 模式 | 段 | 生成方法 | 示例 |
|---------|---------|-----------------|---------|
| `{cosky-system}:ns_idx` | 命名空间索引 | 在 `RedisNamespaceService` 中硬编码 | `cosky-{system}:ns_idx` |
| `{ns}:svc_idx` | 服务索引 | `DiscoveryKeyGenerator.getServiceIdxKey()` | `cosky-{default}:svc_idx` |
| `{ns}:svc_itc_idx:{serviceId}` | 实例索引 | `DiscoveryKeyGenerator.getInstanceIdxKey()` | `cosky-{default}:svc_itc_idx:order-service` |
| `{ns}:svc_itc:{instanceId}` | 实例数据 | `DiscoveryKeyGenerator.getInstanceKey()` | `cosky-{default}:svc_itc:order-service@http#10.0.0.1#8080` |
| `{ns}:svc_stat` | 服务统计 | `DiscoveryKeyGenerator.getServiceStatKey()` | `cosky-{default}:svc_stat` |
| `{ns}:svc_itc_exp` | 实例过期索引 | `DiscoveryKeyGenerator.getInstanceExpiryKey()` | `cosky-{default}:svc_itc_exp` |
| `{ns}:topology_idx` | 拓扑消费者 | `DiscoveryKeyGenerator.getTopologyIdxKey()` | `cosky-{default}:topology_idx` |
| `{ns}:topology:{consumer}` | 消费者依赖的生产者 | `DiscoveryKeyGenerator.getTopologyKey()` | `cosky-{default}:topology:gateway` |
| `{ns}:cfg_idx` | 配置索引 | `ConfigKeyGenerator.getConfigIdxKey()` | `cosky-{default}:cfg_idx` |
| `{ns}:cfg:{configId}` | 配置数据 | `ConfigKeyGenerator.getConfigKey()` | `cosky-{default}:cfg:application.yaml` |
| `{ns}:cfg_htr_idx:{configId}` | 历史索引 | `ConfigKeyGenerator.getConfigHistoryIdxKey()` | `cosky-{default}:cfg_htr_idx:application.yaml` |
| `{ns}:cfg_htr:{configId}:{version}` | 历史数据 | `ConfigKeyGenerator.getConfigHistoryKey()` | `cosky-{default}:cfg_htr:application.yaml:3` |

## 事件系统

CoSky 使用基于 Redis Pub/Sub 的事件驱动架构来实时传播变更。事件系统由两层定义：`cosky-core` 中的通用接口和 `cosky-discovery`、`cosky-config` 中的领域特定实现。

### `EventListenerContainer` 接口

[`EventListenerContainer<T, E>`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/EventListenerContainer.kt) 接口是一个最小的响应式契约：

```kotlin
interface EventListenerContainer<T, E : Any> {
    fun listen(topic: T): Mono<Flux<E>>

    fun receive(topic: T): Flux<E> = listen(topic).flatMapMany { it }
}
```

- `T` 是主题类型（例如，命名空间级事件用 `String`，实例级事件用 `NamespacedServiceId`，配置事件用 `NamespacedConfigId`）
- `E` 是事件类型（例如，服务变更通知用 `String`，实例变更用 `InstanceChangedEvent`，配置变更用 `ConfigChangedEvent`）
- `listen` 只在订阅生效（Redis 确认 `SUBSCRIBE`）后才发出事件流，因此之后发起的读取不会错过变更；`receive` 是将其展平的便捷方法
- 容器不拥有底层传输：Spring 提供一个所有 CoSky 容器共享的 `ReactiveRedisMessageListenerContainer`，并随上下文一起关闭它

### `RedisEventListenerContainer` 基类

[`RedisEventListenerContainer`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/redis/RedisEventListenerContainer.kt) 抽象类封装了 Spring 的 `ReactiveRedisMessageListenerContainer`，基于 `receiveLater` 实现 `listen`，并处理 `CancellationException`：

```mermaid
classDiagram
    class EventListenerContainer~T, E~ {
        <<interface>>
        +listen(topic: T): Mono~Flux~E~~
        +receive(topic: T): Flux~E~
    }

    class RedisEventListenerContainer~T, E~ {
        <<abstract>>
        #delegate: ReactiveRedisMessageListenerContainer
        +listen(topic: T): Mono~Flux~E~~
        #listenEvent(topic: T): Mono~Flux~E~~*
    }

    class RedisInstanceEventListenerContainer {
        +listenEvent(topic: NamespacedServiceId): Mono~Flux~InstanceChangedEvent~~
    }

    class RedisServiceEventListenerContainer {
        +listenEvent(topic: String): Mono~Flux~String~~
    }

    class RedisConfigEventListenerContainer {
        +listenEvent(topic: NamespacedConfigId): Mono~Flux~ConfigChangedEvent~~
    }

    class InstanceEventListenerContainer {
        <<interface>>
    }

    class ServiceEventListenerContainer {
        <<interface>>
    }

    class ConfigEventListenerContainer {
        <<interface>>
    }

    EventListenerContainer <|.. RedisEventListenerContainer
    EventListenerContainer <|.. InstanceEventListenerContainer
    EventListenerContainer <|.. ServiceEventListenerContainer
    EventListenerContainer <|.. ConfigEventListenerContainer
    RedisEventListenerContainer <|-- RedisInstanceEventListenerContainer
    RedisEventListenerContainer <|-- RedisServiceEventListenerContainer
    RedisEventListenerContainer <|-- RedisConfigEventListenerContainer
    InstanceEventListenerContainer <|.. RedisInstanceEventListenerContainer
    ServiceEventListenerContainer <|.. RedisServiceEventListenerContainer
    ConfigEventListenerContainer <|.. RedisConfigEventListenerContainer

    style EventListenerContainer fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style RedisEventListenerContainer fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style RedisInstanceEventListenerContainer fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style RedisServiceEventListenerContainer fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style RedisConfigEventListenerContainer fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style InstanceEventListenerContainer fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style ServiceEventListenerContainer fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style ConfigEventListenerContainer fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
```

<!-- Sources: cosky-core/src/main/kotlin/me/ahoo/cosky/core/EventListenerContainer.kt:11-20, cosky-core/src/main/kotlin/me/ahoo/cosky/core/redis/RedisEventListenerContainer.kt:10-39, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/InstanceEventListenerContainer.kt:5, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceEventListenerContainer.kt:5, cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigEventListenerContainer.kt:22 -->

### 事件流 -- 服务实例注册

以下序列图追踪了服务实例注册时以及外部订阅者观察变更时的完整事件流：

```mermaid
sequenceDiagram
    autonumber
    participant Client as 客户端应用
    participant Reg as RedisServiceRegistry
    participant Redis as Redis
    participant Sub as RedisInstanceEventListenerContainer
    participant App as 订阅者应用

    Client->>Reg: register(namespace, serviceInstance)
    Reg->>Redis: EVALSHA registry_register.lua<br>(SADD svc_itc_idx, SET svc_itc data, PUBLISH, INCR stat)
    Redis-->>Redis: 原子执行 Lua
    Redis->>Redis: PUBLISH {ns}:svc_itc:{instanceId} "register"
    Redis->>Sub: 投递 Pub/Sub 消息
    Sub->>Sub: 解码通道为 NamespacedServiceId<br>解码消息为 InstanceChangedEvent
    Sub->>App: InstanceChangedEvent(namespacedServiceId, REGISTER, instance)
    App->>App: 更新本地缓存或刷新
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt:50-76, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisInstanceEventListenerContainer.kt:22-42, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/InstanceChangedEvent.kt:20-47 -->

### 事件流 -- 配置变更传播

配置变更遵循类似的 Pub/Sub 模式。当配置被设置或回滚时，Lua 脚本发布到配置键通道，`RedisConfigEventListenerContainer` 将消息解码为 `ConfigChangedEvent`：

```mermaid
sequenceDiagram
    autonumber
    participant Admin as 管理员 / API
    participant Svc as RedisConfigService
    participant Redis as Redis
    participant Sub as RedisConfigEventListenerContainer
    participant Cache as RedisConsistencyConfigService
    participant Refresher as CoSkyConfigRefresher

    Admin->>Svc: setConfig(namespace, configId, data)
    Svc->>Redis: EVALSHA config_set.lua<br>(HSET cfg, ZADD cfg_htr_idx, HSET cfg_htr, PUBLISH)
    Redis-->>Redis: 原子执行 Lua
    Redis->>Redis: PUBLISH {ns}:cfg:{configId} "set"
    Redis->>Sub: 投递 Pub/Sub 消息
    Sub->>Sub: 解码为 ConfigChangedEvent<br>(NamespacedConfigId, SET)
    Sub->>Cache: 收到 ConfigChangedEvent
    Cache->>Redis: 重新加载配置，发布新快照
    Cache->>Refresher: watchConfig() 推送新快照
    Refresher->>Refresher: 发布 RefreshEvent（缓存已更新）
```

<!-- Sources: cosky-config/src/main/kotlin/me/ahoo/cosky/config/redis/RedisConfigService.kt:95-105, cosky-config/src/main/kotlin/me/ahoo/cosky/config/redis/RedisConfigEventListenerContainer.kt:15-30, cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigChangedEvent.kt:20-36 -->

### 事件类型

| 模块 | 事件类 | 主题类型 | 可能的事件 |
|--------|------------|------------|-----------------|
| 发现 | `InstanceChangedEvent` | `NamespacedServiceId` | `REGISTER`, `DEREGISTER`, `EXPIRED`, `RENEW`, `SET_METADATA` |
| 发现 | `String`（命名空间名称） | `String` | 服务索引变更（服务添加/移除） |
| 配置 | `ConfigChangedEvent` | `NamespacedConfigId` | `SET`, `ROLLBACK`, `REMOVE` |

## 命名空间作用域操作流程

CoSky 中的每个操作都流经命名空间模型。`CoSkyProperties` 会把 Spring Cloud 客户端配置的 namespace 包装为 hashtag，Spring 组件在每次调用时显式传入它；命名空间经过校验，除此之外原样使用。下图说明了典型的服务发现请求如何在命名空间作用域内解析键：

```mermaid
flowchart TD
    A[Spring 客户端配置 namespace] --> B[CoSkyProperties 调用 RedisKeys.ofKey]
    B --> C[Spring 组件获取 CoSkyProperties.namespace]
    C --> D[组件调用 getInstances namespace, serviceId]
    D --> E{requireValidNamespace}
    E -->|为空或包含 ':'| F[IllegalArgumentException]
    E -->|合法| G[原样使用 namespace]
    G --> H[DiscoveryKeyGenerator 拼接 namespace]
    H --> I[EVALSHA discovery_get_instances.lua]
    I --> J[从 svc_itc 键读取实例数据]
    J --> K[通过 ServiceInstanceCodec 解码]
    K --> L[返回 ServiceInstance 快照列表]

    style A fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style B fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style C fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style D fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style E fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style F fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style G fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style H fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style I fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style J fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style K fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style L fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
```

<!-- Sources: cosky-spring-cloud-core/src/main/kotlin/me/ahoo/cosky/spring/cloud/CoSkyProperties.kt:38-46, cosky-core/src/main/kotlin/me/ahoo/cosky/core/Namespaced.kt:35-41, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/DiscoveryKeyGenerator.kt:54-58, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceDiscovery.kt:33-50 -->

## 交叉引用

- [架构概览](./architecture) -- 高层模块结构和设计原则。
- [配置模块](./config-service) -- `cosky-config` 如何基于核心的命名空间和事件抽象构建。
- [服务发现模块](./service-discovery) -- `cosky-discovery` 如何基于核心的命名空间和事件抽象构建。

## 参考

- [CoSky.kt -- 品牌常量](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/CoSky.kt)
- [Namespaced.kt -- 命名空间契约和常量](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/Namespaced.kt)
- [ReactiveWatchCache.kt -- 事件驱动的本地缓存原语](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/cache/ReactiveWatchCache.kt)
- [NamespaceService.kt -- 命名空间 CRUD 接口](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/NamespaceService.kt)
- [RedisNamespaceService.kt -- 基于 Redis SET 的命名空间存储](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/redis/RedisNamespaceService.kt)
- [RedisKeys.kt -- 集群 hashtag 和键工具](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/util/RedisKeys.kt)
- [EventListenerContainer.kt -- 响应式事件订阅接口](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/EventListenerContainer.kt)
- [RedisEventListenerContainer.kt -- Redis Pub/Sub 基类](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/redis/RedisEventListenerContainer.kt)
- [DiscoveryKeyGenerator.kt -- 服务发现键模式](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/DiscoveryKeyGenerator.kt)
- [ConfigKeyGenerator.kt -- 配置键模式](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigKeyGenerator.kt)
- [InstanceChangedEvent.kt -- 发现事件类型](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/InstanceChangedEvent.kt)
- [ConfigChangedEvent.kt -- 配置事件类型](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigChangedEvent.kt)
