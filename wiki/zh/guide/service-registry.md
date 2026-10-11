---
title: "Service Registry"
---

# 服务注册

CoSky 的服务注册管理微服务集群中服务实例的生命周期。基于 Redis 和 Lua 脚本，它提供原子性、无竞态条件的注册、注销、心跳续约和元数据管理——所有操作都在多租户命名空间模型下运行。

| 方面 | 详情 |
|---|---|
| **接口** | `ServiceRegistry` |
| **Redis 实现** | `RedisServiceRegistry` |
| **存储引擎** | Redis Hash + Set + Lua 脚本 |
| **并发模型** | 响应式（`Mono<Boolean>`） |
| **心跳** | `RenewInstanceService`（定时保活） |
| **序列化** | `ServiceInstanceCodec` |

## ServiceRegistry 接口

[`ServiceRegistry`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt) 接口定义了所有注册操作的契约。

| 方法 | 返回类型 | 描述 | 源码 |
|---|---|---|---|
| `register` | `Mono<Boolean>` | 注册服务实例，可选 TTL | [ServiceRegistry.kt:32](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L32) |
| `deregister` | `Mono<Boolean>` | 从注册中心移除服务实例 | [ServiceRegistry.kt:42](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L42) |
| `renew` | `Mono<Boolean>` | 续约/延长临时实例的 TTL | [ServiceRegistry.kt:40](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L40) |
| `setService` | `Mono<Boolean>` | 在命名空间索引中创建命名服务条目 | [ServiceRegistry.kt:23](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L23) |
| `removeService` | `Mono<Boolean>` | 从命名空间索引中移除命名服务 | [ServiceRegistry.kt:23](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L23) |
| `setMetadata` | `Mono<Boolean>` | `setMetadata(key, value)` 设置一项并保留其他元数据（[ServiceRegistry.kt:55](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L55)）；`setMetadata(map)` 替换全部元数据（[ServiceRegistry.kt:66](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L66)） |
| `reapExpiredInstances` | `Mono<Long>` | 移除 TTL 已过期的实例并发布其 `expired` 事件 | [ServiceRegistry.kt:78](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L78) |

## ServiceInstance 数据模型

[`ServiceInstance`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceInstance.kt) 继承基础 [`Instance`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/Instance.kt) 接口，包含以下字段：

| 字段 | 类型 | 默认值 | 描述 |
|---|---|---|---|
| `instanceId` | `String` | -- | 唯一标识符：`{serviceId}@{schema}#{host}#{port}` |
| `serviceId` | `String` | -- | 逻辑服务名称 |
| `schema` | `String` | -- | 协议模式（`http`、`https` 等） |
| `host` | `String` | -- | 主机地址 |
| `port` | `Int` | -- | 端口号 |
| `weight` | `Int` | `1` | 负载均衡权重 |
| `isEphemeral` | `Boolean` | `true` | 临时实例会过期；持久实例不会 |
| `ttlAt` | `Long` | `TTL_AT_FOREVER (-1)` | 绝对 TTL 过期时间戳（纪元秒） |
| `metadata` | `Map<String, String>` | `emptyMap()` | 用户自定义键值元数据；拒绝以 `_` 开头的键 |

`ServiceInstance` 上的 `isExpired` 属性将 `ttlAt` 与当前系统时间比较，以确定临时实例是否已过期 ([ServiceInstance.kt:34](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceInstance.kt#L34))。

### ServiceInstanceCodec 序列化

[`ServiceInstanceCodec`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceInstanceCodec.kt) 负责实例数据与 Redis 哈希字段之间的编码和解码。它使用 `_` 前缀表示元数据键，保留 `__` 用于系统元数据：

```kotlin
// 编码：元数据键 "version" 在 Redis 中变为 "_version"
fun encodeMetadataKey(key: String): String {
    require(!key.startsWith(METADATA_PREFIX))
    return METADATA_PREFIX + key
}
```

元数据键不得以 `_` 开头。该前缀保留给 `__last_renew_pub_ttl_at` 等内部字段；拒绝此类键可防止用户元数据与注册中心状态冲突。

`decode` 函数 ([ServiceInstanceCodec.kt:57](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceInstanceCodec.kt#L57)) 将 Redis `HGETALL` 返回的扁平键值列表解析为 `ServiceInstance` 对象。

## Redis 实现

### RedisServiceRegistry

[`RedisServiceRegistry`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt) 是 `ServiceRegistry` 的 Redis 实现。它使用 Lua 脚本进行原子操作，并维护已注册临时实例的内存映射：

```kotlin
class RedisServiceRegistry(
    private val registryProperties: RegistryProperties,
    private val redisTemplate: ReactiveStringRedisTemplate
) : ServiceRegistry
```

关键设计要点：
- 临时实例被跟踪在 `ConcurrentHashMap<NamespacedInstanceId, ServiceInstance>` 中，用于心跳续约 ([RedisServiceRegistry.kt:47](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt#L47))。
- `register` 方法只在 Lua 脚本执行成功后才将实例添加到临时映射——在 `doOnNext` 中，且仅当脚本返回 `true` 时 ([RedisServiceRegistry.kt:110](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt#L110))。
- 如果续约失败（实例键不存在），会自动重新注册实例 ([RedisServiceRegistry.kt:203](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt#L203))。

### DiscoveryRedisScripts

[`DiscoveryRedisScripts`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/DiscoveryRedisScripts.kt) 通过 `LuaScripts` 从类路径加载所有注册相关的 Lua 脚本，它会解析 `-- @include` 指令，让脚本共享 `cosky/discovery/discovery_lib.lua`。调用时即可确定的 key 由 `DiscoveryKeyGenerator` 生成并通过 `KEYS` 传入；只能在脚本内确定的 key 使用共享的 Lua key 函数，`DiscoveryLuaKeysTest` 保证它们与 Kotlin 生成器一致：

| 脚本 | 资源文件 | 用途 |
|---|---|---|
| `SCRIPT_REGISTRY_REGISTER` | `registry_register.lua` | 原子实例注册 |
| `SCRIPT_REGISTRY_DEREGISTER` | `registry_deregister.lua` | 原子实例移除 |
| `SCRIPT_REGISTRY_RENEW` | `registry_renew.lua` | 带发布节流的 TTL 续约 |
| `SCRIPT_REGISTRY_SET_METADATA` | `registry_set_metadata.lua` | 替换实例的全部元数据 |
| `SCRIPT_REGISTRY_PUT_METADATA` | `registry_put_metadata.lua` | 设置元数据项，保留其他元数据 |
| `SCRIPT_REGISTRY_REAP` | `registry_reap.lua` | 通过过期索引回收过期实例 |
| `SCRIPT_REGISTRY_SET_SERVICE` | `registry_set_service.lua` | 在命名空间索引中创建服务 |
| `SCRIPT_REGISTRY_REMOVE_SERVICE` | `registry_remove_service.lua` | 从命名空间索引中移除服务 |

### RegistryProperties

[`RegistryProperties`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RegistryProperties.kt) 配置默认实例 TTL：

| 属性 | 类型 | 默认值 | 描述 |
|---|---|---|---|
| `instanceTtl` | `Duration` | `1 minute` | 临时实例的生存时间 |

## RenewInstanceService（心跳）

[`RenewInstanceService`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RenewInstanceService.kt) 为临时实例提供保活机制。它在调用方持有的调度器（默认 `Schedulers.parallel()`）上定期续约所有已注册的临时实例：

| 属性 | 默认值 | 描述 |
|---|---|---|
| `initialDelay` | `1 second` | 首次续约周期前的延迟 |
| `period` | `10 seconds` | 续约周期间隔 |

续约周期必须小于 `RegistryProperties.instanceTtl`，以防止过早过期。该服务遍历所有 `registeredEphemeralInstances` 并通过 `ServiceRegistry` 对每个实例调用 `renew`（[RenewInstanceService.kt:117](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RenewInstanceService.kt#L117)）：

- 每次续约以 `period` 为超时上限，失败只记录日志，不影响其他实例；
- 上一轮续约仍在进行时，下一次调度会被跳过而不是重叠执行；
- `stop()` 只取消调度，服务可以再次启动。

## 时序图

### 注册流程

```mermaid
sequenceDiagram
    autonumber
    participant App as 应用程序
    participant SR as RedisServiceRegistry
    participant EMap as EphemeralInstances 映射
    participant Redis as Redis (Lua 脚本)
    participant Sub as PubSub 订阅者

    App->>SR: register(namespace, serviceInstance)
    SR->>SR: 构建 ARGV（ttl、serviceId、instanceId、schema、host、port、weight、metadata）
    SR->>Redis: EVAL registry_register.lua KEYS=[svc_idx, svc_stat, svc_itc_idx, svc_itc, svc_itc_exp] ARGV=[...]
    Redis->>Redis: SADD svc_itc_idx:{serviceId} {instanceId}
    Redis->>Redis: SADD svc_idx {serviceId}
    Redis->>Redis: HMSET svc_itc:{instanceId} {fields}
    Redis->>Sub: PUBLISH svc_itc:{instanceId} "register"
    Redis->>Redis: ZADD svc_itc_exp {now + ttl} {instance}
    Redis->>Redis: EXPIRE svc_itc:{instanceId} {ttl}
    Redis-->>SR: Boolean (成功)
    alt registered == true
        SR->>EMap: addEphemeralInstance(namespace, instance)
    end
    SR-->>App: Mono<Boolean>
```

<!-- Sources: cosky-discovery/src/main/resources/registry_register.lua, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt:105, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/DiscoveryRedisScripts.kt:26 -->

### 续约 / 心跳流程

```mermaid
sequenceDiagram
    autonumber
    participant RS as RenewInstanceService
    participant SR as RedisServiceRegistry
    participant Redis as Redis (Lua 脚本)
    participant Sub as PubSub 订阅者

    RS->>RS: 调度器触发（每 period 秒）
    RS->>RS: 遍历 registeredEphemeralInstances
    RS->>SR: renew(namespace, instance)
    SR->>Redis: EVAL registry_renew.lua KEYS=[svc_itc, svc_itc_exp] ARGV=[ttl, serviceId, instanceId, publishTtlAt]
    Redis->>Redis: TTL svc_itc:{instanceId}（检查是否存在）
    alt 实例存在
        Redis->>Redis: EXPIRE svc_itc:{instanceId} {ttl}
        Redis->>Redis: ZADD svc_itc_exp {now + ttl} {instance}
        Redis->>Redis: 检查发布节流窗口
        alt 超过节流窗口
            Redis->>Redis: HSET __last_renew_pub_ttl_at {currentTtlAt}
            Redis->>Sub: PUBLISH svc_itc:{instanceId} "renew"
        end
        Redis-->>SR: 1 (成功)
    else 实例缺失（TTL <= 0）
        Redis-->>SR: -1 或 -2 (失败)
        SR->>SR: 自动重新注册实例
    end
```

<!-- Sources: cosky-discovery/src/main/resources/registry_renew.lua, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RenewInstanceService.kt:117, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt:177 -->

### 注销流程

```mermaid
sequenceDiagram
    autonumber
    participant App as 应用程序
    participant SR as RedisServiceRegistry
    participant EMap as EphemeralInstances 映射
    participant Redis as Redis (Lua 脚本)
    participant Sub as PubSub 订阅者

    App->>SR: deregister(namespace, instance)
    SR->>EMap: removeEphemeralInstance(namespace, instance)
    SR->>Redis: EVAL registry_deregister.lua KEYS=[svc_itc_idx, svc_itc, svc_itc_exp] ARGV=[instanceId, serviceId]
    Redis->>Redis: ZREM svc_itc_exp {instance}
    Redis->>Redis: SREM svc_itc_idx:{serviceId} {instanceId}
    alt removed == 1
        Redis->>Sub: PUBLISH svc_itc:{instanceId} "deregister"
        Redis->>Redis: DEL svc_itc:{instanceId}
        Redis-->>SR: true
    else 已被移除
        Redis-->>SR: false
    end
    SR-->>App: Mono<Boolean>
```

<!-- Sources: cosky-discovery/src/main/resources/registry_deregister.lua, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt:210, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/DiscoveryRedisScripts.kt:26 -->

## 过期回收

临时实例的 key 的 TTL 到期后，实例即过期。把它从服务索引中移除并发布 `expired` 事件，由**过期回收器**完成：

- `register` 和 `renew` 把实例加入命名空间的过期索引 `{namespace}:svc_itc_exp`，分数为其 `ttlAt`；`deregister` 和固定实例注册会将其移除。
- `registry_reap.lua` 以 `O(log N + k)` 取出到期的候选（`ZRANGEBYSCORE -inf now LIMIT 0 100`）。key 已不存在的候选会从索引中移除并广播 `expired`；key 仍存活的候选会被重新打分。索引只列出候选，key 的 TTL 才是权威依据，因此陈旧的索引永远不会移除存活实例（[RedisServiceRegistry.kt:249](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt#L249)）。
- `RenewInstanceService` 在每轮续约后回收它所续约的命名空间，因此每个已注册客户端都会保持其命名空间整洁，`expired` 事件不再依赖 `cosky-rest-api`（[RenewInstanceService.kt:99](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RenewInstanceService.kt#L99)）。

读取（`discovery_get_instances.lua`）和统计任务仍会懒清理无效实例，这也覆盖了不维护索引的旧版本客户端注册的实例。

### 携带 ttlAt 的续约事件

续约事件可以携带新的 `ttlAt`（`renew:<ttlAt>`），让消费者无需访问 Redis 即可更新缓存的实例。解析已支持该载荷（[InstanceChangedEvent.kt:50](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/InstanceChangedEvent.kt#L50)）；发布默认关闭（`spring.cloud.cosky.discovery.registry.renew-event-carries-ttl-at`，[RegistryProperties.kt:32](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RegistryProperties.kt#L32)）。只有在所有客户端都运行能解析它的版本后才应开启：旧版本客户端会拒绝该载荷。

## Redis 键结构

注册中心使用结构化的键模式，在每个命名空间内组织服务和实例数据：

| Redis 键 | 类型 | 用途 | 示例 |
|---|---|---|---|
| `{namespace}:svc_idx` | SET | 命名空间中所有服务 ID 的集合 | `production:svc_idx` |
| `{namespace}:svc_stat` | HASH | 服务 ID 到实例数统计 | `production:svc_stat` |
| `{namespace}:svc_itc_idx:{serviceId}` | SET | 指定服务的实例 ID 集合 | `production:svc_itc_idx:order-service` |
| `{namespace}:svc_itc:{instanceId}` | HASH | 实例数据（字段：instanceId、serviceId、schema、host、port、weight、ephemeral、metadata） | `production:svc_itc:order-service@http#10.0.1.5#8080` |
| `{namespace}:svc_itc_exp` | ZSET | 过期索引：按 `ttlAt` 打分的临时实例，即回收候选 | `production:svc_itc_exp` |
| `{namespace}:topology_idx` | HASH | 拓扑索引：消费者名称到时间戳 | `production:topology_idx` |
| `{namespace}:topology:{consumer}` | HASH | 拓扑依赖：生产者名称到时间戳 | `production:topology:gateway-service` |

注意：`ttl_at` **并不**存储在实例哈希中。它由发现脚本在读取时按 `now + ttl` 计算得出（[discovery_lib.lua:50](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/cosky/discovery/discovery_lib.lua#L50)）。续约脚本还可能向哈希中写入系统字段 `__last_renew_pub_ttl_at`，用于发布节流（[registry_renew.lua:20](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/registry_renew.lua#L20)）。

实例 ID 格式为 `{serviceId}@{schema}#{host}#{port}`（例如 `order-service@http#10.0.1.5#8080`），由 [`Instance.asInstanceId`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/Instance.kt#L57) 定义。

## 类图

```mermaid
classDiagram
    class ServiceRegistry {
        <<interface>>
        +register(namespace, serviceInstance) Mono~Boolean~
        +deregister(namespace, serviceInstance) Mono~Boolean~
        +renew(namespace, serviceInstance) Mono~Boolean~
        +setService(namespace, serviceId) Mono~Boolean~
        +removeService(namespace, serviceId) Mono~Boolean~
        +setMetadata(namespace, serviceId, instanceId, key, value) Mono~Boolean~
        +registeredEphemeralInstances Map
    }
    class RedisServiceRegistry {
        -registryProperties: RegistryProperties
        -redisTemplate: ReactiveStringRedisTemplate
        +registeredEphemeralInstances: ConcurrentHashMap
        -registerInternal(namespace, instance) Mono~Boolean~
        -addEphemeralInstance(namespace, instance)
        -removeEphemeralInstance(namespace, instance)
    }
    class RegistryProperties {
        +instanceTtl: Duration
    }
    class RenewProperties {
        +initialDelay: Duration
        +period: Duration
    }
    class RenewInstanceService {
        -renewProperties: RenewProperties
        -serviceRegistry: ServiceRegistry
        -scheduler: Scheduler
        +isRunning: Boolean
        +start()
        +stop()
        -renew()
    }
    class DiscoveryRedisScripts {
        +SCRIPT_REGISTRY_REGISTER: RedisScript~Boolean~
        +SCRIPT_REGISTRY_DEREGISTER: RedisScript~Boolean~
        +SCRIPT_REGISTRY_RENEW: RedisScript~Long~
        +SCRIPT_REGISTRY_SET_METADATA: RedisScript~Boolean~
        +SCRIPT_REGISTRY_SET_SERVICE: RedisScript~Boolean~
        +SCRIPT_REGISTRY_REMOVE_SERVICE: RedisScript~Boolean~
    }
    class ServiceInstanceCodec {
        +encodeMetadataKey(key) String
        +encodeMetadata(preArgs, metadata) List~String~
        +decode(instanceData) ServiceInstance
    }

    ServiceRegistry <|.. RedisServiceRegistry : 实现
    RedisServiceRegistry --> RegistryProperties : 配置
    RedisServiceRegistry --> DiscoveryRedisScripts : 使用 Lua 脚本
    RedisServiceRegistry --> ServiceInstanceCodec : 编码使用
    RenewInstanceService --> ServiceRegistry : 续约
    RenewInstanceService --> RenewProperties : 配置
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt:22, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt:39, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RegistryProperties.kt:22, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RenewInstanceService.kt:39, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RenewProperties.kt:22, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/DiscoveryRedisScripts.kt:23 -->

## 相关页面

- [服务发现](./service-discovery) -- 客户端如何发现已注册的实例
- [负载均衡](./load-balancers) -- 如何从注册中心选择实例
- [服务拓扑](./service-topology) -- 如何构建服务依赖图

## 参考文献

- [ServiceRegistry.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt)
- [ServiceInstance.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceInstance.kt)
- [ServiceInstanceCodec.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceInstanceCodec.kt)
- [Instance.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/Instance.kt)
- [RedisServiceRegistry.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceRegistry.kt)
- [DiscoveryRedisScripts.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/DiscoveryRedisScripts.kt)
- [RegistryProperties.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RegistryProperties.kt)
- [RenewInstanceService.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RenewInstanceService.kt)
- [RenewProperties.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/RenewProperties.kt)
- [DiscoveryKeyGenerator.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/DiscoveryKeyGenerator.kt)
- [registry_register.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/registry_register.lua)
- [registry_renew.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/registry_renew.lua)
- [registry_deregister.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/registry_deregister.lua)
