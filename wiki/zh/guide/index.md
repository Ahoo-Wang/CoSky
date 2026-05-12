---
title: CoSky Guide
description: Explore CoSky's architecture, features, and documentation for high-performance microservice governance.
---

# CoSky 指南

**CoSky** 是一个轻量级、低成本的服务注册、服务发现和配置服务 SDK。通过利用现有基础设施中的 Redis（您很可能已经部署了它），CoSky 消除了额外的运维成本和部署负担。凭借 Redis 的高性能，CoSky 提供了卓越的 TPS 和 QPS（100,000+/s）。通过结合本地进程缓存策略和 Redis PubSub，CoSky 实现了实时缓存刷新，具有出色的 QPS 性能（70,000,000+/s），并保持进程缓存与 Redis 之间的实时一致性。

## 核心特性

| 特性 | 说明 | 源码 |
|---------|-------------|--------|
| **服务发现** | 通过客户端心跳自动续约，注册、发现和管理服务实例。支持加权负载均衡和服务拓扑。 | [ServiceRegistry.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L24), [ServiceDiscovery.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceDiscovery.kt#L24) |
| **配置管理** | 支持版本历史（最近 10 个版本）、回滚和文件导入/导出的动态配置。变更通过 Redis PubSub 即时传播。 | [ConfigService.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigService.kt#L24), [ConfigRollback.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigRollback.kt#L24) |
| **一致性缓存** | 通过 Redis PubSub 保持同步的本地进程缓存，相比直接读取 Redis 实现 250 倍至 800 倍的性能提升。 | [RedisConsistencyConfigService](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/redis/RedisConsistencyConfigService.kt), [ConsistencyRedisServiceDiscovery](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/ConsistencyRedisServiceDiscovery.kt) |
| **Spring Cloud 集成** | 配置和发现的即用型 Starter。通过 Spring Boot `@AutoConfiguration` 自动配置。 | [CoSkyConfigAutoConfiguration.kt:43](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt#L43), [CoSkyDiscoveryAutoConfiguration.kt:47](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-discovery/src/main/kotlin/me/ahoo/cosky/discovery/spring/cloud/discovery/CoSkyDiscoveryAutoConfiguration.kt#L47) |
| **加权负载均衡** | 与服务发现集成的二进制权重随机负载均衡器，用于高效的实例选择。 | [CoSkyDiscoveryAutoConfiguration.kt:105](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-discovery/src/main/kotlin/me/ahoo/cosky/discovery/spring/cloud/discovery/CoSkyDiscoveryAutoConfiguration.kt#L105) |
| **命名空间隔离** | 支持多租户命名空间，通过 Redis hashtag 包装实现集群模式兼容。 | [CoSkyProperties.kt:32](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-core/src/main/kotlin/me/ahoo/cosky/spring/cloud/CoSkyProperties.kt#L32), [NamespaceService.kt:23](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/NamespaceService.kt#L23) |
| **REST API 与控制台** | 基于 Web 的管理 UI，支持 RBAC、审计日志和服务拓扑可视化。 | [RestApiServer.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/RestApiServer.kt#L24) |
| **实时事件** | 配置和实例变更事件通过 Redis PubSub 监听器即时传播。 | [ConfigChangedEvent.kt:20](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigChangedEvent.kt#L20), [EventListenerContainer.kt:5](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/EventListenerContainer.kt#L5) |

## 架构概览

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
graph TB
    subgraph Applications["应用程序"]
        style Applications fill:#161b22,stroke:#30363d,color:#e6edf3
        A["Spring Boot 应用 1"] --> |使用| SC_C["spring-cloud-starter-cosky-config"]
        A --> |使用| SC_D["spring-cloud-starter-cosky-discovery"]
        B["Spring Boot 应用 2"] --> |使用| SC_C
        B --> |使用| SC_D
    end

    subgraph Starters["Spring Cloud Starter"]
        style Starters fill:#161b22,stroke:#30363d,color:#e6edf3
        SC_C --> CS["cosky-config"]
        SC_D --> CD["cosky-discovery"]
    end

    subgraph Core["cosky-core"]
        style Core fill:#161b22,stroke:#30363d,color:#e6edf3
        CS --> CK["cosky-core<br>(命名空间, 事件, Redis 工具)"]
        CD --> CK
    end

    subgraph Server["REST API 服务器（可选）"]
        style Server fill:#161b22,stroke:#30363d,color:#e6edf3
        RA["cosky-rest-api"] --> |使用| CS
        RA --> |使用| CD
        DASH["控制台 (Web UI)"] --> RA
        DASH --> |RBAC, 审计| SEC["安全 (CoSec)"]
    end

    CK --> REDIS[("Redis<br>(存储 + PubSub)")]

    style REDIS fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style SC_C fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style SC_D fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CS fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CD fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CK fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style RA fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style DASH fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style SEC fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style A fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style B fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
```

<!-- Sources: settings.gradle.kts:14-27, build.gradle.kts:32-43, RestApiServer.kt:24 -->

### 模块依赖图

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
graph LR
    subgraph Modules["CoSky 模块"]
        style Modules fill:#161b22,stroke:#30363d,color:#e6edf3
        CORE["cosky-core"] --> CONFIG["cosky-config"]
        CORE --> DISCOVERY["cosky-discovery"]
        CONFIG --> SCC["spring-cloud-starter-cosky-config"]
        DISCOVERY --> SCD["spring-cloud-starter-cosky-discovery"]
        SCC --> REST["cosky-rest-api"]
        SCD --> REST
        SC_CORE["cosky-spring-cloud-core"] --> SCC
        SC_CORE --> SCD
        BOM["cosky-bom<br>(物料清单)"] -.-> SCC
        BOM -.-> SCD
        DEPS["cosky-dependencies<br>(版本目录)"] -.-> CORE
        DEPS -.-> CONFIG
        DEPS -.-> DISCOVERY
    end

    style CORE fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CONFIG fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style DISCOVERY fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style SCC fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style SCD fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style REST fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style SC_CORE fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style BOM fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style DEPS fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
```

<!-- Sources: settings.gradle.kts:14-27, build.gradle.kts:32-43 -->

### 数据流：服务发现

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
sequenceDiagram
    autonumber
    participant App as Spring Boot 应用
    participant Reg as CoSkyServiceRegistry
    participant SR as ServiceRegistry
    participant Redis as Redis
    participant PubSub as Redis PubSub
    participant Discovery as ServiceDiscovery
    participant Cache as 本地缓存

    App->>Reg: register(instance)
    Reg->>SR: register(serviceInstance)
    SR->>Redis: Lua 脚本: 注册实例
    SR->>PubSub: PUBLISH 实例变更事件
    Reg->>SR: 启动续约调度器

    Note over App,Cache: 从缓存读取发现数据

    App->>Discovery: getInstances(serviceId)
    Discovery->>Cache: 读取本地缓存
    Cache-->>App: 返回实例列表

    PubSub->>Cache: 使过期条目失效
    Cache->>Redis: 从源刷新
```

<!-- Sources: CoSkyServiceRegistry.kt:30, ServiceRegistry.kt:33, ServiceDiscovery.kt:26, CoSkyDiscoveryAutoConfiguration.kt:82 -->

### 数据流：配置

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
sequenceDiagram
    autonumber
    participant App as Spring Boot 应用
    participant PSL as CoSkyPropertySourceLocator
    participant CS as ConfigService
    participant Redis as Redis
    participant Refresher as CoSkyConfigRefresher
    participant Ctx as ApplicationContext

    Note over App,Ctx: 应用启动阶段
    App->>PSL: locate(environment)
    PSL->>CS: getConfig(namespace, configId)
    CS->>Redis: GET 配置数据
    Redis-->>CS: 配置 JSON
    CS-->>PSL: Config 对象
    PSL-->>App: PropertySource

    Note over App,Ctx: 运行时配置刷新
    Redis-->>Refresher: PUBLISH 配置变更事件
    Refresher->>Ctx: publishEvent(RefreshEvent)
    Ctx->>App: 刷新 @ConfigurationProperties
```

<!-- Sources: CoSkyPropertySourceLocator.kt:35, ConfigService.kt:27, CoSkyConfigRefresher.kt:33 -->

### 实例生命周期

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
stateDiagram-v2
    [*] --> Registering: 应用启动
    Registering --> Registered: register() 成功
    Registered --> Renewing: 周期性心跳<br>(每 10 秒)
    Renewing --> Registered: renew() 成功
    Registered --> Deregistering: 应用关闭
    Deregistering --> [*]: deregister()
    Renewing --> Expired: TTL 超时<br>(默认 60 秒)
    Expired --> [*]: 自动移除
```

<!-- Sources: RegistryProperties.kt:23, RenewProperties.kt:22, ServiceInstance.kt:33, CoSkyServiceRegistry.kt:30 -->

## 文档导航

| 页面 | 说明 |
|------|-------------|
| [快速入门](./getting-started) | 快速入门指南：5 分钟内搭建使用 CoSky 的 Spring Boot 应用 |
| [安装](./installation) | 所有安装方式：Maven、Gradle、Docker、Kubernetes |
| [架构](./architecture) | 详细架构、模块结构、Redis 键设计和事件系统 |
| [配置服务](./config-service) | 配置 CRUD、版本管理、回滚、导入/导出 |
| [配置一致性](./config-consistency) | 本地缓存 + PubSub 失效层 |
| [服务注册](./service-registry) | 实例注册、心跳、注销 |
| [Spring Cloud 配置](./spring-cloud-config) | PropertySourceLocator、`@RefreshScope`、自动配置 |
| [REST API 服务器](./rest-api) | 所有操作的 HTTP 端点、控制台、安全 |

## 相关页面

- [CoSky 首页](../index.md) -- 项目概览
- [CoSky GitHub 仓库](https://github.com/Ahoo-Wang/CoSky) -- 源代码、问题和发布版本
- [示例](https://github.com/Ahoo-Wang/CoSky/tree/main/examples) -- 服务提供者和消费者示例，集成 Feign RPC
- [REST API 文档](https://ahoo-cosky.apifox.cn/) -- 交互式 API 文档（Apifox）
