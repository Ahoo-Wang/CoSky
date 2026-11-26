---
title: 故障排查
description: server 出问题时运维人员会看到什么、原因是什么、该怎么做。
---

# 故障排查

本页面向 server 的运维人员。每一条都从你看到的现象出发，一行日志、一个指标或一个错误原因，说明它的原因和处理办法。指标列在[部署](./deployment.md#可观测性)中，错误原因列在 [REST API](./rest-api.md#约定) 中。

## server 无法启动

server 在日志末尾的 `APPLICATION FAILED TO START` 下说明原因，包括描述（Description）和处理办法（Action）：

| 描述的开头 | 处理 |
|---|---|
| `The secret of the bootstrap token, after its second _, has ... characters` | 把 `COSKY_BOOTSTRAP_TOKEN` 换成一个新 token，比如 `cosky_$(openssl rand -hex 8)_$(openssl rand -hex 16)`，或者删除它。开启鉴权时，server 每次启动都检查它，即使已经不再读取它。 |
| `The bootstrap token must look like cosky_<id>_<secret>` | 同上。 |
| `cosky.lease.reaper-interval is 0s: it must be longer than 0.` | 一个不能工作的设置。处理办法中写明了属性、对应的环境变量和默认值。不带单位的时长按秒计算。 |
| `CoSky cannot reach Redis` | 检查 `COSKY_STORE_REDIS_URI`：主机、端口、密码，TLS 要用 `rediss://`。 |
| `This Redis cannot back CoSky:` | 后面是哪里不对，比如 `maxmemory_policy is allkeys-lru, and eviction silently deletes data; it must be noeviction.`，或者版本低于 7.0。按[部署](./deployment.md#redis)的要求修改 Redis。托管 Redis 可能需要修改它的参数组。 |

有一种失败以堆栈结束，请看它最后的 cause：

- **`With an OIDC issuer set, cosky.auth.oidc.audience or client-id is required.`** 设置 `cosky.auth.oidc.client-id`。

JVM 不信任的 Redis 证书同样会让启动失败，见[凭据与 TLS](./deployment.md#凭据与-tls)。

有三条消息在启动时出现，但不阻止启动：

- **`Cannot check the Redis prerequisites, since INFO failed`**：Redis 用户不能执行 `INFO`，所以什么也没检查。给它 `+info`。
- **`No RoleBinding exists and no bootstrap token is set, so nobody can sign in.`** 设置 [bootstrap token](./security.md#第一个-token) 后重启。
- **`Authentication is off (cosky.auth.enabled=false)`**，一条错误日志：每个调用者都是管理员，`cosky_auth_enabled` 为 0。请开启鉴权。

未知的 `cosky.*` 键不会阻止启动，所以拼错的属性会被悄悄忽略：请对照[配置属性](./properties.md)检查拼写。

## 副本没有就绪

就绪探针 `/actuator/health/readiness` 返回 `OUT_OF_SERVICE`、状态码 503，直到副本加载完系统 namespace `_system`，以及其中列出的每个 namespace。这需要几秒：50,000 个实例和 10,000 个 Config [用时 2 到 3.5 秒](./performance.md)。在此期间，访问仍在加载的 namespace 的请求被拒绝，原因是 `REPLICA_NOT_READY`，客户端会去另一个副本。

要看它在等什么，设置 `management.endpoint.health.show-details=always`。就绪组中的 `coSkyReadiness` 组件会在 `loading` 下列出这些 namespace。只有 `_system` 说明副本还没读到访问规则，所以每个带 token 的调用也会以 `REPLICA_NOT_READY` 被拒绝。

如果一直这样，说明副本读不到 Redis。它的日志反复出现 `Following namespace <name> failed; loading it again.` 和原因，它的 `cosky_store_failures_total` 上升。检查它到 Redis 的网络，以及 Redis 本身。

已经就绪的副本在 Redis 不可用时仍然就绪，见下一条。

## 副本与 Redis 断开

Redis 不可用时副本仍然就绪，这是有意的：Redis 是共享依赖，否则一次故障切换就会让所有副本同时退出 Service。所以单独与 Redis 断开的副本会继续提供它最后知道的数据：读取从这里返回，写入以 `STORE_UNAVAILABLE` 失败。

它表现为：

- 这个副本上 `rate(cosky_store_failures_total[1m]) > 0`，空闲时也是，因为它的回收器每秒都向 Redis 申请锁；
- 它的 `cosky_cache_revision{namespace}` 落后于其他副本；
- 它的日志中有 `Reading the log of namespace <name> failed; trying again`。

如果只有一个副本这样，是它自己到 Redis 的路径断了：重启它，或者把它移到另一个节点。如果每个副本都这样，是 Redis 本身宕机或正在故障切换，见下一条。

## 写入失败并返回 STORE_UNAVAILABLE

`STORE_UNAVAILABLE`（503）表示 server 连不上 Redis，或者 Redis 没有在 5 秒内应答。读取继续由副本的内存提供。应用保留它们的注册，等 Redis 再次应答后续期。回收器之后会等 30 秒才删除任何东西，好让它们续期。

`cosky_store_failures_total{operation}` 说明失败的是什么：`apply` 是写入，`tryLock` 是回收器的锁。常见原因：

- **Redis 宕机，或正在故障切换。** 实测 Cluster 或 Sentinel 的一次故障切换用了 [10 到 16 秒](./performance.md#redis-cluster-故障切换)。
- **主节点设置了 `min-replicas-to-write`，却没有副本。** Redis 会拒绝写入，直到有副本回来，这正是它的作用，见[备份与恢复](./backup.md#redis-必须做到的)。在主节点上执行 `INFO replication` 可以看到它有哪些副本。
- **Redis 达到 `maxmemory`。** 在 `noeviction` 下，Redis 拒绝写入，而不是丢弃任何数据。给它更多内存，或者看[容量规划](./deployment.md#容量规划)中是什么占用了它。

## 返回 503 或 504 的错误

| 原因 | 含义 | 处理 |
|---|---|---|
| `STORE_UNAVAILABLE` | 连不上 Redis，或 Redis 没有在 5 秒内应答 | 见[上一条](#写入失败并返回-store-unavailable)。 |
| `REPLICA_NOT_READY` | 这个副本还在加载错误中写明的 namespace，或者落后于客户端要求的位置。`_system` 代表访问规则。 | 如果很快过去，无需处理：客户端会去另一个副本。如果某个副本一直返回它，见[副本没有就绪](#副本没有就绪)。 |
| `IDENTITY_PROVIDER_UNAVAILABLE` | 连不上 OIDC 提供方或它的密钥。token 可能没问题。 | 检查提供方，以及 server 到 issuer 的路径。使用 ServiceAccount token 的应用不受影响；在此期间用这种 token 登录。 |
| `REVISION_REGRESSION` | Redis 丢失了写入，副本正在重新加载这个 namespace | 片刻即过。见[Redis 丢失了写入](#redis-丢失了写入)。 |
| `MIN_REVISION_TIMEOUT`（504） | 带 `minRevision` 的读取等了 3 秒，这个副本仍未到达那个 revision | 副本落后：检查它的 `cosky_cache_lag_seconds`，以及它是否[与 Redis 断开](#副本与-redis-断开)。 |

这四个 503 原因对客户端的含义相同：换一个副本，或稍后再试。区分它们是为了告诉你该查 Redis、身份提供方还是副本。

## 调用因 token 被拒绝

`cosky_auth_failures_total{reason}` 统计这些调用。每一次也是[审计日志](./security.md#审计)中的一行，结果为 `UNAUTHENTICATED`，`remote` 中是调用方地址。

| 原因 | 含义 | 处理 |
|---|---|---|
| `missing` | 没带 token | 按 `remote` 找到调用方：没设置 `cosky.token` 的应用，或者某个脚本。 |
| `invalid` | token 格式不对、未知、已吊销或已过期，或 ID token 没通过 OIDC 校验 | 通常是吊销或轮换后仍在使用的 token。 |
| `unavailable` | 无法判定：访问规则还在加载，或身份提供方不可达 | 伴随故障出现，`_system` 上的 `REPLICA_NOT_READY` 或 `IDENTITY_PROVIDER_UNAVAILABLE` 会显示出来。 |

token 被拒绝的应用无法启动，它以 `CoSky refused to register service <name> in namespace <namespace>: UNAUTHENTICATED` 结束。运行中 token 被吊销的应用会失去它的流，并停留在 `DEGRADED`。

`PERMISSION_DENIED`（403）不计入其中：token 有效，只是它的角色不允许这次调用。用这个 token 访问 `GET /v1/me` 可以看到它拥有的规则。

## 应用一直处于 DEGRADED

客户端没有连上 server 时，starter 的健康指示器 `cosky` 保持 `UP`，带 `connection: DEGRADED`，因为重启也无济于事。在此期间应用提供它已知的数据。它的日志在每次尝试时说明原因：`The watch stream to <endpoint> failed:`，后面是状态。

| 状态 | 原因 |
|---|---|
| `UNAVAILABLE: io exception (Connection refused ...)` | `cosky.endpoints` 上没有服务在监听。端口应是 gRPC 的 9090，不是 8080。 |
| `UNAUTHENTICATED` | token 已被吊销或已过期：见[上一条](#调用因-token-被拒绝)。 |
| `UNAVAILABLE: This replica cannot load namespace <namespace> from its store.`，或者 `has not loaded`、`is behind` | 副本[没有就绪](#副本没有就绪)，或者落后。客户端会新建连接，Kubernetes Service 可能把它路由到另一个副本。 |

两次尝试之间，客户端最多退避 10 秒。server 开启了 [TLS](./security.md#tls) 时，应用需要设置 `cosky.tls.enabled`。

## 应用启动时使用旧值

客户端把它 watch 的内容的[快照](./sdk.md#快照)保存在 `~/.cosky`，启动时从它开始，处于 `DEGRADED`，直到连上 server。读取时最多等 1 秒等 server 回应，所以只有在没有 server 应答、或应答慢于 1 秒时，它才提供上一次运行看到的内容：Config 的旧内容，或者旧的实例列表。空列表会让按这个服务名的调用失败。连上之后，它跟随 server，一次刷新就会替换旧值。

快照按端点和 token 区分。所以与上一个开发 server 地址相同的新 server，比如 `localhost:9090`，会看到应用从上一个 server 提供过的内容开始，尽管它自己什么也没保留。要从头开始，删除 `~/.cosky`，或者设置 `cosky.snapshot.enabled=false`。

## cosky_watch_gone_total 上升

不能从原来位置续传的客户端会读取它 watch 的内容的一份新快照。这需要一次读取，但不会丢失任何东西。原因说明了为什么：

- **`slow`**：跟随时掉出了窗口，因为投递跟不上。
- **`compacted`**：回来时带的 revision 比窗口还早，比如滚动更新中重连之后。
- **`epoch`**：namespace 的时间线被替换了，因为 Redis 丢失了写入或数据，或者 namespace 被删除后又重新创建。
- **`regression`**：回来时带的位置 Redis 已经没有了。

对于 `slow` 和 `compacted`，窗口对你的写入速率和重连间隔来说太小了。把 `cosky.watch.window`（默认 10,000 个事件）调大到不小于每秒峰值事件数乘以最长重连间隔的秒数；Config 较大时，也要调大 `cosky.watch.window-bytes`。二者同时限制 Redis 中的日志，所以 Redis 和每个副本都会保存更多，见[容量规划](./deployment.md#容量规划)。[性能](./performance.md#事件窗口)一页说明了它何时出现。

对于 `epoch` 和 `regression`，原因不在窗口：见[Redis 丢失了写入](#redis-丢失了写入)。

## 回收暂停

`cosky_lease_protection_active{namespace}` 为 1，日志中有一条错误 `<n> of <m> leases expired in namespace <namespace>; pausing the reaper.`：在至少 10 个租约（`protection-min-leases`）中，有超过一半（`cosky.lease.protection-threshold`）同时到期。这看起来像应用与 server 之间的网络分区，所以回收器保留它们的实例，而不是删除活着的实例。只有回收器的 Leader 上报这个 gauge：对各副本取 `max by (namespace)`。

检查应用能否连上 server：它们的日志中有 `Renewing the lease of namespace <namespace> at <endpoint> failed`，原因可能在 9090 端口前面的网络或负载均衡器。应用续期之后，到期比例下降，回收在 30 秒宽限之后恢复。

如果这些进程确实已经不在了，比如所在节点故障，回收会在 `cosky.lease.protection-max-duration`（5 分钟）之后自行恢复。在此之前，它们的实例仍然列出，调用方需要重试。

## cosky_reaper_failures_total 上升

回收器回收或巡检某个 namespace 时，存储在这个 namespace 上失败了。回收器继续处理其他 namespace，这个 namespace 单独等待自己的宽限。日志中有 `The store failed on namespace <namespace>; it waits out a grace, and the others go on.`，之后是 `The store answers on namespace <namespace> again`。

在 Redis Cluster 上，请检查存放这个 namespace 的分片及其故障切换。分片故障如何影响回收，见[服务发现](./service-discovery.md#出故障时)。整个存储失败时，日志中出现的是 `A reaper tick failed.`。

## Redis 丢失了写入

`cosky_revision_regression_total` 上升，某个副本记录一条错误 `Namespace <namespace> went back from ...; replacing the epoch.` 或 `Namespace <namespace> diverged, now at ...; loading it again.`。Redis 丢失了它已经确认的写入，原因是故障切换，或者恢复了一份较旧的快照。

CoSky 会替换这个 namespace 的 epoch。每个副本重新加载它，每个 watcher 重新开始，`cosky_watch_gone_total{reason="epoch"}` 上升，应用重新注册缺失的内容。Redis 丢失的内容仍然丢失：怎样找出它们，见[备份与恢复](./backup.md#redis-丢失数据时)。

错误日志 `The system namespace has no state: the store lost its data.` 表示 Redis 丢失了全部数据。server 会按首次启动的方式重建，见[备份与恢复](./backup.md#redis-丢失全部数据时)。

`cosky_anti_entropy_drift_total` 上升，并伴有 `<n> objects of namespace <namespace> differ from the store; replacing the epoch.`，表示某个副本的视图与 Redis 不一致。这本不该发生，恢复方式同上。请带上日志[报告给我们](https://github.com/Ahoo-Wang/CoSky/issues/new/choose)。

## 反向代理后面的 Dashboard

代理需要做什么，见[控制台](./dashboard.md#在反向代理后面)。缺了哪一项，各有各的现象：

| 你看到 | 代理 |
|---|---|
| 在 `/cosky` 上是空白页，或者找不到它的文件 | 没有把 `/cosky` 重定向到 `/cosky/`，于是浏览器按 `/` 解析页面中的地址 |
| 页面正常，但每个调用都返回 404 | 没有把 `/cosky/v1/` 转发到 server 的 `/v1/` |
| 刷新页面后才看到变化 | 缓冲了响应，挡住了变化流。nginx 中设置 `proxy_buffering off`。 |
| 文件放在别处，调用被 CORS 拦下 | API 与页面不同源。server 不提供 CORS，请在页面旁边提供 `v1/`。 |

用 OIDC 登录后，提供方会回到你给它的地址。请注册带结尾斜杠的 Dashboard 地址，比如 `https://example.com/cosky/`。
