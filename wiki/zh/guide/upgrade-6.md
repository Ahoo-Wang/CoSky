---
title: 升级到 6.0
description: CoSky 6.0 的破坏性变更，以及从 5.x 升级服务端和客户端的步骤。
---

# 升级到 6.0

CoSky 6.0 重建了一致性层，加固了安全，并限制了 Redis 中保留的数据量。Redis 键结构没有变化，滚动升级期间 5.x 和 6.0 客户端可以共用同一个命名空间。

## 建议的升级顺序

1. **升级 REST API 服务器**：先设置 JWT 密钥（见下文），再部署 6.0。
2. **为非管理员用户授予命名空间角色**；如果存储过自定义策略，同步修改。
3. **升级使用 starter 或 SDK 的应用**，按下文修改 API 调用。
4. **所有客户端都运行 6.0 之后**，可以开启 `renew-event-carries-ttl-at`。

## REST API 服务器

### 必须设置 JWT 密钥

开启安全（默认）时，未设置 `cosky.security.key` 服务器会拒绝启动。旧版本用仓库中公开的密钥签发令牌。

```bash
export COSKY_SECURITY_KEY="$(openssl rand -base64 32)"
```

所有服务器实例使用同一个密钥。更换密钥会让已签发的令牌全部失效。本 wiki 中的 Docker、Kubernetes 和独立部署示例都已传入 `COSKY_SECURITY_KEY`。

### 用户需要角色才能读取命名空间

默认策略以前允许所有已登录用户读取所有命名空间，现在只放行 `GET /v1/namespaces`（列出用户角色覆盖的命名空间）和 `GET /v1/namespaces/current`，其余接口都需要角色授权。请为每个非管理员用户授予对应命名空间的只读（`r`）或读写（`rw`）角色，参见[安全与 RBAC](./security-rbac.md)。

如果存储过自定义策略（`cosky-{system}` 命名空间中的配置 `cosky-policy.json`），它会覆盖内置策略：把其中 `namespace` 语句的 pattern `/v1/namespaces/**` 改为 `["/v1/namespaces", "/v1/namespaces/current"]`。

### 其他服务端变更

| 变更 | 需要做什么 |
|---|---|
| 密码改用 BCrypt 哈希 | 无需操作：已有哈希照常可用，并在用户下次登录时升级。新密码最长 72 字节。 |
| 删除 `PUT /v1/namespaces/current/{namespace}` | 它会为所有用户切换整个服务器的命名空间。请在每个请求路径中显式传入命名空间。 |
| 配置历史只保留最近 10 个版本 | 每个配置下次变更时清理更早的版本。 |
| 审计日志保留最新 100,000 条 | 可通过 `cosky.security.audit-log.max-size` 调整。 |
| 配置导入有大小限制 | zip 文件最大 10 MiB，解压后最大 64 MiB。 |

## 应用（starter 与 SDK）

### 配置

- `spring.cloud.cosky.enabled=false` 现在也会关闭服务发现和注册。
- 新增 `spring.cloud.cosky.discovery.topology-enabled`（默认 `true`），控制应用是否把自己记录为所发现服务的消费者。
- `spring.cloud.cosky.discovery.registry.is-ephemeral` 仍然可用。

### API 变更

以下变更只影响直接使用 CoSky 类的代码；通过 Spring Cloud `DiscoveryClient` 或 `@Value`/`@ConfigurationProperties` 使用的应用不受影响。

| 5.x | 6.0 |
|---|---|
| `ServiceDiscovery.getServices(namespace)` / `getInstances(namespace, serviceId)` 返回 `Flux` | 返回 `Mono<List<…>>` 快照。快照不变时是同一个列表实例。 |
| 不带命名空间的重载、`NamespacedContext`、`ServiceInstanceContext` | 已删除：请显式传入命名空间。命名空间不能为空，也不能包含 `:`。 |
| `EventListenerContainer` 实现 `AutoCloseable`，提供 `receive(topic)` | `listen(topic)` 在订阅生效后发出事件流；`receive(topic)` 保留。 |
| 负载均衡器接收 `ServiceDiscovery` 和 `InstanceEventListenerContainer` | 只接收 `ServiceDiscovery`，例如 `RandomLoadBalancer(serviceDiscovery)`。 |
| 一致性服务上的重置钩子 | 已删除：用 `ConfigWatcher.watchConfig(namespace, configId)` 监听变更。 |
| `RedisServiceTopology(redisTemplate)` | `RedisServiceTopology(redisTemplate, consumer)`，`consumer` 是本地服务的 `NamespacedServiceId`。 |
| `ServiceRegistry.setMetadata(…, key, value)` 会替换全部元数据 | 现在只设置这一个键；`setMetadata(…, map)` 仍替换全部元数据。 |
| `ServiceInstance` 相等性只比较地址（instance id、schema、host、port） | 改为结构相等：weight、ephemeral、ttlAt 和元数据也参与比较。 |

## 新旧版本混用

- 5.x 客户端注册的实例过期后照常会被清理。6.0 客户端还会按过期时间索引临时实例，`expired` 事件不再依赖 REST API 服务器。
- `spring.cloud.cosky.discovery.registry.renew-event-carries-ttl-at`（默认 `false`）让续约事件携带新的过期时间，每个订阅者每次续约可少读一次 Redis。5.x 客户端无法解析这种事件内容：所有客户端都运行 6.0 之后再开启。
