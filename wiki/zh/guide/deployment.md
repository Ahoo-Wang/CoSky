---
title: 部署
description: 用 Docker、Kubernetes 或独立发行包运行 CoSky REST API 服务器与控制台，并为生产环境准备 Redis。
---

# 部署

本页面向运行 **REST API 服务器**的运维人员，该服务器提供 REST API 和控制台。使用 starter 的应用不需要它，只需要 Redis。

服务器是无状态的，所有状态都在 Redis 中。可以在负载均衡器后面运行任意多个实例，但每个实例必须使用**相同的 JWT 密钥**。

## 准备工作

| 需要 | 原因 |
|---|---|
| 服务器能访问 Redis | 所有数据都在 Redis 中。见[准备 Redis](#准备-redis)。 |
| JWT 密钥（`COSKY_SECURITY_KEY`），随机，至少 32 个字符 | 开启安全时，没有密钥服务器拒绝启动。可用 `openssl rand -base64 32` 生成。 |
| 端口 8080 | REST API 与控制台。 |

## Docker

镜像发布到 `ahoowang/cosky`、`ghcr.io/ahoo-wang/cosky` 和 `registry.cn-shanghai.aliyuncs.com/ahoo/cosky`，支持 `linux/amd64` 与 `linux/arm64`，标签为 `6.0.0` 和 `6.0`。

```bash
docker run -d --name cosky -p 8080:8080 \
  -e SPRING_DATA_REDIS_URL=redis://redis.example.com:6379 \
  -e COSKY_SECURITY_KEY="$(openssl rand -base64 32)" \
  ahoowang/cosky:6.0.0
```

同时启动开启持久化的 Redis 和 CoSky 的 Compose 文件：

```yaml
services:
  redis:
    image: redis:7
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
  cosky:
    image: ahoowang/cosky:6.0.0
    ports:
      - "8080:8080"
    environment:
      SPRING_DATA_REDIS_URL: redis://redis:6379
      COSKY_SECURITY_KEY: ${COSKY_SECURITY_KEY:?set COSKY_SECURITY_KEY to a random secret}
    depends_on:
      redis:
        condition: service_healthy
volumes:
  redis-data:
```

镜像以非 root 用户运行，并定义了针对 `/actuator/health` 的健康检查。

## Kubernetes

仓库在 [`k8s/deployment`](https://github.com/Ahoo-Wang/CoSky/tree/main/k8s/deployment) 中提供清单：`cosky.yml` 用于单机 Redis，`cosky-cluster.yml` 用于 Redis Cluster，`cosky-service.yaml` 暴露 80 端口。

```bash
kubectl create secret generic cosky-secret --from-literal=security-key="$(openssl rand -base64 32)"
kubectl create secret generic redis-secret \
  --from-literal=host=redis.default.svc --from-literal=password='<redis 密码>'
# Redis Cluster：用 --from-literal=nodes=node1:6379,node2:6379,node3:6379 代替 host
kubectl apply -f k8s/deployment/cosky.yml -f k8s/deployment/cosky-service.yaml
```

- 启动、就绪和存活探针分别使用 `/actuator/health`、`/actuator/health/readiness` 和 `/actuator/health/liveness`。
- 资源请求为 250m CPU / 1 GiB 内存，上限为 1 CPU / 1.25 GiB；JVM 堆固定为 512 MiB（见 [JVM 参数](#jvm-参数)）。
- Pod 带有 OpenTelemetry Operator Java 自动埋点的注解；没有运行该 operator 时可以删掉它。

## 独立发行包

从源码构建发行包（需要 JDK 17+，构建控制台还需要 Node.js 22.12+ 和 pnpm）：

```bash
cd dashboard && pnpm install && pnpm build && cd ..   # 不执行这一步，服务器就没有 Web 界面
./gradlew :cosky-rest-api:distTar
tar -xvf cosky-rest-api/build/distributions/cosky-rest-api-6.0.0.tar
cd cosky-rest-api-6.0.0
COSKY_SECURITY_KEY="$(openssl rand -base64 32)" bin/cosky-rest-api \
  --spring.data.redis.url=redis://localhost:6379
```

| 路径 | 内容 |
|---|---|
| `bin/cosky-rest-api` | 启动脚本；额外的 JVM 参数放在 `JAVA_OPTS` 中 |
| `config/application.yaml`、`config/bootstrap.yaml` | 配置，代替 jar 中自带的默认配置 |
| `ui/` | 控制台 |
| `logs/` | 应用日志（`cosky-rest-api.log`）和 GC 日志 |
| `data/` | 发生 `OutOfMemoryError` 时的堆转储 |

请在发行包目录下运行脚本：`config/`、`logs/` 和 `data/` 都相对于工作目录解析。

### JVM 参数

启动脚本（以及 Docker 镜像）使用固定的 512 MiB 堆、ZGC，GC 日志写入 `logs/`，堆转储写入 `data/`。它还在 **5555 端口开启了无认证的 JMX**：绝不要把这个端口暴露到主机或 Pod 之外。

## 配置服务器

任何属性都可以通过环境变量（`SPRING_DATA_REDIS_URL`）、命令行参数（`--spring.data.redis.url=…`）或 `config/application.yaml` 设置。运维通常设置的有：

| 环境变量 | 默认值 | 用途 |
|---|---|---|
| `SPRING_DATA_REDIS_URL` | `redis://localhost:6379` | Redis 连接；也可用 `SPRING_DATA_REDIS_HOST`、`_PORT`、`_PASSWORD` |
| `SPRING_DATA_REDIS_CLUSTER_NODES` | — | Redis Cluster 节点，逗号分隔 |
| `COSKY_SECURITY_KEY` | —（必填） | JWT 签名密钥 |
| `COSKY_SUPER_INIT` | `false` | `true` 时每次启动都重置超级用户密码；恢复访问时设置一次，之后删除 |
| `COSKY_NAMESPACE` | `cosky-{system}` | 服务器自身配置与注册所在的命名空间 |
| `COSKY_AUTO_REGISTRY` | `true` | 把服务器自身注册为服务 `cosky-rest-api` |
| `SERVER_PORT` | `8080` | HTTP 端口 |

完整列表见[配置属性](./properties.md#rest-api-服务器)。

### 首次启动

首次启动时，服务器创建超级用户 `cosky` 并把随机密码打印到日志：

```bash
docker logs cosky | grep "init super user"        # 或：grep "init super user" logs/cosky-rest-api.log
```

在 `http://<服务器>:8080` 登录，然后创建用户和角色：见[安全](./security.md)。

## 准备 Redis

| 关注点 | 建议 |
|---|---|
| 版本与模式 | 任何仍在维护的 Redis 版本，单机、Sentinel 或 Cluster 均可。不需要模块或 Redis Functions。 |
| 持久化 | 开启 AOF（`appendonly yes`）或 RDB 快照：配置、用户和角色只存在于 Redis 中。重启后实例会通过续约重新注册。 |
| 淘汰策略 | 使用 `maxmemory-policy noeviction`，或把 CoSky 放在不设 `maxmemory` 的 Redis 中：被淘汰的键就是丢失的配置或实例。 |
| 集群 | 命名空间的所有键都带有该命名空间的 hash tag（starter 会自动加上 `{…}`），因此一个命名空间位于同一个槽，Lua 脚本始终有效。 |
| 安全 | 设置密码（`requirepass` 或 ACL），并把 Redis 放在私有网络中：能写 Redis 的人就能修改配置和注册信息。 |

## 升级

逐台升级服务器；键结构在各版本间通用。从 5.x 升级请阅读[升级到 6.0](./upgrade-6.md)。
