---
title: Using the SDK
description: The Kotlin client without Spring, its snapshots and connection states, and clients for other languages.
---

# Using the SDK

`cosky-client` is a coroutine client with no dependency on Spring or Redis. The starter is built on it. It needs JDK 21 or later.

```kotlin
implementation("me.ahoo.cosky:cosky-client:8.0.0-alpha.2")
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

## Reading Configs

```kotlin
// Once, waiting for the first view.
val config: Resource? = prod.configs.load("order-service.yaml")

// Always current, from memory.
val current: StateFlow<Resource?> = prod.configs.watch("order-service.yaml")
current.collect { apply(it?.config?.content) }
```

`load` throws a `CoSkyException` when the server refuses the watch, such as for lack of permission or because the namespace does not exist, unless a snapshot knows better. The watch keeps trying, so a later `load` works once that changes.

## Finding instances

```kotlin
val instances: StateFlow<List<Resource>> = prod.instances.watch("payment")
val services: Set<String> = prod.instances.services()

val balancer = prod.loadBalancer("payment")
val chosen: Resource? = balancer.choose()
```

`choose()` picks at random in proportion to weight, allocates nothing and does not suspend. It returns `null` when no instance can be chosen.

## Registering

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

The client keeps the lease alive, and registers everything again if the lease is lost. Closing the last registration ends the lease.

## Closing

`client.close()` revokes its leases within 3 seconds. After that the server expires them.

## Connection states

`client.connectionState` is a `StateFlow`:

| State | Means |
|---|---|
| `CONNECTING` | No server has answered yet. |
| `SYNCED` | Following a server. |
| `DEGRADED` | Not connected: serving what is known, from memory or a snapshot. |
| `CLOSED` | Closed. |

Reconnecting backs off from 100 ms to 10 seconds with jitter, moving through the endpoints in turn.

## Snapshots

The client writes its views to `~/.cosky` (`snapshotDir`, or `cosky.snapshot.dir` with the starter) and starts from them after a restart, in `DEGRADED`, until it reaches a server. This helps a process restarting on the same disk. In Kubernetes it takes a persistent volume.

Snapshots are not encrypted. The directory is `0700` and the files `0600`. `snapshotIncludeConfig = false` keeps Config contents off the disk, and `snapshotDir = null` turns snapshots off.

## TLS

```kotlin
CoSkyClientOptions(
    endpoints = listOf("cosky.internal:9090"),
    tls = true,
    tlsTrustedCertificates = Path.of("/etc/cosky/ca.pem"), // for a private CA
)
```

See [Security](./security.md#tls). For anything else, `channelFactory` builds the gRPC channel yourself.

## Writing

The client reads, registers and renews. To manage resources from code, use the generated gRPC stubs in `cosky-api`:

```kotlin
val resources = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel)
resources.put(putRequest { resource = /* ... */ })
```

## Java

`cosky-client` exposes coroutines, which are awkward from Java. A Java facade is planned for 8.1. Until then, Java applications use the Spring Boot starter, which needs no Kotlin in your code.

## Other languages

Nothing in the client is privileged: everything goes through the public protocol. The protos are in `api/src/main/proto`, and `./gradlew :api:generateOpenApi` writes the OpenAPI document to `api/build/openapi/cosky.yaml`. A conforming client has to follow what the [design](https://github.com/Ahoo-Wang/CoSky/blob/main/docs/architecture-redesign.md) says about Watch positions and lease renewal, and `conformance-test` holds the suite the Kotlin client passes.
