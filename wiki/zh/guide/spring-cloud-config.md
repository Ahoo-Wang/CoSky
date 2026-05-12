---
title: "Spring Cloud Config Starter"
---

# Spring Cloud Config Starter

CoSky 的 Spring Cloud Config Starter 桥接了 CoSky 基于 Redis 的配置中心与 Spring Cloud Config 模型之间的差距。无需运行独立的配置服务器，服务在 Spring Boot 引导阶段直接从 Redis 获取配置，并且 Redis 中配置的任何变更都会在运行时自动推送到应用程序。这消除了对独立 config-server 部署的需求，同时保留了 Spring 的 `@RefreshScope` 和 `PropertySource` 抽象的完整功能。

## 一览

| 组件 | 职责 | 关键文件 | 源码 |
|---|---|---|---|
| **CoSkyConfigProperties** | 绑定 `spring.cloud.cosky.config.*` 属性 | `CoSkyConfigProperties.kt` | [cosky-spring-cloud-starter-config/.../CoSkyConfigProperties.kt:25](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigProperties.kt#L25) |
| **CoSkyConfigBootstrapConfiguration** | 引导入口；创建 `PropertySourceLocator` | `CoSkyConfigBootstrapConfiguration.kt` | [cosky-spring-cloud-starter-config/.../CoSkyConfigBootstrapConfiguration.kt:27](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigBootstrapConfiguration.kt#L27) |
| **CoSkyConfigAutoConfiguration** | 装配 `ConfigService`、事件监听器和刷新器 Bean | `CoSkyConfigAutoConfiguration.kt` | [cosky-spring-cloud-starter-config/.../CoSkyConfigAutoConfiguration.kt:43](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt#L43) |
| **CoSkyPropertySourceLocator** | 从 Redis 加载配置到 Spring `Environment` | `CoSkyPropertySourceLocator.kt` | [cosky-spring-cloud-starter-config/.../CoSkyPropertySourceLocator.kt:35](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyPropertySourceLocator.kt#L35) |
| **CoSkyConfigRefresher** | 监听配置变更并触发 Spring `RefreshEvent` | `CoSkyConfigRefresher.kt` | [cosky-spring-cloud-starter-config/.../refresh/CoSkyConfigRefresher.kt:33](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/refresh/CoSkyConfigRefresher.kt#L33) |
| **ConditionalOnCoSkyConfigEnabled** | 基于 `enabled` 属性的条件激活 | `ConditionalOnCoSkyConfigEnabled.kt` | [cosky-spring-cloud-starter-config/.../ConditionalOnCoSkyConfigEnabled.kt:29](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/ConditionalOnCoSkyConfigEnabled.kt#L29) |

## 配置属性

所有属性都在 `spring.cloud.cosky.config` 前缀下，由 [CoSkyConfigProperties.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigProperties.kt#L24) 绑定。

| 属性 | 默认值 | 描述 |
|---|---|---|
| `spring.cloud.cosky.config.enabled` | `true` | 完全启用或禁用 CoSky 配置启动器。 |
| `spring.cloud.cosky.config.config-id` | `${spring.application.name}.yaml` | 用于在 Redis 中查找配置的配置 ID。如果留空，则回退为 `{appName}.{fileExtension}`。 |
| `spring.cloud.cosky.config.file-extension` | `yaml` | 用于选择正确的 Spring `PropertySourceLoader` 的文件扩展名（如 `yaml`、`properties`）。 |
| `spring.cloud.cosky.config.timeout` | `2s` | 对基于 Redis 的 `ConfigService` 的阻塞调用超时时间。 |

## 自动配置链

配置启动器通过两阶段自动配置链激活。在**引导阶段**，首先加载 `CoSkyConfigBootstrapConfiguration`。它导入 `CoSkyConfigAutoConfiguration`，后者装配核心 Bean（`ConfigService`、`ConfigEventListenerContainer`、`CoSkyConfigRefresher`）。引导配置还注册了 Spring Cloud 用于定位外部属性的 `CoSkyPropertySourceLocator` Bean。

每个自动配置类都由 `@ConditionalOnCoSkyConfigEnabled` 注解（[ConditionalOnCoSkyConfigEnabled.kt:24](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/ConditionalOnCoSkyConfigEnabled.kt#L24)）保护，该注解检查 `spring.cloud.cosky.config.enabled` 属性，在未设置时默认为 `true`。

```mermaid
flowchart TB
    subgraph Bootstrap["引导阶段"]
        direction TB
        A["Spring Boot<br>引导上下文"] --> B["CoSkyConfigBootstrapConfiguration"]
    end

    subgraph AutoConfig["自动配置"]
        direction TB
        B -->|"@ImportAutoConfiguration"| C["CoSkyConfigAutoConfiguration"]
        C --> D["RedisConfigService"]
        C --> E["RedisConfigEventListenerContainer"]
        C --> F["RedisConsistencyConfigService"]
        C --> G["CoSkyConfigRefresher"]
    end

    subgraph PropertySourceLoading["属性源加载"]
        direction TB
        B --> H["CoSkyPropertySourceLocator"]
        H --> I["Spring Environment"]
    end

    style Bootstrap fill:#161b22,stroke:#30363d,color:#e6edf3
    style AutoConfig fill:#161b22,stroke:#30363d,color:#e6edf3
    style PropertySourceLoading fill:#161b22,stroke:#30363d,color:#e6edf3
    style A fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style B fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style C fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style D fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style E fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style F fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style G fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style H fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style I fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
```
<!-- Sources: cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigBootstrapConfiguration.kt:27, cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt:43 -->

## 配置加载流程

`CoSkyPropertySourceLocator` 实现了 Spring Cloud 的 `PropertySourceLocator` 接口（[CoSkyPropertySourceLocator.kt:38](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyPropertySourceLocator.kt#L38)）。在引导阶段，Spring 会对每个已注册的定位器调用 `locate(Environment)`。该定位器：

1. 解析**配置 ID** -- 如果 `configId` 为空，则默认为 `{appName}.{fileExtension}`，如 [CoSkyConfigAutoConfiguration.kt:48](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt#L48) 所计算。
2. 通过 `ConfigService.getConfig(namespace, configId)` 从 Redis 获取配置数据（[CoSkyPropertySourceLocator.kt:60](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyPropertySourceLocator.kt#L60)）。
3. 根据文件扩展名选择合适的 `PropertySourceLoader`（例如 `.yaml` 对应 `YamlPropertySourceLoader`）。
4. 将原始配置数据解析为 `PropertySource` 并添加到 Spring `Environment` 中。

```mermaid
sequenceDiagram
    autonumber
    participant Bootstrap as Spring 引导
    participant Locator as CoSkyPropertySourceLocator
    participant ConfigSvc as ConfigService
    participant Redis as Redis

    Bootstrap->>Locator: locate(Environment)
    Locator->>Locator: 解析 configId<br>({appName}.yaml)
    Locator->>Locator: 确定 fileExtension
    Locator->>ConfigSvc: getConfig(namespace, configId)
    ConfigSvc->>Redis: GET 配置数据
    Redis-->>ConfigSvc: 原始配置 YAML
    ConfigSvc-->>Locator: Config(data, configId)
    Locator->>Locator: 根据 fileExtension<br>选择 PropertySourceLoader
    Locator->>Locator: 将 YAML 解析为 PropertySource
    Locator-->>Bootstrap: OriginTrackedMapPropertySource
```
<!-- Sources: cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyPropertySourceLocator.kt:50, cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt:48 -->

## 配置刷新机制

应用程序运行后，Redis 中所做的配置变更会自动传播到应用程序。`CoSkyConfigRefresher`（[CoSkyConfigRefresher.kt:33](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/refresh/CoSkyConfigRefresher.kt#L33)）在 `ApplicationReadyEvent` 触发后订阅 `ConfigEventListenerContainer`。当配置变更事件到达时，它会发布 Spring `RefreshEvent`，从而触发所有 `@RefreshScope` Bean 的重新初始化。

```mermaid
sequenceDiagram
    autonumber
    participant User as 管理员 / API
    participant Redis as Redis
    participant Listener as ConfigEventListenerContainer
    participant Refresher as CoSkyConfigRefresher
    participant Context as ApplicationContext
    participant Beans as @RefreshScope Beans

    User->>Redis: 更新配置 (SET)
    Redis-->>Listener: 配置变更事件
    Listener-->>Refresher: ConfigEvent
    Refresher->>Refresher: 记录变更事件
    Refresher->>Context: publishEvent(RefreshEvent)
    Context->>Beans: 销毁并重新初始化
    Beans-->>Context: 使用新值刷新完成
```
<!-- Sources: cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/refresh/CoSkyConfigRefresher.kt:46, cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt:84 -->

刷新器在 [CoSkyConfigAutoConfiguration.kt:84](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt#L84) 中装配为 Bean。它使用 `AtomicBoolean` 保护（[CoSkyConfigRefresher.kt:39](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/refresh/CoSkyConfigRefresher.kt#L39)）确保订阅只建立一次，即使触发了多个 `ApplicationReadyEvent` 实例。

## 类图

```mermaid
classDiagram
    class CoSkyConfigProperties {
        +enabled: Boolean = true
        +configId: String?
        +fileExtension: String = "yaml"
        +timeout: Duration = 2s
        +PREFIX: String
    }

    class CoSkyConfigAutoConfiguration {
        +configEventListenerContainer() ConfigEventListenerContainer
        +redisConfigService() RedisConfigService
        +consistencyRedisConfigService() RedisConsistencyConfigService
        +coSkyConfigRefresher() CoSkyConfigRefresher
    }

    class CoSkyConfigBootstrapConfiguration {
        +coSkyPropertySourceLocator() CoSkyPropertySourceLocator
    }

    class CoSkyPropertySourceLocator {
        -configProperties: CoSkyConfigProperties
        -configService: ConfigService
        +locate(Environment) PropertySource
    }

    class CoSkyConfigRefresher {
        -coSkyProperties: CoSkyProperties
        -configProperties: CoSkyConfigProperties
        -configEventListenerContainer: ConfigEventListenerContainer
        +onApplicationEvent(ApplicationReadyEvent)
    }

    class ConditionalOnCoSkyConfigEnabled {
        +ENABLED_KEY: String
    }

    CoSkyConfigAutoConfiguration --> CoSkyConfigProperties : 使用
    CoSkyConfigBootstrapConfiguration --> CoSkyConfigAutoConfiguration : 导入
    CoSkyConfigBootstrapConfiguration --> CoSkyPropertySourceLocator : 创建
    CoSkyPropertySourceLocator --> CoSkyConfigProperties : 读取
    CoSkyPropertySourceLocator --> ConfigService : 获取配置
    CoSkyConfigAutoConfiguration --> CoSkyConfigRefresher : 创建
    CoSkyConfigRefresher --> ConfigEventListenerContainer : 订阅

    style CoSkyConfigProperties fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CoSkyConfigAutoConfiguration fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CoSkyConfigBootstrapConfiguration fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CoSkyPropertySourceLocator fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style CoSkyConfigRefresher fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
    style ConditionalOnCoSkyConfigEnabled fill:#2d333b,stroke:#6d5dfc,color:#e6edf3
```
<!-- Sources: cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigProperties.kt:25, cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt:43, cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigBootstrapConfiguration.kt:27, cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyPropertySourceLocator.kt:35, cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/refresh/CoSkyConfigRefresher.kt:33, cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/ConditionalOnCoSkyConfigEnabled.kt:29 -->

## YAML 配置示例

```yaml
spring:
  application:
    name: order-service
  cloud:
    cosky:
      namespace: production
      config:
        enabled: true
        config-id: order-service.yaml   # 可选；默认为 ${spring.application.name}.yaml
        file-extension: yaml            # yaml 或 properties
        timeout: 2s
```

使用以上配置，CoSky 将：

1. 在引导阶段，从 `production` 命名空间下的 Redis 中获取 `order-service.yaml` 配置。
2. 解析 YAML 并将其作为属性源注入到 Spring `Environment` 中。
3. 在运行时监听 `order-service.yaml` 的变更，并自动刷新 `@RefreshScope` Bean。

## 相关页面

- [Spring Cloud Discovery Starter](/guide/spring-cloud-discovery) -- 基于 Redis 的服务注册与发现
- [Configuration Center](/guide/config) -- CoSky 的配置管理 API 和 Redis 存储模型

## 参考

- [CoSkyConfigAutoConfiguration.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigAutoConfiguration.kt)
- [CoSkyConfigBootstrapConfiguration.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigBootstrapConfiguration.kt)
- [CoSkyPropertySourceLocator.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyPropertySourceLocator.kt)
- [CoSkyConfigProperties.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/CoSkyConfigProperties.kt)
- [CoSkyConfigRefresher.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/refresh/CoSkyConfigRefresher.kt)
- [ConditionalOnCoSkyConfigEnabled.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-spring-cloud-starter-config/src/main/kotlin/me/ahoo/cosky/config/spring/cloud/ConditionalOnCoSkyConfigEnabled.kt)
