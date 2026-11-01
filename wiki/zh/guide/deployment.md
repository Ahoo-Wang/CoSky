---
title: 部署
description: 在 Docker 或 Kubernetes 上把 CoSky server 用于生产环境，以及它对 Redis 的要求。
---

# 部署

server 是一个镜像，`ahoowang/cosky`（也可用 `ghcr.io/ahoo-wang/cosky`），同时包含 Dashboard。至少运行两个副本：它们之间只共享 Redis，客户端会自行在副本之间切换。

| 端口 | 提供 |
|---|---|
| `8080` | REST API、Dashboard、Actuator |
| `9090` | gRPC，供应用使用 |

## Redis

CoSky 需要 **Redis 7.0 或更高版本，或 Valkey 8**，并且 `maxmemory-policy noeviction`。server 启动时检查这两项，不满足就拒绝运行，因为淘汰会悄悄丢失对象和 Watch 历史。支持单节点、Sentinel 和 Cluster，每个 namespace 都位于一个 slot 中。

Redis 复制是异步的，故障切换可能丢失最后的写入。为了缩小这个窗口，请在主节点上设置 `min-replicas-to-write 1`。CoSky 能发现丢失的时间线并恢复，详见[架构](./architecture.md#redis-丢失写入时)，客户端也会重新注册。但故障切换前刚写入的 Config 可能已经丢失。

如果 Redis 丢失了全部数据，server 会在大约 30 秒内自行按首次启动的方式重建：系统 namespace、`default`，以及由 bootstrap token 生成的管理员，所以不要移除这个 token。其余内容都已丢失：其他 namespace、Config、Role 和 RoleBinding。需要重新创建，或者导入你保存的 [zip 导出](./configuration.md#导入与导出)。应用会继续提供已知的数据，直到它的 namespace 重新存在，然后跟随其中的内容，并重新注册自己。

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
  ahoowang/cosky:8.0.0-alpha.1
```

请保存这个 token，它是新安装实例的管理员，详见[安全](./security.md#第一个-token)。使用 Redis Cluster 时，加上 `-e COSKY_STORE_REDIS_CLUSTER=true`。

所有[属性](./properties.md)都可以用环境变量或参数设置。`--dev` 在内存中运行，不需要登录，只用于试用。

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

探针使用 `/actuator/health/liveness` 和 `/actuator/health/readiness`。副本加载完 namespace 后才报告就绪，50,000 个实例和 10,000 个 Config [用时 2 到 3.5 秒](./performance.md)。

Redis 不可用时，副本仍然报告就绪，这是有意的。Redis 是共享依赖，否则一次故障切换就会让所有副本同时退出 Service，而它们其实还能提供已知的数据。代价是：单独和 Redis 断开的副本会继续提供它最后知道的数据。各副本的 `cosky_cache_revision{namespace}` 超过一分钟不一致就说明出现了这种情况，重启该副本即可。

## 滚动更新

关闭时，副本最多等 5 秒就结束它的流，客户端重连到另一个副本并从原来的位置继续。如果它们在此期间掉出了 Watch 窗口，就读取一份新的快照，代价稍高但不会丢失任何东西。`cosky_watch_gone_total` 显示是否发生了这种情况，如果发生了，可以调大 `cosky.watch.window`。

## 单独部署 Dashboard

镜像在 `/` 提供 Dashboard。要托管在别处，请把它放在反向代理后面，并把 `/v1` 也转发给 server：页面要求 API 与它同源，server 不提供 CORS。

## 可观测性

指标在 `/actuator/prometheus`：

| 指标 | 关注什么 |
|---|---|
| `cosky_cache_lag_seconds{namespace}` | 写入到达副本的耗时 |
| `cosky_cache_revision{namespace}` | 副本之间长时间不一致：其中一个和 Redis 断开了 |
| `cosky_store_apply_seconds` | 写入延迟 |
| `cosky_watch_active` | 这个副本上的 watch 数 |
| `cosky_watch_gone_total{reason}` | 客户端不得不重新开始：`slow`、`compacted`、`epoch`、`regression` |
| `cosky_lease_active{namespace}`、`cosky_lease_expired_total` | 已注册的进程和到期数 |
| `cosky_lease_protection_active{namespace}` | 回收因大面积到期而暂停 |
| `cosky_revision_regression_total` | Redis 丢失了写入 |
| `cosky_anti_entropy_drift_total{namespace}` | 副本的视图与存储不一致，这本不该发生 |
| `cosky_auth_failures_total{reason}` | 登录失败 |

**链路追踪**覆盖 gRPC 和 HTTP，采样率 10%。要导出，请设置 `management.opentelemetry.tracing.export.otlp.endpoint`，比如 `http://collector:4318/v1/traces`。

**审计**：每次写入和每次认证失败都是日志中的一行 JSON。请和其他日志一起收集，详见[安全](./security.md#审计)。

## 容量规划

测量结果见[性能](./performance.md)。作为起点，512 MB 堆可以轻松容纳 10,000 个对象的 namespace，内存按每个对象约 1.5 KB 增长。镜像设置了 `-XX:MaxRAMPercentage=75` 和紧凑对象头，所以给容器的内存应该是期望的堆大小再加四分之一。
