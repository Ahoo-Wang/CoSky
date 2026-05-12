---
title: CoSky Guide
description: Explore CoSky's architecture, features, and documentation for high-performance microservice governance.
---

# CoSky Guide

**CoSky** is a lightweight, low-cost service registration, service discovery, and configuration service SDK. By leveraging Redis in your existing infrastructure (which you've likely already deployed), CoSky eliminates additional operational costs and deployment burdens. Powered by Redis's high performance, CoSky delivers exceptional TPS and QPS (100,000+/s). Through its combination of local process caching strategies and Redis PubSub, CoSky achieves real-time cache refreshing with outstanding QPS performance (70,000,000+/s) and maintains real-time consistency between process cache and Redis.

## Key Features

| Feature | Description | Source |
|---------|-------------|--------|
| **Service Discovery** | Register, discover, and manage service instances with automatic renewal via client heartbeat. Supports weighted load balancing and service topology. | [ServiceRegistry.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceRegistry.kt#L24), [ServiceDiscovery.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/ServiceDiscovery.kt#L24) |
| **Configuration Management** | Dynamic configuration with version history (last 10 versions), rollback support, and file import/export. Changes propagate instantly via Redis PubSub. | [ConfigService.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigService.kt#L24), [ConfigRollback.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigRollback.kt#L24) |
| **Consistency Caching** | Local process cache kept in sync via Redis PubSub, achieving 250x-800x performance improvement over direct Redis reads. | [RedisConsistencyConfigService](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/redis/RedisConsistencyConfigService.kt), [ConsistencyRedisServiceDiscovery](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-discovery/src/main/kotlin/me/ahoo/cosky/discovery/redis/ConsistencyRedisServiceDiscovery.kt) |
| **Spring Cloud Integration** | Drop-in starters for both config and discovery. Auto-configures via Spring Boot `@AutoConfiguration`. | [CoSkyConfigAutoConfiguration.kt:43](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt#L43), [CoSkyDiscoveryAutoConfiguration.kt:47](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-discovery/src/main/kotlin/me/ahoo/cosky/discovery/spring/cloud/discovery/CoSkyDiscoveryAutoConfiguration.kt#L47) |
| **Weighted Load Balancing** | Binary weight random load balancer integrated with service discovery for efficient instance selection. | [CoSkyDiscoveryAutoConfiguration.kt:105](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-discovery/src/main/kotlin/me/ahoo/cosky/discovery/spring/cloud/discovery/CoSkyDiscoveryAutoConfiguration.kt#L105) |
| **Namespace Isolation** | Multi-tenant namespace support with Redis hashtag wrapping for cluster mode compatibility. | [CoSkyProperties.kt:32](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-core/src/main/kotlin/me/ahoo/cosky/spring/cloud/CoSkyProperties.kt#L32), [NamespaceService.kt:23](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/NamespaceService.kt#L23) |
| **REST API & Dashboard** | Web-based management UI with RBAC, audit logging, and service topology visualization. | [RestApiServer.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/RestApiServer.kt#L24) |
| **Real-time Events** | Config and instance change events propagated instantly via Redis PubSub listeners. | [ConfigChangedEvent.kt:20](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-config/src/main/kotlin/me/ahoo/cosky/config/ConfigChangedEvent.kt#L20), [EventListenerContainer.kt:5](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-core/src/main/kotlin/me/ahoo/cosky/core/EventListenerContainer.kt#L5) |

## Architecture Overview

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
graph TB
    subgraph Applications["Applications"]
        style Applications fill:#161b22,stroke:#30363d,color:#e6edf3
        A["Spring Boot App 1"] --> |uses| SC_C["spring-cloud-starter-cosky-config"]
        A --> |uses| SC_D["spring-cloud-starter-cosky-discovery"]
        B["Spring Boot App 2"] --> |uses| SC_C
        B --> |uses| SC_D
    end

    subgraph Starters["Spring Cloud Starters"]
        style Starters fill:#161b22,stroke:#30363d,color:#e6edf3
        SC_C --> CS["cosky-config"]
        SC_D --> CD["cosky-discovery"]
    end

    subgraph Core["cosky-core"]
        style Core fill:#161b22,stroke:#30363d,color:#e6edf3
        CS --> CK["cosky-core<br>(Namespaces, Events, Redis Utils)"]
        CD --> CK
    end

    subgraph Server["REST API Server (Optional)"]
        style Server fill:#161b22,stroke:#30363d,color:#e6edf3
        RA["cosky-rest-api"] --> |uses| CS
        RA --> |uses| CD
        DASH["Dashboard (Web UI)"] --> RA
        DASH --> |RBAC, Audit| SEC["Security (CoSec)"]
    end

    CK --> REDIS[("Redis<br>(Storage + PubSub)")]

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

### Module Dependency Graph

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
graph LR
    subgraph Modules["CoSky Modules"]
        style Modules fill:#161b22,stroke:#30363d,color:#e6edf3
        CORE["cosky-core"] --> CONFIG["cosky-config"]
        CORE --> DISCOVERY["cosky-discovery"]
        CONFIG --> SCC["spring-cloud-starter-cosky-config"]
        DISCOVERY --> SCD["spring-cloud-starter-cosky-discovery"]
        SCC --> REST["cosky-rest-api"]
        SCD --> REST
        SC_CORE["cosky-spring-cloud-core"] --> SCC
        SC_CORE --> SCD
        BOM["cosky-bom<br>(Bill of Materials)"] -.-> SCC
        BOM -.-> SCD
        DEPS["cosky-dependencies<br>(Version Catalog)"] -.-> CORE
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

### Data Flow: Service Discovery

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
sequenceDiagram
    autonumber
    participant App as Spring Boot App
    participant Reg as CoSkyServiceRegistry
    participant SR as ServiceRegistry
    participant Redis as Redis
    participant PubSub as Redis PubSub
    participant Discovery as ServiceDiscovery
    participant Cache as Local Cache

    App->>Reg: register(instance)
    Reg->>SR: register(serviceInstance)
    SR->>Redis: Lua script: register instance
    SR->>PubSub: PUBLISH instance change event
    Reg->>SR: start renew scheduler

    Note over App,Cache: Discovery reads from cache

    App->>Discovery: getInstances(serviceId)
    Discovery->>Cache: read local cache
    Cache-->>App: return instances

    PubSub->>Cache: invalidate stale entries
    Cache->>Redis: refresh from source
```

<!-- Sources: CoSkyServiceRegistry.kt:30, ServiceRegistry.kt:33, ServiceDiscovery.kt:26, CoSkyDiscoveryAutoConfiguration.kt:82 -->

### Data Flow: Configuration

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
sequenceDiagram
    autonumber
    participant App as Spring Boot App
    participant PSL as CoSkyPropertySourceLocator
    participant CS as ConfigService
    participant Redis as Redis
    participant Refresher as CoSkyConfigRefresher
    participant Ctx as ApplicationContext

    Note over App,Ctx: Application Bootstrap
    App->>PSL: locate(environment)
    PSL->>CS: getConfig(namespace, configId)
    CS->>Redis: GET config data
    Redis-->>CS: config JSON
    CS-->>PSL: Config object
    PSL-->>App: PropertySource

    Note over App,Ctx: Runtime Config Refresh
    Redis-->>Refresher: PUBLISH config change event
    Refresher->>Ctx: publishEvent(RefreshEvent)
    Ctx->>App: refresh @ConfigurationProperties
```

<!-- Sources: CoSkyPropertySourceLocator.kt:35, ConfigService.kt:27, CoSkyConfigRefresher.kt:33 -->

### Instance Lifecycle

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor':'#2d333b','primaryBorderColor':'#6d5dfc','primaryTextColor':'#e6edf3','lineColor':'#8b949e','secondaryColor':'#161b22','tertiaryColor':'#161b22'}}}%%
stateDiagram-v2
    [*] --> Registering: Application starts
    Registering --> Registered: register() success
    Registered --> Renewing: periodic heartbeat<br>(every 10s)
    Renewing --> Registered: renew() success
    Registered --> Deregistering: Application shuts down
    Deregistering --> [*]: deregister()
    Renewing --> Expired: TTL exceeded<br>(default 60s)
    Expired --> [*]: auto-removed
```

<!-- Sources: RegistryProperties.kt:23, RenewProperties.kt:22, ServiceInstance.kt:33, CoSkyServiceRegistry.kt:30 -->

## Documentation Map

| Page | Description |
|------|-------------|
| [Getting Started](./getting-started) | Quick start guide: set up a Spring Boot app with CoSky in 5 minutes |
| [Installation](./installation) | All installation methods: Maven, Gradle, Docker, Kubernetes |
| [Architecture](./architecture) | Detailed architecture, module structure, Redis key design, and event system |
| [Configuration Service](./config-service) | Configuration CRUD, versioning, rollback, import/export |
| [Config Consistency](./config-consistency) | Local caching + PubSub invalidation layer |
| [Service Registry](./service-registry) | Instance registration, heartbeat, deregistration |
| [Spring Cloud Config](./spring-cloud-config) | PropertySourceLocator, `@RefreshScope`, auto-configuration |
| [REST API Server](./rest-api) | HTTP endpoints for all operations, dashboard, security |

## Related Pages

- [CoSky Home](../index.md) -- Landing page with project overview
- [CoSky GitHub Repository](https://github.com/Ahoo-Wang/CoSky) -- Source code, issues, and releases
- [Examples](https://github.com/Ahoo-Wang/CoSky/tree/main/examples) -- Service provider and consumer examples with Feign RPC integration
- [REST API Documentation](https://ahoo-cosky.apifox.cn/) -- Interactive API documentation (Apifox)
