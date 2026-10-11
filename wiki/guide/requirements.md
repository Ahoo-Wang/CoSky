---
title: Requirements
description: The functional and non-functional requirements CoSky meets, each traced to the tests that verify it.
---

# Requirements

This page states what CoSky must do, with stable identifiers to reference in issues and pull requests. Every requirement names the tests that verify it, so a change that breaks one fails the build. New requirements are proposed through a feature request and added here with their tests.

## Scope

CoSky provides configuration management and service discovery for microservices, using only Redis as infrastructure. It is used as a library through Spring Cloud starters and the SDK, and optionally operated through the REST API server and its dashboard.

## Functional requirements

### Configuration

| ID | Requirement | Verified by |
|---|---|---|
| FR-C1 | Configs are stored per namespace and config id, created, updated, read and removed. | `RedisConfigServiceTest`, `RestApiIntegrationTest.configLifecycle` |
| FR-C2 | Every change creates a new version; the latest 10 versions are kept and any of them can be rolled back to, including a removed config. | `RedisConfigServiceTest`, `RedisConsistencyConfigServiceTest.rollback` |
| FR-C3 | Applications receive config changes without polling, and a change made right after the first read is never lost. | `RedisConsistencyConfigServiceTest`, `CoSkyConfigRefresherTest` |
| FR-C4 | Spring Cloud applications load their config at startup, including multi-document YAML, and refresh on change. | `CoSkyPropertySourceLocatorTest`, `CoSkyConfigRefresherTest` |
| FR-C5 | Configs are exported to and imported from zip archives, skipping or overwriting existing configs, within size limits. | `ConfigControllerImportTest`, `ZipsTest`, `RestApiIntegrationTest.configLifecycle` |

### Service discovery

| ID | Requirement | Verified by |
|---|---|---|
| FR-D1 | Instances register as ephemeral (kept alive by renewal) or fixed, and deregister. | `RedisServiceRegistryTest`, `RenewInstanceServiceTest` |
| FR-D2 | An ephemeral instance that stops renewing expires, is removed and announced, independently of the REST API server. | `InstanceExpiryReaperTest`, `RenewInstanceServiceRobustnessTest` |
| FR-D3 | An instance re-registered after expiring keeps the metadata set since its registration. | `RedisServiceRegistryTest.reRegisterAfterExpiryKeepsMetadataSetSince` |
| FR-D4 | Instance metadata can be set per key or replaced as a whole. | `RedisServiceRegistryTest` |
| FR-D5 | Clients discover services and their live instances, kept up to date by change events, never serving expired instances. | `ConsistencyRedisServiceDiscoveryTest`, `ConsistencyRenewEventTest` |
| FR-D6 | Load balancers choose among live instances (random and weighted), consistent with the discovery cache. | `RandomLoadBalancerTest`, `ArrayChooserTest`, `BinaryChooserTest`, `TreeChooserTest`, `LoadBalancerConsistencyTest` |
| FR-D7 | Consumers are recorded as dependents of the services they discover (topology). | `ServiceTopologyTest`, `TopologyRecordingServiceDiscoveryTest` |
| FR-D8 | Spring Cloud applications register themselves and discover others through the standard `DiscoveryClient` APIs. | `CoSkyDiscoveryClientTest`, `CoSkyServiceRegistryTest`, `CoSkyAutoServiceRegistrationAutoConfigurationTest` |
| FR-D9 | Spring Cloud clients receive only instances that are `UP` (or have no status), and see each instance's weight. | `CoSkyDiscoveryClientTest` |

### REST API server and security

| ID | Requirement | Verified by |
|---|---|---|
| FR-S1 | Users authenticate with a password and receive JWT access and refresh tokens; anonymous requests to protected endpoints are rejected. | `RestApiIntegrationTest`, `RefreshTokenAuthenticationTest` |
| FR-S2 | Passwords are stored as BCrypt hashes; hashes of older versions are verified and upgraded on login. | `UserPasswordTest` |
| FR-S3 | Users are locked after repeated failed logins or by an administrator, and unlocked by an administrator. | `UserServiceTest`, `RestApiIntegrationTest.usersAndAuditLog` |
| FR-S4 | Roles grant read or read-write access per namespace; a user only sees and reaches the namespaces their roles grant. | `RbacServiceTest`, `RestApiIntegrationTest.namespaceRoleGrantsOnlyItsNamespace` |
| FR-S5 | The security policy can be replaced at runtime; an invalid policy never replaces a valid one. | `CoSkyPolicyTest` |
| FR-S6 | Operations are recorded in an audit log that can be filtered and exported. | `AuditLogServiceTest`, `AuditLogFilterTest`, `RestApiIntegrationTest.usersAndAuditLog` |

## Non-functional requirements

| ID | Requirement | Verified by |
|---|---|---|
| NFR-1 | **Performance**: cached config and discovery reads are served from local memory, without a Redis round trip. | JMH benchmarks in CI, see [Performance](./performance.md) |
| NFR-2 | **Consistency**: every Redis mutation is a single Lua script, so concurrent writers never observe partial state. | `DiscoveryLuaKeysTest`, `ConfigLuaKeysTest`, `LuaScriptsTest` |
| NFR-3 | **Resilience**: a transient Redis failure is retried and never cached; the last good value keeps being served. | `ReactiveWatchCacheTest`, `RedisConsistencyConfigServiceErrorTest`, `ConsistencyRedisServiceDiscoveryErrorTest` |
| NFR-4 | **Bounded storage**: config history and the audit log are trimmed to fixed sizes. | `RedisConfigServiceTest.historyIsTrimmedToHistorySize`, `AuditLogRetentionTest` |
| NFR-5 | **Security by default**: the server refuses to start without a JWT secret, and users get no namespace access unless granted. | `JwtSecretValidatorTest`, `RestApiIntegrationTest` |
| NFR-6 | **Compatibility**: plain Redis, standalone or cluster (a namespace must carry a hash tag), with no Redis Functions or modules; Java 17+. | Integration tests against Redis in CI, [Architecture](./architecture.md#data-model) |
| NFR-7 | **Quality gates**: every change passes static analysis, license checks and at least 90% line / 70% branch coverage. | `./gradlew check` in CI, see [CONTRIBUTING.md](https://github.com/Ahoo-Wang/CoSky/blob/main/CONTRIBUTING.md) |
