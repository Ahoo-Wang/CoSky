---
title: Using the SDK
description: Use CoSky's configuration and discovery services directly, without the Spring Cloud starters.
---

# Using the SDK

The Spring Cloud starters wire everything for you. Use the SDK directly when you are not on Spring Cloud, or need finer control: managing configs from a tool, registering an instance on behalf of another process, or watching a config.

All APIs are reactive (Project Reactor `Mono` / `Flux`) and take the namespace explicitly. Namespaces must not be blank or contain `:`; wrap them in a hash tag (e.g. `{dev}`) when Redis runs in cluster mode.

## Dependencies

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosky:cosky-bom:6.2.0"))
    implementation(platform("me.ahoo.cosky:cosky-dependencies:6.2.0"))
    implementation("me.ahoo.cosky:cosky-config")     // configuration
    implementation("me.ahoo.cosky:cosky-discovery")  // service discovery
}
```

Both build on Spring Data Redis with Lettuce:

```kotlin
val connectionFactory = LettuceConnectionFactory("localhost", 6379).apply { afterPropertiesSet() }
val redisTemplate = ReactiveStringRedisTemplate(connectionFactory)
// Shared by every cache that follows changes.
val listenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)
```

## Configuration

`RedisConfigService` reads and writes Redis directly; `RedisConsistencyConfigService` adds the local cache kept current by change events, and is what applications should read through.

```kotlin
val redisConfigService = RedisConfigService(redisTemplate)
val configService = RedisConsistencyConfigService(
    delegate = redisConfigService,
    configEventListenerContainer = RedisConfigEventListenerContainer(listenerContainer),
)

configService.setConfig("{dev}", "order-service.yaml", "greeting: hello").block()
configService.getConfig("{dev}", "order-service.yaml").block()?.data      // served from memory after the first read

// The current snapshot, then one snapshot per change, after the cache is updated.
configService.watchConfig("{dev}", "order-service.yaml")
    .subscribe { snapshot -> println("v${snapshot.version}: ${snapshot.value?.data}") }

configService.getConfigVersions("{dev}", "order-service.yaml").collectList().block()
configService.rollback("{dev}", "order-service.yaml", targetVersion = 1).block()

configService.close()   // stops listening when you are done
```

## Service discovery

### Registering and renewing

```kotlin
val registry = RedisServiceRegistry(RegistryProperties(instanceTtl = Duration.ofSeconds(60)), redisTemplate)
val renewal = RenewInstanceService(RenewProperties(period = Duration.ofSeconds(10)), registry)

val instance = Instance.asInstance(serviceId = "order-service", schema = "http", host = "10.0.0.5", port = 8080)
    .asServiceInstance(weight = 2, metadata = mapOf("zone" to "a"))

registry.register("{dev}", instance).block()
renewal.start()        // renews every registered ephemeral instance and reaps expired ones

registry.setMetadata("{dev}", instance.serviceId, instance.instanceId, "zone", "b").block()   // one key
registry.setMetadata("{dev}", instance.serviceId, instance.instanceId, mapOf("zone" to "b")).block() // replace all

renewal.stop()
registry.deregister("{dev}", instance).block()
```

Register a fixed instance with `asServiceInstance(isEphemeral = false)`; it is never renewed nor expired.

### Discovering

```kotlin
val discovery = ConsistencyRedisServiceDiscovery(
    delegate = RedisServiceDiscovery(redisTemplate),
    serviceEventListenerContainer = RedisServiceEventListenerContainer(listenerContainer),
    instanceEventListenerContainer = RedisInstanceEventListenerContainer(listenerContainer),
)

discovery.getServices("{dev}").block()                       // List<String>
discovery.getInstances("{dev}", "order-service").block()     // List<ServiceInstance>, expired ones excluded

val loadBalancer = BinaryWeightRandomLoadBalancer(discovery)
loadBalancer.choose("{dev}", "order-service").block()        // weighted random choice

discovery.close()
```

Each call returns an immutable snapshot; an unchanged snapshot is the same list instance, so caching derived data per snapshot is cheap.

## See also

- [Architecture](./architecture.md) for the data model, events and consistency guarantees.
- The tests under `cosky-config` and `cosky-discovery` exercise every API against Redis.
