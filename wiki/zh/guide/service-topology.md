---
title: "Service Topology"
---

# 服务拓扑

CoSky 的服务拓扑模块自动构建微服务生态系统的依赖关系图。当一个服务发现另一个服务的实例时，调用方身份（消费者）和目标方身份（生产者）会被记录在 Redis 中。这使您能够可视化哪些服务依赖于哪些服务、跟踪跨命名空间依赖关系以及监控服务级别的实例统计——所有这些都无需任何手动配置。

| 方面 | 详情 |
|---|---|
| **接口** | `ServiceTopology` |
| **Redis 实现** | `RedisServiceTopology` |
| **存储引擎** | Redis Hash（`topology_idx` + `topology:{consumer}`） |
| **消费者身份** | 构造时注入（注册的 serviceId，否则为 `spring.application.name`） |
| **并发模型** | 响应式（`Mono<Map<String, Set<String>>>`） |

## ServiceTopology 接口

[`ServiceTopology`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt) 接口定义了两个操作：

| 方法 | 返回类型 | 描述 | 源码 |
|---|---|---|---|
| `addTopology` | `Mono<Void>` | 记录本地服务依赖某个生产者服务 | [ServiceTopology.kt:26](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt#L26) |
| `getTopology` | `Mono<Map<String, Set<String>>>` | 获取命名空间的完整拓扑图 | [ServiceTopology.kt:31](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt#L31) |

该接口是纯粹的契约，不持有任何全局状态。

## 谁来记录拓扑

拓扑由 [`TopologyRecordingServiceDiscovery`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt) 记录，它是面向应用的 `ServiceDiscovery` 的装饰器：应用第一次查询某个服务的实例时，把该服务记录为生产者（[TopologyRecordingServiceDiscovery.kt:41](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt#L41)）。写入失败时会在下一次发现时重试。

只有面向应用的服务发现会被装饰。`cosky-rest-api` 关闭了它（`spring.cloud.cosky.discovery.topology-enabled: false`）并直接读取 Redis，因此在控制台浏览服务不会增加依赖关系。

## RedisServiceTopology

[`RedisServiceTopology`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt) 将拓扑数据存储在两个 Redis 哈希结构中：

### addTopology

`RedisServiceTopology` 在构造时以 `NamespacedServiceId` 的形式接收本地服务作为消费者（[RedisServiceTopology.kt:28](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt#L28)）。Spring 自动配置从 `spring.cloud.cosky.discovery.registry.service-id` 推导它，缺省时回退到 `spring.application.name`，因此在应用注册自身之前就已确定（[CoSkyDiscoveryAutoConfiguration.kt:78](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-discovery/src/main/kotlin/me/ahoo/cosky/discovery/spring/cloud/discovery/CoSkyDiscoveryAutoConfiguration.kt#L78)）。

`addTopology` 方法（[RedisServiceTopology.kt:36](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt#L36)）在消费者的命名空间中执行 `service_topology_add.lua`，该脚本：
1. 将消费者名称记录到拓扑索引中：`HSETNX {namespace}:topology_idx {consumerName} {timestamp}`
2. 记录生产者依赖：`HSETNX {namespace}:topology:{consumerName} {producerName} {timestamp}`

自依赖（消费者 == 生产者）会被跳过。

### getTopology

`getTopology` 方法 ([RedisServiceTopology.kt:59](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt#L59)) 执行 `service_topology_get.lua`，该脚本：
1. 通过 `HKEYS` 从 `{namespace}:topology_idx` 读取所有消费者名称
2. 对每个消费者，通过 `HKEYS` 从 `{namespace}:topology:{consumerName}` 读取其依赖集合
3. 返回 `Map<String, Set<String>>` 结果——将每个消费者映射到其生产者集合

## 拓扑构建过程

```mermaid
flowchart TD
    A[服务 A 发现服务 B 的实例] --> B{首次发现<br>服务 B？}
    B -->|否| Z[无需记录]
    B -->|是| C[consumerName = 注入的本地服务 ID]
    C --> E[producerName = producerServiceId，<br>跨命名空间时加命名空间前缀]
    E --> F{consumerName == producerName？}
    F -->|是| G[跳过 - 自依赖]
    F -->|否| H[EVAL service_topology_add.lua]
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

## 时序图：拓扑查询流程

```mermaid
sequenceDiagram
    autonumber
    participant Client as 仪表盘 / API
    participant RST as RedisServiceTopology
    participant Redis as Redis (Lua 脚本)

    Client->>RST: getTopology("production")
    RST->>Redis: EVAL service_topology_get.lua KEYS=["production"]
    Redis->>Redis: HKEYS production:topology_idx
    Redis-->>Redis: [gateway-svc, order-svc, user-svc]

    loop 对每个消费者
        Redis->>Redis: HKEYS production:topology:{consumer}
        Redis-->>Redis: 生产者名称列表
    end

    Redis-->>RST: [consumer1, [producers1], consumer2, [producers2], ...]
    RST->>RST: 解析为 Map~String, Set~String~~
    RST-->>Client: consumer 到 producer 集合的映射
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:59, cosky-discovery/src/main/resources/service_topology_get.lua -->

## 时序图：拓扑记录流程

```mermaid
sequenceDiagram
    autonumber
    participant SvcA as 服务 A（gateway-svc）
    participant TRD as TopologyRecordingServiceDiscovery
    participant RST as RedisServiceTopology
    participant Redis as Redis (Lua 脚本)
    participant Sub as PubSub

    SvcA->>TRD: getInstances("production", "order-svc")
    TRD->>RST: addTopology(producerNs="production", producerServiceId="order-svc")
    Note over RST: 构造时注入的消费者：<br>production / gateway-svc
    RST->>RST: producerName = "order-svc"
    RST->>RST: 检查自依赖（相同则跳过）
    RST->>Redis: EVAL service_topology_add.lua KEYS=["production"]
    Redis->>Redis: HSETNX production:topology_idx gateway-svc {timestamp}
    Redis->>Redis: HSETNX production:topology:gateway-svc order-svc {timestamp}
    Redis-->>RST: (void)
    RST-->>TRD: Mono~Void~（发出即不等待）
    TRD-->>SvcA: order-svc 的实例
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt:41, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:36, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:52 -->

## Redis 键模式

| Redis 键 | 类型 | 用途 | Lua 脚本 |
|---|---|---|---|
| `{namespace}:topology_idx` | HASH | 消费者服务名称到首次出现时间戳的映射 | `service_topology_add.lua` |
| `{namespace}:topology:{consumer}` | HASH | 指定消费者的生产者服务名称到首次出现时间戳的映射 | `service_topology_add.lua` |

跨命名空间的生产者名称格式为 `{producerNamespace}.{producerServiceId}`，由 [`RedisServiceTopology.getProducerName`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt#L52) 生成。这使得拓扑图能够区分不同命名空间中的同名服务。

## 服务统计集成

[`ServiceStat`](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStat.kt) 数据类与拓扑视图配合，显示每个服务的实例数量。`RedisServiceStatistic` 维护一个 `{namespace}:svc_stat` 哈希，其中每个条目将 `serviceId` 映射到其 `instanceCount`。

当实例事件发生时（不包括续约），统计会通过 Lua `service_stat.lua` 脚本自动重新计算。这种集成使仪表盘能够同时显示依赖图和拓扑中每个节点的健康/负载状态。

```mermaid
flowchart TD
    subgraph ProdNS["生产环境命名空间"]
        GW["gateway-svc<br>实例数: 3"]
        OS["order-svc<br>实例数: 5"]
        US["user-svc<br>实例数: 2"]
        PS["payment-svc<br>实例数: 4"]
    end

    GW -->|"依赖"| OS
    GW -->|"依赖"| US
    OS -->|"依赖"| PS
    OS -->|"依赖"| US

    style GW fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style OS fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style US fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style PS fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style ProdNS fill:#161b22,stroke:#30363d,color:#e6edf3
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStat.kt:20, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:28, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceStatistic.kt:97 -->

## 仪表盘集成

拓扑数据驱动 CoSky 仪表盘的服务拓扑可视化。仪表盘调用 `getTopology(namespace)` 获取完整依赖图，调用 `getServiceStats(namespace)` 显示每个服务的实例数量。这两者共同生成交互式图谱，显示：

- 带有实例数量的服务节点
- 表示调用依赖关系的有向边
- 跨命名空间关系（显示为 `{namespace}.{serviceId}`）

## 类图

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

    ServiceTopology <|.. RedisServiceTopology : 实现
    ServiceStatistic <|.. RedisServiceStatistic : 实现
    TopologyRecordingServiceDiscovery --> ServiceTopology : 记录生产者
    RedisServiceStatistic --> ServiceStat : 生成
```

<!-- Sources: cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt:22, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt:28, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStat.kt:20, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStatistic.kt:23, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceStatistic.kt:34, cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt:26 -->

## 相关页面

- [服务注册](./service-registry) -- 服务实例如何注册（提供拓扑数据）
- [服务发现](./service-discovery) -- 消费者如何发现实例
- [负载均衡](./load-balancers) -- 如何从已发现的服务中选择实例

## 参考文献

- [ServiceTopology.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceTopology.kt)
- [RedisServiceTopology.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceTopology.kt)
- [ServiceStat.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStat.kt)
- [ServiceStatistic.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceStatistic.kt)
- [RedisServiceStatistic.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/RedisServiceStatistic.kt)
- [TopologyRecordingServiceDiscovery.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/TopologyRecordingServiceDiscovery.kt)
- [service_topology_add.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/service_topology_add.lua)
- [service_topology_get.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/service_topology_get.lua)
- [service_stat.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/resources/service_stat.lua)
