---
title: "Service Topology"
---

# Service Topology

CoSky's Service Topology module automatically builds a dependency graph of your microservice ecosystem. When one service discovers the instances of another, the caller's identity (consumer) and the target's identity (producer) are recorded in Redis. This enables you to visualize which services depend on which, track cross-namespace dependencies, and monitor service-level instance statistics -- all without any manual configuration.

| Aspect | Detail |
|---|---|
| **Interface** | `ServiceTopology` |
| **Redis Implementation** | `RedisServiceTopology` |
| **Storage Engine** | Redis Hash (`topology_idx` + `topology:{consumer}`) |
| **Consumer Identity** | Injected at construction (registry service id, else `spring.application.name`) |
| **Concurrency Model** | Reactive (`Mono<Map<String, Set<String>>>`) |

## ServiceTopology Interface

The [`ServiceTopology`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt) interface defines two operations:

| Method | Return Type | Description | Source |
|---|---|---|---|
| `addTopology` | `Mono<Void>` | Records that the local service depends on a producer service | [ServiceTopology.kt:26](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt#L26) |
| `getTopology` | `Mono<Map<String, Set<String>>>` | Retrieves the full topology graph for a namespace | [ServiceTopology.kt:31](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt#L31) |

The interface is a pure contract: it holds no global state.

## Who Records Topology

Topology is recorded by [`TopologyRecordingServiceDiscovery`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt), a decorator around the application-facing `ServiceDiscovery`: the first time the application asks for the instances of a service, it records that service as a producer ([TopologyRecordingServiceDiscovery.kt:41](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt#L41)). A failed write is retried on the next discovery.

Only the application-facing discovery is decorated. `cosky-rest-api` disables it (`spring.cloud.cosky.discovery.topology-enabled: false`) and reads Redis directly, so browsing services in the dashboard does not add dependencies.

## RedisServiceTopology

[`RedisServiceTopology`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt) stores topology data in two Redis hash structures:

### addTopology

`RedisServiceTopology` takes the local service as a `NamespacedServiceId` consumer at construction ([RedisServiceTopology.kt:28](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt#L28)). The Spring auto-configuration derives it from `spring.cloud.cosky.discovery.registry.service-id`, falling back to `spring.application.name`, so it is known before the application registers itself ([CoSkyDiscoveryAutoConfiguration.kt:78](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-discovery/src/main/kotlin/me/ahoo/cosky/discovery/spring/cloud/discovery/CoSkyDiscoveryAutoConfiguration.kt#L78)).

The `addTopology` method ([RedisServiceTopology.kt:36](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt#L36)) executes `service_topology_add.lua` in the consumer's namespace, which:
1. Records the consumer name in the topology index: `HSETNX {namespace}:topology_idx {consumerName} {timestamp}`
2. Records the producer dependency: `HSETNX {namespace}:topology:{consumerName} {producerName} {timestamp}`

Self-dependencies (consumer == producer) are skipped.

### getTopology

The `getTopology` method ([RedisServiceTopology.kt:59](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt#L59)) executes `service_topology_get.lua`, which:
1. Reads all consumer names from `{namespace}:topology_idx` via `HKEYS`
2. For each consumer, reads their dependency set from `{namespace}:topology:{consumerName}` via `HKEYS`
3. Returns the result as `Map<String, Set<String>>` -- mapping each consumer to its set of producers

## Topology Building Process

```mermaid
flowchart TD
    A[Service A discovers Service B instances] --> B{First discovery<br>of Service B?}
    B -->|No| Z[Nothing to record]
    B -->|Yes| C[consumerName = injected local service id]
    C --> E[producerName = producerServiceId,<br>namespace-qualified if in another namespace]
    E --> F{consumerName == producerName?}
    F -->|Yes| G[Skip - self dependency]
    F -->|No| H[EVAL service_topology_add.lua]
    H --> I[HSETNX topology_idx<br>consumer -> timestamp]
    H --> J[HSETNX topology:consumer<br>producer -> timestamp]

    style A fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style B fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style Z fill:#161b22,stroke:#30363d,color:#e6edf3
    style F fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style G fill:#161b22,stroke:#30363d,color:#e6edf3
    style H fill:#161b22,stroke:#30363d,color:#e6edf3
    style I fill:#161b22,stroke:#30363d,color:#e6edf3
    style J fill:#161b22,stroke:#30363d,color:#e6edf3
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt:41, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:36, cosky-discovery/src/main/resources/service_topology_add.lua -->

## Sequence Diagram: Topology Query Flow

```mermaid
sequenceDiagram
    autonumber
    participant Client as Dashboard / API
    participant RST as RedisServiceTopology
    participant Redis as Redis (Lua Script)

    Client->>RST: getTopology("production")
    RST->>Redis: EVAL service_topology_get.lua KEYS=["production"]
    Redis->>Redis: HKEYS production:topology_idx
    Redis-->>Redis: [gateway-svc, order-svc, user-svc]

    loop For each consumer
        Redis->>Redis: HKEYS production:topology:{consumer}
        Redis-->>Redis: List of producer names
    end

    Redis-->>RST: [consumer1, [producers1], consumer2, [producers2], ...]
    RST->>RST: parse into Map~String, Set~String~~
    RST-->>Client: Map of consumer -> set of producers
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:59, cosky-discovery/src/main/resources/service_topology_get.lua -->

## Sequence Diagram: Topology Recording Flow

```mermaid
sequenceDiagram
    autonumber
    participant SvcA as Service A (gateway-svc)
    participant TRD as TopologyRecordingServiceDiscovery
    participant RST as RedisServiceTopology
    participant Redis as Redis (Lua Script)
    participant Sub as PubSub

    SvcA->>TRD: getInstances("production", "order-svc")
    TRD->>RST: addTopology(producerNs="production", producerServiceId="order-svc")
    Note over RST: consumer injected at construction:<br>production / gateway-svc
    RST->>RST: producerName = "order-svc"
    RST->>RST: Check self-dependency (skip if same)
    RST->>Redis: EVAL service_topology_add.lua KEYS=["production"]
    Redis->>Redis: HSETNX production:topology_idx gateway-svc {timestamp}
    Redis->>Redis: HSETNX production:topology:gateway-svc order-svc {timestamp}
    Redis-->>RST: (void)
    RST-->>TRD: Mono~Void~ (fire and forget)
    TRD-->>SvcA: instances of order-svc
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt:41, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:36, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:52 -->

## Redis Key Patterns

| Redis Key | Type | Purpose | Lua Script |
|---|---|---|---|
| `{namespace}:topology_idx` | HASH | Maps consumer service names to first-seen timestamps | `service_topology_add.lua` |
| `{namespace}:topology:{consumer}` | HASH | Maps producer service names to first-seen timestamps for a given consumer | `service_topology_add.lua` |

Cross-namespace producer names are formatted as `{producerNamespace}.{producerServiceId}` by [`RedisServiceTopology.getProducerName`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt#L52). This allows the topology graph to distinguish between same-name services in different namespaces.

## Service Stat Integration

The [`ServiceStat`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStat.kt) data class pairs with the topology view to show per-service instance counts. The `RedisServiceStatistic` maintains a `{namespace}:svc_stat` hash where each entry maps a `serviceId` to its `instanceCount`.

Statistics are automatically recalculated when instance events occur (excluding renewals), driven by the Lua `service_stat.lua` script. This integration allows the dashboard to display both the dependency graph and the health/load of each node in the topology.

```mermaid
flowchart TD
    subgraph ProdNS["Production Namespace"]
        GW["gateway-svc<br>instances: 3"]
        OS["order-svc<br>instances: 5"]
        US["user-svc<br>instances: 2"]
        PS["payment-svc<br>instances: 4"]
    end

    GW -->|"depends on"| OS
    GW -->|"depends on"| US
    OS -->|"depends on"| PS
    OS -->|"depends on"| US

    style GW fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style OS fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style US fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style PS fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style ProdNS fill:#161b22,stroke:#30363d,color:#e6edf3
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStat.kt:20, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:28, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceStatistic.kt:97 -->

## Dashboard Integration

The topology data powers the CoSky dashboard's service topology visualization. The dashboard calls `getTopology(namespace)` to retrieve the full dependency graph and `getServiceStats(namespace)` to display instance counts per service. Together, these produce an interactive graph showing:

- Service nodes with their instance counts
- Directed edges representing call dependencies
- Cross-namespace relationships (shown as `{namespace}.{serviceId}`)

## Class Diagram

```mermaid
classDiagram
    class ServiceTopology {
        <<interface>>
        +addTopology(producerNamespace, producerServiceId) Mono~Void~
        +getTopology(namespace) Mono~Map~
    }
    class RedisServiceTopology {
        -redisTemplate: ReactiveStringRedisTemplate
        -consumer: NamespacedServiceId
        +addTopology(producerNamespace, producerServiceId) Mono~Void~
        +getTopology(namespace) Mono~Map~
        -getProducerName(ns, serviceId) String
    }
    class TopologyRecordingServiceDiscovery {
        -delegate: ServiceDiscovery
        -serviceTopology: ServiceTopology
        +getInstances(namespace, serviceId) Mono~List~
    }
    class ServiceStat {
        +serviceId: String
        +instanceCount: Int
    }
    class ServiceStatistic {
        <<interface>>
        +statService(namespace) Mono~Void~
        +statService(namespace, serviceId) Mono~Void~
        +getServiceStats(namespace) Flux~ServiceStat~
        +getInstanceCount(namespace) Mono~Long~
    }
    class RedisServiceStatistic {
        -redisTemplate: ReactiveStringRedisTemplate
        -instanceEventListenerContainer
        +statService(namespace) Mono~Void~
        +getServiceStats(namespace) Flux~ServiceStat~
        +getInstanceCount(namespace) Mono~Long~
    }

    ServiceTopology <|.. RedisServiceTopology : implements
    ServiceStatistic <|.. RedisServiceStatistic : implements
    TopologyRecordingServiceDiscovery --> ServiceTopology : records producers
    RedisServiceStatistic --> ServiceStat : produces
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt:22, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:28, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStat.kt:20, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStatistic.kt:23, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceStatistic.kt:34, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt:26 -->

## Related Pages

- [Service Registry](./service-registry) -- How service instances are registered (which feeds topology data)
- [Service Discovery](./service-discovery) -- How instances are discovered by consumers
- [Load Balancers](./load-balancers) -- How instances are selected from discovered services

## References

- [ServiceTopology.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt)
- [RedisServiceTopology.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt)
- [ServiceStat.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStat.kt)
- [ServiceStatistic.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStatistic.kt)
- [RedisServiceStatistic.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceStatistic.kt)
- [TopologyRecordingServiceDiscovery.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt)
- [service_topology_add.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/service_topology_add.lua)
- [service_topology_get.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/service_topology_get.lua)
- [service_stat.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/service_stat.lua)
