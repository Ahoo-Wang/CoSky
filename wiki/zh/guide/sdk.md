---
title: 使用 SDK
description: 不依赖 Spring Cloud starter，直接使用 CoSky 的配置与服务发现服务。
---

# 使用 SDK

Spring Cloud starter 会替你装配好一切。以下情况可以直接使用 SDK：不使用 Spring Cloud，或需要更细的控制，例如用工具管理配置、代替另一个进程注册实例、监听某个配置。

所有 API 都是响应式的（Project Reactor 的 `Mono` / `Flux`），并显式传入命名空间。命名空间不能为空，也不能包含 `:`；Redis 为集群模式时，请把它包进 hash tag（例如 `{dev}`）。

## 依赖

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosky:cosky-bom:6.0.0"))
    implementation(platform("me.ahoo.cosky:cosky-dependencies:6.0.0"))
    implementation("me.ahoo.cosky:cosky-config")     // 配置
    implementation("me.ahoo.cosky:cosky-discovery")  // 服务发现
}
```

两者都基于 Spring Data Redis 与 Lettuce：

```kotlin
val connectionFactory = LettuceConnectionFactory("localhost", 6379).apply { afterPropertiesSet() }
val redisTemplate = ReactiveStringRedisTemplate(connectionFactory)
// 所有跟随变更的缓存共用。
val listenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)
```

## 配置

`RedisConfigService` 直接读写 Redis；`RedisConsistencyConfigService` 在其上加了由变更事件保持最新的本地缓存，应用应通过它读取。

```kotlin
val redisConfigService = RedisConfigService(redisTemplate)
val configService = RedisConsistencyConfigService(
    delegate = redisConfigService,
    configEventListenerContainer = RedisConfigEventListenerContainer(listenerContainer),
)

configService.setConfig("{dev}", "order-service.yaml", "greeting: hello").block()
configService.getConfig("{dev}", "order-service.yaml").block()?.data      // 首次读取后由内存提供

// 先发出当前快照，之后每次变更（缓存更新后）发出一个快照。
configService.watchConfig("{dev}", "order-service.yaml")
    .subscribe { snapshot -> println("v${snapshot.version}: ${snapshot.value?.data}") }

configService.getConfigVersions("{dev}", "order-service.yaml").collectList().block()
configService.rollback("{dev}", "order-service.yaml", targetVersion = 1).block()

configService.close()   // 用完后停止监听
```

## 服务发现

### 注册与续约

```kotlin
val registry = RedisServiceRegistry(RegistryProperties(instanceTtl = Duration.ofSeconds(60)), redisTemplate)
val renewal = RenewInstanceService(RenewProperties(period = Duration.ofSeconds(10)), registry)

val instance = Instance.asInstance(serviceId = "order-service", schema = "http", host = "10.0.0.5", port = 8080)
    .asServiceInstance(weight = 2, metadata = mapOf("zone" to "a"))

registry.register("{dev}", instance).block()
renewal.start()        // 为所有已注册的临时实例续约，并清理过期实例

registry.setMetadata("{dev}", instance.serviceId, instance.instanceId, "zone", "b").block()   // 设置一个键
registry.setMetadata("{dev}", instance.serviceId, instance.instanceId, mapOf("zone" to "b")).block() // 整体替换

renewal.stop()
registry.deregister("{dev}", instance).block()
```

用 `asServiceInstance(isEphemeral = false)` 注册固定实例；它既不续约也不过期。

### 发现

```kotlin
val discovery = ConsistencyRedisServiceDiscovery(
    delegate = RedisServiceDiscovery(redisTemplate),
    serviceEventListenerContainer = RedisServiceEventListenerContainer(listenerContainer),
    instanceEventListenerContainer = RedisInstanceEventListenerContainer(listenerContainer),
)

discovery.getServices("{dev}").block()                       // List<String>
discovery.getInstances("{dev}", "order-service").block()     // List<ServiceInstance>，不含已过期实例

val loadBalancer = BinaryWeightRandomLoadBalancer(discovery)
loadBalancer.choose("{dev}", "order-service").block()        // 加权随机选择

discovery.close()
```

每次调用返回不可变快照；未变化的快照是同一个列表实例，所以按快照缓存派生数据的成本很低。

## 另见

- [架构](./architecture.md)：数据模型、事件与一致性保证。
- `cosky-config` 和 `cosky-discovery` 下的测试针对 Redis 覆盖了每个 API。
