---
title: 配置属性
description: CoSky starter 与 REST API 服务器的全部属性及默认值。
---

# 配置属性

时长支持 Spring Boot 的格式（`10s`、`1m`、`PT10S`）。决定加载哪个配置的属性必须写在 `bootstrap.yaml` 中。

## 通用

| 属性 | 默认值 | 说明 |
|---|---|---|
| `spring.cloud.cosky.enabled` | `true` | 启用 CoSky（配置与服务发现）。 |
| `spring.cloud.cosky.namespace` | `cosky-{default}` | 应用的配置与注册所在的命名空间。不带 `{…}` 的值会被包进 hash tag：`dev` 变为 `{dev}`。 |
| `spring.data.redis.*` | Spring Boot 默认值 | Redis 连接（`url`、`host`、`port`、`password`、`cluster.nodes` 等），与应用共用。 |

## 配置

| 属性 | 默认值 | 说明 |
|---|---|---|
| `spring.cloud.cosky.config.enabled` | `true` | 启用 config starter。 |
| `spring.cloud.cosky.config.config-id` | `${spring.application.name}.${file-extension}` | 要加载的配置。 |
| `spring.cloud.cosky.config.file-extension` | `yaml` | 不带扩展名的配置 ID 所用格式：`yaml`、`yml`、`properties` 或 `xml`。 |
| `spring.cloud.cosky.config.timeout` | `2s` | 启动时等待配置的最长时间。 |

## 服务发现

| 属性 | 默认值 | 说明 |
|---|---|---|
| `spring.cloud.cosky.discovery.enabled` | `true` | 启用服务发现与注册。 |
| `spring.cloud.cosky.discovery.order` | `0` | 存在多个 `DiscoveryClient` 时 CoSky 的顺序。 |
| `spring.cloud.cosky.discovery.timeout` | `2s` | 阻塞式 `DiscoveryClient` 调用的最长等待时间。 |
| `spring.cloud.cosky.discovery.topology-enabled` | `true` | 把应用记录为其所发现服务的消费者。 |
| `spring.cloud.service-registry.auto-registration.enabled` | `true` | 启动时注册应用（Spring Cloud）。 |

### 注册

前缀：`spring.cloud.cosky.discovery.registry`。

| 属性 | 默认值 | 说明 |
|---|---|---|
| `service-id` | `spring.application.name` | 实例所属的服务。 |
| `host` | 第一个非回环 IP | 其他服务调用的地址。 |
| `port` | Web 服务器端口 | 其他服务调用的端口。 |
| `schema` | `http` | URI 协议。 |
| `weight` | `1` | CoSky 加权负载均衡器使用的相对权重。 |
| `is-ephemeral` | `true` | `true`：不续约就过期。`false`：固定实例，永不过期。 |
| `ttl` | `60s` | 临时实例不续约时的存活时间。 |
| `renew.initial-delay` | `1s` | 首次续约前的延迟。 |
| `renew.period` | `10s` | 续约间隔；要明显小于 `ttl`。 |
| `metadata.<key>` | — | 随实例注册的元数据。 |
| `initial-status` | `UP` | 注册时元数据 `instance_status` 的值。 |
| `timeout` | `2s` | 注册、注销和修改状态的最长等待时间。 |
| `renew-event-carries-ttl-at` | `false` | 续约事件携带新的过期时间，消费者可少读一次 Redis。所有客户端都运行 6.0 或更高版本后再开启。 |

## REST API 服务器

| 属性 | 默认值 | 说明 |
|---|---|---|
| `cosky.security.key` | —（必填） | JWT 签名密钥；环境变量 `COSKY_SECURITY_KEY`。 |
| `cosky.security.enabled` | `true` | 启用认证与授权。 |
| `cosky.security.enforce-init-super-user` | `false` | 每次启动都重置超级用户密码；环境变量 `COSKY_SUPER_INIT`。 |
| `cosky.security.audit-log.action` | `write` | 记录哪些请求：`read`、`write` 或 `rw`。 |
| `cosky.security.audit-log.max-size` | `100000` | 保留的最新审计条目数。 |
| `cosec.jwt.token-validity.access` | `15m` | 访问令牌有效期。 |
| `cosec.jwt.token-validity.refresh` | `3H` | 刷新令牌有效期。 |
| `cosky.namespace` | `cosky-{system}` | 服务器自身配置与注册所在的命名空间；环境变量 `COSKY_NAMESPACE`。 |
| `cosky.auto-registry` | `true` | 把服务器注册为服务 `cosky-rest-api`；环境变量 `COSKY_AUTO_REGISTRY`。 |
| `server.port` | `8080` | HTTP 端口。 |

服务器是 Spring Boot 应用：标准的 Spring Boot 属性（日志、actuator、Redis）同样适用。
