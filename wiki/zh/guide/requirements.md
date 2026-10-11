---
title: 需求
description: CoSky 满足的功能需求与非功能需求，每条都对应验证它的测试。
---

# 需求

本页列出 CoSky 必须做到的事情，每条需求有固定编号，可在 issue 和 PR 中引用。每条需求都注明验证它的测试，破坏需求的改动会让构建失败。新需求通过功能请求提出，连同测试一起加入本页。

## 范围

CoSky 为微服务提供配置管理与服务发现，只依赖 Redis。它以 Spring Cloud starter 和 SDK 的形式作为库使用，也可以通过 REST API 服务器及其控制台进行运维。

## 功能需求

### 配置

| 编号 | 需求 | 验证 |
|---|---|---|
| FR-C1 | 配置按命名空间和配置 ID 存储，支持创建、更新、读取和删除。 | `RedisConfigServiceTest`、`RestApiIntegrationTest.configLifecycle` |
| FR-C2 | 每次变更产生新版本；保留最近 10 个版本，可回滚到其中任意版本，包括已删除的配置。 | `RedisConfigServiceTest`、`RedisConsistencyConfigServiceTest.rollback` |
| FR-C3 | 应用无需轮询即可收到配置变更，首次读取后紧接着发生的变更也不会丢失。 | `RedisConsistencyConfigServiceTest`、`CoSkyConfigRefresherTest` |
| FR-C4 | Spring Cloud 应用启动时加载配置（支持多文档 YAML），并在变更时刷新。 | `CoSkyPropertySourceLocatorTest`、`CoSkyConfigRefresherTest` |
| FR-C5 | 配置可导出为 zip，也可从 zip 导入（跳过或覆盖已有配置），并有大小限制。 | `ConfigControllerImportTest`、`ZipsTest`、`RestApiIntegrationTest.configLifecycle` |

### 服务发现

| 编号 | 需求 | 验证 |
|---|---|---|
| FR-D1 | 实例可注册为临时实例（靠续约保活）或固定实例，并可注销。 | `RedisServiceRegistryTest`、`RenewInstanceServiceTest` |
| FR-D2 | 停止续约的临时实例会过期、被移除并发出通知，不依赖 REST API 服务器。 | `InstanceExpiryReaperTest`、`RenewInstanceServiceRobustnessTest` |
| FR-D3 | 实例过期后重新注册时，保留注册以来设置的元数据。 | `RedisServiceRegistryTest.reRegisterAfterExpiryKeepsMetadataSetSince` |
| FR-D4 | 实例元数据可按键设置，也可整体替换。 | `RedisServiceRegistryTest` |
| FR-D5 | 客户端发现服务及其存活实例，由变更事件保持最新，绝不返回已过期实例。 | `ConsistencyRedisServiceDiscoveryTest`、`ConsistencyRenewEventTest` |
| FR-D6 | 负载均衡器在存活实例中选择（随机与加权），并与发现缓存保持一致。 | `RandomLoadBalancerTest`、`ArrayChooserTest`、`BinaryChooserTest`、`TreeChooserTest`、`LoadBalancerConsistencyTest` |
| FR-D7 | 消费者被记录为其所发现服务的依赖方（拓扑）。 | `ServiceTopologyTest`、`TopologyRecordingServiceDiscoveryTest` |
| FR-D8 | Spring Cloud 应用通过标准 `DiscoveryClient` API 注册自身并发现其他服务。 | `CoSkyDiscoveryClientTest`、`CoSkyServiceRegistryTest`、`CoSkyAutoServiceRegistrationAutoConfigurationTest` |

### REST API 服务器与安全

| 编号 | 需求 | 验证 |
|---|---|---|
| FR-S1 | 用户用密码登录，获得 JWT 访问令牌和刷新令牌；匿名访问受保护接口会被拒绝。 | `RestApiIntegrationTest`、`RefreshTokenAuthenticationTest` |
| FR-S2 | 密码以 BCrypt 哈希存储；旧版本的哈希可校验，并在登录时升级。 | `UserPasswordTest` |
| FR-S3 | 用户多次登录失败或被管理员锁定后无法登录，由管理员解锁。 | `UserServiceTest`、`RestApiIntegrationTest.usersAndAuditLog` |
| FR-S4 | 角色按命名空间授予只读或读写权限；用户只能看到并访问角色授予的命名空间。 | `RbacServiceTest`、`RestApiIntegrationTest.namespaceRoleGrantsOnlyItsNamespace` |
| FR-S5 | 安全策略可在运行时替换；无效策略永远不会替换有效策略。 | `CoSkyPolicyTest` |
| FR-S6 | 操作记录在审计日志中，可筛选和导出。 | `AuditLogServiceTest`、`AuditLogFilterTest`、`RestApiIntegrationTest.usersAndAuditLog` |

## 非功能需求

| 编号 | 需求 | 验证 |
|---|---|---|
| NFR-1 | **性能**：带缓存的配置与服务发现读取从本地内存返回，不访问 Redis。 | CI 中的 JMH 基准测试，见[性能](./performance.md) |
| NFR-2 | **一致性**：每次 Redis 修改都是单个 Lua 脚本，并发写入方不会看到中间状态。 | `DiscoveryLuaKeysTest`、`ConfigLuaKeysTest`、`LuaScriptsTest` |
| NFR-3 | **容错**：Redis 临时故障会重试且不被缓存，期间继续返回最近一次的有效值。 | `ReactiveWatchCacheTest`、`RedisConsistencyConfigServiceErrorTest`、`ConsistencyRedisServiceDiscoveryErrorTest` |
| NFR-4 | **存储有界**：配置历史和审计日志按固定上限裁剪。 | `RedisConfigServiceTest.historyIsTrimmedToHistorySize`、`AuditLogRetentionTest` |
| NFR-5 | **默认安全**：未设置 JWT 密钥时服务器拒绝启动；未授权的用户没有任何命名空间权限。 | `JwtSecretValidatorTest`、`RestApiIntegrationTest` |
| NFR-6 | **兼容性**：原生 Redis，单机或集群（命名空间需带 hash tag），不需要 Redis Functions 或模块；Java 17+。 | CI 中基于 Redis 的集成测试，见[架构](./architecture.md#数据模型) |
| NFR-7 | **质量门禁**：每次变更都要通过静态分析、许可证头检查，以及至少 90% 行覆盖率 / 70% 分支覆盖率。 | CI 中的 `./gradlew check`，见 [CONTRIBUTING.md](https://github.com/Ahoo-Wang/CoSky/blob/main/CONTRIBUTING.md) |
