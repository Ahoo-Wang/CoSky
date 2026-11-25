---
title: 使用 SDK
description: 不依赖 Spring 使用 Kotlin 客户端，了解快照和连接状态，以及其他语言的客户端。
---

# 使用 SDK

`cosky-client` 是一个协程客户端，不依赖 Spring 或 Redis，starter 就构建在它之上。需要 JDK 21 或更高版本。

```kotlin
implementation("me.ahoo.cosky:cosky-client:8.0.0-rc.1")
```

```kotlin
val client = CoSkyClient(
    CoSkyClientOptions(listOf("cosky-0.internal:9090", "cosky-1.internal:9090")) {
        token = System.getenv("COSKY_TOKEN")
        clientName = "order-service"
    },
)
val prod = client.namespace("prod")
```

`COSKY_TOKEN` 是应用自己的 token：[安全](./security.md#给应用的-token)中说明了怎么创建。`tokenFile` 改从文件读取，文件变化后重新读，在磁盘上轮换 token 不用重启。除端点以外的设置都写在代码块里，`CoSkyClientOptions.Builder` 列出了全部设置和默认值。

## 读取 Config

```kotlin
// 读一次，等待第一个视图。
val config: Resource? = prod.configs.load("order-service.yaml")

// 始终最新，从内存读取。
val current: StateFlow<Resource?> = prod.configs.watch("order-service.yaml")
current.collect { apply(it?.config?.content) }
```

server 拒绝 watch 时，比如没有权限或 namespace 不存在，`load` 会抛出 `CoSkyException`，除非快照中已有数据。watch 会继续重试，情况变化后再次 `load` 就能成功。

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

同一个名字再次注册会替换原来的注册，由新的句柄关闭它。再次关闭一个句柄，或关闭名字已被再次注册的旧句柄，都不做任何事。关闭失败时（例如没有 server 应答），实例仍然注册并续约，可以再试一次。

## 错误

读取（如 `load`）抛出 `CoSkyException`，`reason` 说明原因。`register` 和 `close` 是写入，抛出 gRPC 的 `StatusException`，带着 server 返回的状态，例如没有有效 token 时是 `UNAUTHENTICATED`，没有允许注册该服务的角色时是 `PERMISSION_DENIED`。没有 server 应答时注册不会失败：等有 server 应答后再发出。server 给出了原因时，`CoSkyException.from(e)` 把它作为 `CoSkyException` 返回，否则返回 `null`：

```kotlin
try {
    prod.instances.register(service = "order-service", endpoint = endpoint)
} catch (e: StatusException) {
    if (CoSkyException.from(e)?.reason != ErrorReason.ERROR_REASON_PERMISSION_DENIED) throw e
    log.error("The token's account may not register order-service.")
}
```

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

重连的退避从 100 毫秒到 10 秒，带抖动，并依次轮换端点。每次重连都新建连接：连到下一个端点；只有一个端点时，比如 Kubernetes Service，再连到它一次。不能服务的副本，比如落后或与 Redis 断开的副本，会结束流让客户端换个地方，而只有新连接才可能到达另一个副本。

## 快照

客户端把视图写入 `~/.cosky`（`snapshotDir`，starter 中为 `cosky.snapshot.dir`），重启后先从这里恢复，状态为 `DEGRADED`，直到连上 server。这对在同一块磁盘上重启的进程有用。在 Kubernetes 中需要持久卷。

快照没有加密，目录权限是 `0700`，文件权限是 `0600`；没有 POSIX 权限的文件系统（如 Windows）沿用所在目录的权限，在用户主目录下即只有该用户可读写。`snapshotIncludeConfig = false` 让 Config 内容不落盘，`snapshotDir = null` 关闭快照。

## TLS

```kotlin
CoSkyClientOptions(listOf("cosky.internal:9090")) {
    tls = true
    tlsTrustedCertificates = Path.of("/etc/cosky/ca.pem") // 用于私有 CA
}
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
