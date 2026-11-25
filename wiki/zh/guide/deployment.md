---
title: 部署
description: 在 Docker 或 Kubernetes 上把 CoSky server 用于生产环境，以及它对 Redis 的要求。
---

# 部署

server 是一个镜像，`ahoowang/cosky`（也可用 `ghcr.io/ahoo-wang/cosky`），同时包含 Dashboard。至少运行两个副本：它们之间只共享 Redis，客户端会自行在副本之间切换。

| 端口 | 提供 |
|---|---|
| `8080` | REST API、Dashboard，以及没有[单独端口](#actuator-使用单独端口)时的 Actuator |
| `9090` | gRPC，供应用使用 |

## Redis

CoSky 需要 **Redis 7.0 或更高版本，或 Valkey 8**，并且 `maxmemory-policy noeviction`。server 启动时检查这两项，不满足就拒绝运行，因为淘汰会悄悄丢失对象和 Watch 历史。支持单节点、Sentinel 和 Cluster，每个 namespace 都位于一个 slot 中。

Redis 复制是异步的，故障切换可能丢失最后的写入。为了缩小这个窗口，请在主节点上设置 `min-replicas-to-write 1`。CoSky 能发现丢失的时间线并恢复，详见[架构](./architecture.md#redis-丢失写入时)，客户端也会重新注册。但故障切换前刚写入的 Config 可能已经丢失。

如果 Redis 丢失了全部数据，server 会在大约 30 秒内自行按首次启动的方式重建：系统 namespace、`default`，以及由 bootstrap token 生成的管理员。所以按[安全](./security.md#第一个-token)的建议吊销 bootstrap token 之后，请把环境变量或 Secret 换成一个没人用过的新 token，作为应急凭据保管，或者删除它：否则已吊销的那个会重新成为管理员。其余内容都已丢失：其他 namespace、Config、Role、RoleBinding，以及 ServiceAccount 和它们的 token。需要重新创建，或者导入你保存的 Config [zip 导出](./configuration.md#导入与导出)。开启鉴权时，应用会继续提供已知的数据，但在拿到新 token 之前都不会重新注册。关闭鉴权时，等它的 namespace 重新存在后，应用会跟随其中的内容，并重新注册自己。

### 凭据与 TLS

都写在 `cosky.store.redis.uri` 里：

| Redis 使用 | URI |
|---|---|
| 密码 | `redis://:password@redis:6379` |
| ACL 用户 | `redis://cosky:password@redis:6379` |
| TLS | `rediss://cosky:password@redis:6380` |
| Sentinel | `redis-sentinel://cosky:password@s1:26379,s2:26379,s3:26379#mymaster`。凭据是主节点和副本的；试过的 Sentinel 没有设密码。 |
| Cluster | `redis://cosky:password@node:6379`，并设置 `cosky.store.redis.cluster=true` |

使用 TLS 时，server 按 JVM 信任的证书机构校验 Redis 的证书。私有证书机构需要给镜像一个信任库：`JAVA_OPTS="-Djavax.net.ssl.trustStore=/tls/trust.p12 -Djavax.net.ssl.trustStorePassword=..."`。不受信任的证书会让 server 启动失败。`?verifyPeer=NONE` 跳过校验，只用于测试。

`JAVA_OPTS` 是在镜像自带的 JVM 参数（堆为容器内存的 75%、内存耗尽时退出、JDK 25 需要的参数）之后追加，不会替换它们。镜像基于 Ubuntu 24.04 LTS 上的 Eclipse Temurin JRE 25，以用户和组 999 运行。

ACL 用户需要 CoSky 的键和频道（都以 `cosky:` 开头），危险命令中只需要 `INFO`：

```
ACL SETUSER cosky on >password ~cosky:* &cosky:* +@all -@dangerous +info
```

它在单节点上试过各种写入、Watch 和租约过期，在 Sentinel 和 Cluster 上试过故障切换。没有 `+info` 时，server 无法检查上面的前置条件：它会在日志中说明，然后照常启动。

## Docker

```bash
docker network create cosky
```

```bash
docker run -d --name redis --network cosky redis:7.4
```

```bash
export COSKY_BOOTSTRAP_TOKEN="cosky_$(openssl rand -hex 8)_$(openssl rand -hex 16)"
echo "$COSKY_BOOTSTRAP_TOKEN"
```

```bash
docker run -d --name cosky --network cosky -p 8080:8080 -p 9090:9090 \
  -e COSKY_STORE_REDIS_URI=redis://redis:6379 \
  -e COSKY_BOOTSTRAP_TOKEN \
  ahoowang/cosky:8.0.0-beta.15
```

请保存这个 token，它是新安装实例的管理员，详见[安全](./security.md#第一个-token)。它的 secret 至少要有 22 个字母和数字，更短的话 server 拒绝启动。使用 Redis Cluster 时，加上 `-e COSKY_STORE_REDIS_CLUSTER=true`。

所有[属性](./properties.md)都可以用环境变量或参数设置。`--dev` 在内存中运行，不需要登录，只用于试用。

## Actuator 使用单独端口

Actuator 的健康检查、信息和指标不需要 token，而指标中包含 namespace 名称。在 8080 端口上，能访问 API 的人都能访问它们，所以生产环境中请给它们一个只有探针和 Prometheus 能访问的端口。`MANAGEMENT_SERVER_PORT`（`management.server.port`）把整个 Actuator 移到那里，之后 8080 端口对 `/actuator` 返回 404：

```bash
docker run -d --name cosky --network cosky -p 8080:8080 -p 9090:9090 -p 127.0.0.1:8081:8081 \
  -e COSKY_STORE_REDIS_URI=redis://redis:6379 \
  -e COSKY_BOOTSTRAP_TOKEN \
  -e MANAGEMENT_SERVER_PORT=8081 \
  ahoowang/cosky:8.0.0-beta.15
```

- **Kubernetes：**`k8s/base` 就是这样做的。它的探针访问名为 `management` 的端口（8081），Service 中不包含这个端口，所以经 Service 访问 API 的一方，比如 Ingress，访问不到 Actuator。Prometheus 直接抓取 Pod：按 `prometheus.io/*` 注解，或者用一个指向 `management` 端口的 PodMonitor，不需要为此建 Service。
- **镜像的健康检查**跟随 `MANAGEMENT_SERVER_PORT`。请用这个变量设置端口，或者用 `--health-cmd` 给容器单独配置健康检查。
- **默认端口在 8.0 中仍是 8080**，已经指向它的探针和抓取配置在你修改之前照常工作。
- **探针检查的是管理端口的服务**，而不是 8080 端口本身。如果也要检查 8080，`management.endpoint.health.probes.add-additional-paths=true` 在 8080 的 `/livez` 和 `/readyz` 提供存活和就绪状态，只返回状态。
- **开启 TLS 时**，Actuator 的端口也使用 HTTPS，除非在那里关闭它，详见[安全](./security.md#tls)。

## Kubernetes

仓库中有 [Kustomize](https://kustomize.io) 清单：一个三副本的 Deployment、一个 Service 和一个 PodDisruptionBudget。

```bash
kubectl create secret generic cosky-redis --from-literal=uri=redis://redis:6379
```

```bash
kubectl create secret generic cosky-auth --from-literal=bootstrap-token="cosky_$(openssl rand -hex 8)_$(openssl rand -hex 16)"
```

```bash
kubectl apply -k k8s/base
```

使用 Redis Cluster 时，改为应用 `k8s/overlays/redis-cluster`。你自己的 overlay 只要几行，就能设置 namespace、镜像标签或资源，可以照它的样子写。

每个副本请求半个 CPU，不限制 CPU；请求 2 GiB 内存，这也是它的内存上限，原因见[容量规划](#容量规划)：两个内存值要一起修改。节点足够时副本分散在不同节点上，清单中的注释说明了每项设置的原因。

探针在 [Actuator 的单独端口](#actuator-使用单独端口)上使用 `/actuator/health/liveness` 和 `/actuator/health/readiness`。副本加载完 namespace 后才报告就绪，50,000 个实例和 10,000 个 Config [用时 2 到 3.5 秒](./performance.md)。

Redis 不可用时，副本仍然报告就绪，这是有意的。Redis 是共享依赖，否则一次故障切换就会让所有副本同时退出 Service，而它们其实还能提供已知的数据。代价是：单独和 Redis 断开的副本会继续提供它最后知道的数据。各副本的 `cosky_cache_revision{namespace}` 超过一分钟不一致就说明出现了这种情况，重启该副本即可。

## 滚动更新

在 Kubernetes 中，停止的副本先等 5 秒，让 Service 不再把连接发给它。然后最多再等 5 秒结束它的流，客户端重连到另一个副本并从原来的位置继续。如果它们在此期间掉出了 Watch 窗口，就读取一份新的快照，代价稍高但不会丢失任何东西。`cosky_watch_gone_total` 显示是否发生了这种情况，如果发生了，可以调大 `cosky.watch.window`，Config 较大时还要调大 `cosky.watch.window-bytes`。

客户端一直保持连接，所以滚动更新或扩容后，最后启动的副本上客户端很少。每个副本在大约 30 分钟后（上下浮动十分之一）结束一个连接，给其中的流 5 秒，客户端就重新分布：它们经 Service 重连，并从原来的位置继续。应用只需要配置 Service 这一个端点。`spring.grpc.server.keepalive.connection.max-age` 设置这个时间，它下面的 `grace-period` 设置那 5 秒。

腾空（drain）节点时，其上的副本也按同样的方式停止。PodDisruptionBudget 一次只让一个副本离开，所以同时腾空两个节点时，会等第一个替代副本就绪。在四个节点的 k3s 集群上，依次腾空每个节点、再同时腾空两个，写入、应用读取 Config 和注册都没有失败。

## 单独部署 Dashboard

镜像在 `/` 提供 Dashboard。要托管在别处，请把它放在反向代理后面，并把 `/v1` 也转发给 server：页面要求 API 与它同源，server 不提供 CORS。

## 可观测性

指标在 `/actuator/prometheus`：

| 指标 | 关注什么 |
|---|---|
| `cosky_cache_lag_seconds{namespace}` | 写入到达副本的耗时 |
| `cosky_cache_revision{namespace}` | 副本之间长时间不一致：其中一个和 Redis 断开了 |
| `cosky_store_apply_seconds` | 写入延迟，直方图：`histogram_quantile(0.99, sum by (le) (rate(cosky_store_apply_seconds_bucket[5m])))` 是所有副本合起来的 p99 |
| `cosky_watch_active` | 这个副本上的 watch 数 |
| `cosky_watch_events_sent_total` | 发给 watcher 的事件数 |
| `cosky_watch_gone_total{reason}` | 客户端不得不重新开始：`slow`、`compacted`、`epoch`、`regression` |
| `cosky_lease_active{namespace}`、`cosky_lease_expired_total{namespace}` | 已注册的进程和到期数。只有回收器的 Leader 上报，所以对各副本的 gauge 取 `max by (namespace)`，Leader 切换时也适用 |
| `cosky_lease_protection_active{namespace}` | 回收因大面积到期而暂停，同样由回收器的 Leader 上报 |
| `cosky_reaper_failures_total{namespace}` | 存储在某个 namespace 上失败，比如它所在的 Redis Cluster 分片不可用，回收器照常处理其他 namespace |
| `cosky_revision_regression_total` | Redis 丢失了写入 |
| `cosky_anti_entropy_drift_total{namespace}` | 副本的视图与存储不一致，这本不该发生 |
| `cosky_auth_failures_total{reason}` | 因 token 被拒绝的调用：`missing` 是没带 token；`invalid` 是 token 格式不对、未知、已吊销或已过期，或 ID token 没通过 OIDC 校验；`unavailable` 是无法判定，例如访问规则还在加载，或身份提供方不可达。告警看 `missing` 和 `invalid`，它们指向某个客户端；`unavailable` 伴随故障出现，由其他告警发现 |
| `cosky_auth_enabled` | `0`：鉴权已关闭，每个调用者都是管理员。在 Redis 上这样运行时，server 启动时还会记录一条错误日志 |

namespace 删除后，它的序列随之消失。

**链路追踪**覆盖 gRPC 和 HTTP，采样率 10%。要导出，请设置 `management.opentelemetry.tracing.export.otlp.endpoint`，比如 `http://collector:4318/v1/traces`。

**审计**：每次写入和每次认证失败都是日志中的一行 JSON。请和其他日志一起收集，详见[安全](./security.md#审计)。

## 容量规划

测量结果见[性能](./performance.md)。作为起点，512 MB 堆可以轻松容纳 10,000 个对象的 namespace，内存按每个对象约 1.5 KB 增长。镜像设置了 `-XX:MaxRAMPercentage=75` 和紧凑对象头，所以堆是容器内存上限的四分之三：给容器的内存应该比期望的堆大小多三分之一，并且内存请求等于上限，就像 `k8s/base` 那样：2 GiB 的容器，1.5 GiB 的堆。

大 Config 需要单独考虑，因为为续传保留的内容包括 Config 的内容：

- **副本**：除了所有对象之外，每个 namespace 最多保留 `cosky.watch.window-bytes`（64 MB）的近期变更。超出后，落后的客户端会读取一份新的快照。
- **Redis**：日志同样受这个上限约束，此外还保存每个 Config 最近 20 个 revision 和所有对象。把一个 1 MB 的 Config 写 800 次占用了 92 MB：64 MB 日志和 20 MB 历史。达到 `maxmemory` 后写入会失败，不会淘汰任何数据。
