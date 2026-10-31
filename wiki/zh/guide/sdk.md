---
title: 使用 SDK
description: 不依赖 Spring 使用 Kotlin 客户端，了解快照和连接状态，以及其他语言的客户端。
---

# 使用 SDK

`cosky-client` 是一个协程客户端，不依赖 Spring 或 Redis，starter 就构建在它之上。需要 JDK 21 或更高版本。

```kotlin
implementation("me.ahoo.cosky:cosky-client:8.0.0")
```

```kotlin
val client = CoSkyClient(
    CoSkyClientOptions(
        endpoints = listOf("cosky-0.internal:9090", "cosky-1.internal:9090"),
        token = System.getenv("COSKY_TOKEN"),
        clientName = "order-service",
    ),
)
val prod = client.namespace("prod")
```

## 读取 Config

```kotlin
// 读一次，等待第一个视图。
val config: Resource? = prod.configs.load("order-service.yaml")

// 始终最新，从内存读取。
val current: StateFlow<Resource?> = prod.configs.watch("order-service.yaml")
current.collect { apply(it?.config?.content) }
```

## 查找实例

```kotlin
val instances: StateFlow<List<Resource>> = prod.instances.watch("payment")
val services: Set<String> = prod.instances.services()

val balancer = prod.loadBalancer("payment")
val chosen: Resource? = balancer.choose()
```

`choose()` 按权重比例随机选择，不分配内存，也不挂起。没有可选的实例时返回 `null`。

## 注册

```kotlin
val registration = prod.instances.register(
    service = "order-service",
    endpoint = endpoint {
        scheme = "http"
        host = "10.0.0.7"
        port = 8080
    },
    metadata = mapOf("zone" to "az1"),
    weight = 2,
)
// ...
registration.close()
```

客户端负责维持租约，租约丢失时重新注册全部实例。关闭最后一个注册时租约结束。

## 关闭

`client.close()` 在 3 秒内撤销租约，超时后由 server 让它们到期。

## 连接状态

`client.connectionState` 是一个 `StateFlow`：

| 状态 | 含义 |
|---|---|
| `CONNECTING` | 还没有 server 响应。 |
| `SYNCED` | 正在跟随一个 server。 |
| `DEGRADED` | 未连接：用内存或快照中已知的数据提供服务。 |
| `CLOSED` | 已关闭。 |

重连的退避从 100 毫秒到 10 秒，带抖动，并依次轮换端点。

## 快照

客户端把视图写入 `~/.cosky`（`snapshotDir`，starter 中为 `cosky.snapshot.dir`），重启后先从这里恢复，状态为 `DEGRADED`，直到连上 server。这对在同一块磁盘上重启的进程有用。在 Kubernetes 中需要持久卷。

快照没有加密，目录权限是 `0700`，文件权限是 `0600`。`snapshotIncludeConfig = false` 让 Config 内容不落盘，`snapshotDir = null` 关闭快照。

## TLS

```kotlin
CoSkyClientOptions(
    endpoints = listOf("cosky.internal:9090"),
    tls = true,
    tlsTrustedCertificates = Path.of("/etc/cosky/ca.pem"), // 用于私有 CA
)
```

详见[安全](./security.md#tls)。其他情况可以用 `channelFactory` 自己构建 gRPC 通道。

## 写入

客户端负责读取、注册和续约。要在代码中管理资源，请使用 `cosky-api` 中生成的 gRPC stub：

```kotlin
val resources = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel)
resources.put(putRequest { resource = /* ... */ })
```

## Java

`cosky-client` 暴露的是协程，在 Java 中不好用。Java 门面计划在 8.1 提供。在此之前，Java 应用请使用 Spring Boot starter，你的代码里不需要写 Kotlin。

## 其他语言

客户端没有任何特权，一切都走公开协议。proto 在 `api/src/main/proto`，`./gradlew :api:generateOpenApi` 把 OpenAPI 文档写到 `api/build/openapi/cosky.yaml`。符合规范的客户端要遵守[设计文档](https://github.com/Ahoo-Wang/CoSky/blob/main/docs/architecture-redesign.md)中关于 Watch 位置和租约续期的规定，`conformance-test` 中有 Kotlin 客户端通过的测试套件。
