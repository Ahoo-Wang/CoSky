---
title: 配置属性
description: server 和 Spring Boot starter 的所有配置项及其默认值。
---

# 配置属性

时长要带单位，例如 `500ms`、`30s` 或 `5m`。不带单位时按秒计算，server 和 starter 都是如此，所以 `COSKY_LEASE_REAPER_GRACE=30` 是 30 秒。两者都带有 Spring Boot 的配置元数据，IDE 能补全并解释下面的 `cosky.*` 属性。

## Server

可以写在 `application.yaml` 中，也可以用参数或环境变量设置：`cosky.store.redis.uri` 对应 `COSKY_STORE_REDIS_URI`。

不能工作的值会让 server 拒绝启动，错误信息写明属性和它的值：

- 小于等于 0 的时长、数量或大小。`reaper-grace` 和 `protection-max-duration` 可以为 0，表示关闭宽限或暂停，`protection-min-leases` 也可以为 0；
- 不在 0 到 1 之间的 `protection-threshold`；
- 不长于 `reaper-interval` 的 `lock-ttl`：锁会在两次续期之间失效，Leader 来回易手。

server 不认识的键会被忽略，例如 Kubernetes 为名为 `cosky` 的 Service 设置的 `COSKY_SERVICE_HOST` 和 `COSKY_PORT`。

### 存储

| 属性 | 默认值 | 含义 |
|---|---|---|
| `cosky.store.type` | `redis` | `redis`，或用于开发的 `memory`：进程结束后什么都不保留 |
| `cosky.store.redis.uri` | `redis://localhost:6379` | 也接受 Sentinel URI，例如 `redis-sentinel://s1:26379,s2:26379#mymaster`：server 跟随 Sentinel 指定的主节点。一条命令最多等 Redis 5 秒，除非 URI 自己设置，例如 `?timeout=10s` |
| `cosky.store.redis.cluster` | `false` | URI 是 Redis Cluster 的种子节点 |
| `cosky.store.redis.tail-poll-interval` | `1s` | 没有提示时每个 namespace 的读取间隔 |

### 认证

| 属性 | 默认值 | 含义 |
|---|---|---|
| `cosky.auth.enabled` | `true` | 关闭后任何人都可以做任何事，只用于开发：使用 Redis 时 server 启动会记录一条错误日志，`cosky_auth_enabled` 为 0。 |
| `cosky.auth.bootstrap-token` | `$COSKY_BOOTSTRAP_TOKEN` | 没有任何角色绑定时成为 `admin` ServiceAccount。secret 至少要有 22 个字母和数字，见[安全](./security.md#第一个-token) |
| `cosky.auth.namespace-accounts` | `true` | 给每个 namespace 一个同名 ServiceAccount，在该 namespace 中绑定 `client`，它的应用共用这个账号的 token |
| `cosky.auth.oidc.issuer` | — | 与 `client-id` 一起开启 OIDC |
| `cosky.auth.oidc.client-id` | — | Dashboard 使用的客户端 |
| `cosky.auth.oidc.audience` | 客户端 | token 必须面向的受众 |
| `cosky.auth.oidc.groups-claim` | `groups` | 存放分组名称的声明 |
| `cosky.auth.oidc.scopes` | `openid, profile` | Dashboard 申请的范围。有些提供方要显式申请才返回分组。 |

### Watch 与读取

| 属性 | 默认值 | 含义 |
|---|---|---|
| `cosky.watch.window` | `10000` | 为续传保留的事件数，也是存储日志的长度。应不小于峰值事件速率乘以最长重连间隔。 |
| `cosky.watch.window-bytes` | `64MB` | 同样的上限，按字节计算，每个 namespace 一份，同时约束内存和存储日志。 |
| `cosky.watch.progress-interval` | `10s` | 向空闲 watch 发送书签的间隔 |
| `cosky.watch.max-per-stream` | `1000` | 一条流上的 watch 数 |
| `cosky.watch.anti-entropy-interval` | `5m` | 副本把视图与存储比较的间隔 |
| `cosky.read.min-revision-timeout` | `3s` | 读取或续传等待副本追上的时间 |
| `cosky.view.topology-flush-interval` | `30s` | 副本记录谁 watch 谁的间隔。边在三倍间隔后过期。 |

### 租约

| 属性 | 默认值 | 含义 |
|---|---|---|
| `cosky.lease.reaper-interval` | `1s` | 查找到期租约的间隔 |
| `cosky.lease.reaper-grace` | `30s` | 成为 Leader 或访问存储失败后，这段时间内不回收；只在一个 namespace 上失败时只影响这个 namespace |
| `cosky.lease.reaper-max-per-tick` | `100` | 每个 namespace 每轮回收的租约数 |
| `cosky.lease.lock-ttl` | `15s` | 回收器的 Leader 身份不续约时的有效期 |
| `cosky.lease.protection-threshold` | `0.5` | namespace 中同时到期的租约超过这个比例时暂停回收 |
| `cosky.lease.protection-min-leases` | `10` | 租约少于这个数的 namespace 不会暂停 |
| `cosky.lease.protection-max-duration` | `5m` | 暂停的最长时间 |

### 来自 Spring

| 属性 | 默认值 | 含义 |
|---|---|---|
| `server.port` | `8080` | REST、Dashboard 和 Actuator |
| `management.server.port` | `server.port` | 让 Actuator 使用[单独的端口](./deployment.md#actuator-使用单独端口)，`k8s/base` 就是这样 |
| `spring.grpc.server.port` | `9090` | gRPC |
| `server.ssl.bundle`、`spring.grpc.server.ssl.bundle` | — | [TLS](./security.md#tls) |
| `spring.grpc.server.shutdown.grace-period` | `5s` | 关闭时留给已打开的流的时间 |
| `spring.grpc.server.keepalive.connection.max-age` | `30m` | 客户端连接在这之后（上下浮动十分之一）结束，让客户端[重新分布到各副本](./deployment.md#滚动更新) |
| `spring.grpc.server.keepalive.connection.grace-period` | `5s` | 留给这样一个连接中的流的时间 |
| `management.tracing.sampling.probability` | `0.1` | 追踪的请求比例 |
| `management.opentelemetry.tracing.export.otlp.endpoint` | — | 链路数据发往何处 |
| `springdoc.api-docs.enabled` | `true` | REST API 的文档（`/v3/api-docs`），以及基于它的 Swagger UI（`/swagger-ui.html`）；`springdoc.swagger-ui.enabled=false` 只保留文档 |

### 固定限制

这些在 8.0 中不能配置。

| 限制 | 值 |
|---|---|
| Config 内容 | 1 MiB |
| Config 历史 | 最近 20 个 revision |
| 租约 TTL | 5 到 300 秒，默认 30 |
| 实例元数据 | 64 项；key 最长 63 个字符，value 最长 1024 |
| 标签 | 32 项；key 最长 63 个字符，value 最长 253 |
| 描述 | 1024 个字符 |
| 每个 ServiceAccount 的 token 数 | 2 |
| 单条消息 | 2 MiB；更大的结果分页或分块返回 |
| 列表的一页 | 默认 500，最大 1000 |
| REST 请求体 | 12 MiB，即最大的对象在 JSON 中每个字符都转义时的大小；导入的压缩包 64 MiB |

## Spring Boot starter

| 属性 | 默认值 | 含义 |
|---|---|---|
| `cosky.enabled` | `true` | 控制以下全部功能 |
| `cosky.endpoints` | `localhost:9090` | 依次尝试的 server |
| `cosky.token` | — | 每次调用都发送。空白值视为未设置，所以 `${COSKY_TOKEN:}` 可以和 `cosky.token-file` 同时写。 |
| `cosky.token-file` | — | 改从文件读 token，比如挂载的 Secret。文件变化后重新读取，轮换 token 不用重启。 |
| `cosky.namespace` | `default` | |
| `cosky.tls.enabled` | `false` | 通过 TLS 连接 |
| `cosky.tls.trusted-certificates` | — | 要信任的 PEM 文件，用于私有 CA。否则由 JVM 的信任库决定。 |
| `cosky.snapshot.enabled` | `true` | 把视图保存到磁盘 |
| `cosky.snapshot.dir` | `~/.cosky` | 保持默认且不可写时（比如容器里没有家目录的用户），快照关闭 |
| `cosky.snapshot.include-config` | `true` | 快照没有加密，设为 false 可让 Config 内容不落盘 |
| `cosky.registry.enabled` | `true` | 注册这个应用 |
| `cosky.registry.weight` | `1` | `0` 表示不接收流量 |
| `cosky.registry.metadata` | — | |
| `cosky.discovery.enabled` | `true` | `DiscoveryClient` 和 LoadBalancer |
| `cosky.discovery.load-timeout` | `5s` | 首次读取一个服务的等待时间 |
| `cosky.config.load-timeout` | `5s` | 导入一个 Config 的等待时间 |
| `cosky.config.refresh.enabled` | `true` | 变化时发布 `RefreshEvent` |
| `management.health.cosky.enabled` | `true` | 健康指示器，在 `/actuator/health` 中名为 `cosky` |

服务名是 `spring.application.name`。`spring.cloud.service-registry.auto-registration.enabled: false` 也会停止注册。

应用自己定义的 bean 会取代 starter 的：名为 `coskyHealthIndicator` 的 bean，或类型为 `CoSkyDiscoveryClient`、`CoSkyReactiveDiscoveryClient`、`CoSkyServiceRegistry`、`CoSkyAutoServiceRegistration` 的 bean。
