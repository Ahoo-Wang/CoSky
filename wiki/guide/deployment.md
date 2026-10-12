---
title: Deployment
description: Run the CoSky REST API server and dashboard with Docker, Kubernetes or as a standalone distribution, and prepare Redis for production.
---

# Deployment

This page is for operators running the **REST API server**, which serves the REST API and the dashboard. Applications using the starters do not need it: they only need Redis.

The server is stateless; all state lives in Redis. Run as many instances as you need behind a load balancer, with the **same JWT secret** on each.

## Before you start

| Requirement | Why |
|---|---|
| Redis reachable from the server | All data lives there. See [Preparing Redis](#preparing-redis). |
| A JWT secret (`COSKY_SECURITY_KEY`), random, ≥ 32 characters | The server refuses to start without one while security is enabled. Generate it with `openssl rand -base64 32`. |
| Port 8080 | REST API and dashboard. |

## Docker

Images are published for `linux/amd64` and `linux/arm64` to `ahoowang/cosky`, `ghcr.io/ahoo-wang/cosky` and `registry.cn-shanghai.aliyuncs.com/ahoo/cosky`, tagged `6.2.0` and `6.2`.

```bash
docker run -d --name cosky -p 8080:8080 \
  -e SPRING_DATA_REDIS_URL=redis://redis.example.com:6379 \
  -e COSKY_SECURITY_KEY="$(openssl rand -base64 32)" \
  ahoowang/cosky:6.2.0
```

A Compose file starting Redis with persistence and CoSky:

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
    image: ahoowang/cosky:6.2.0
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

The image runs as a non-root user and defines a health check on `/actuator/health`.

## Kubernetes

The repository provides manifests in [`k8s/deployment`](https://github.com/Ahoo-Wang/CoSky/tree/main/k8s/deployment): `cosky.yml` for a standalone Redis, `cosky-cluster.yml` for Redis Cluster, and `cosky-service.yaml` exposing port 80.

```bash
kubectl create secret generic cosky-secret --from-literal=security-key="$(openssl rand -base64 32)"
kubectl create secret generic redis-secret \
  --from-literal=host=redis.default.svc --from-literal=password='<redis password>'
# for Redis Cluster: --from-literal=nodes=node1:6379,node2:6379,node3:6379 instead of host
kubectl apply -f k8s/deployment/cosky.yml -f k8s/deployment/cosky-service.yaml
```

- Startup, readiness and liveness probes use `/actuator/health`, `/actuator/health/readiness` and `/actuator/health/liveness`.
- Requests are 250m CPU / 1 GiB memory, limits 1 CPU / 1.25 GiB; the JVM heap is fixed at 512 MiB (see [JVM options](#jvm-options)).
- The pod is annotated for the OpenTelemetry Operator's Java auto-instrumentation; remove the annotation if you do not run the operator.

## Standalone distribution

Build the distribution from source (JDK 17+, and Node.js 22.12+ with pnpm for the dashboard):

```bash
cd dashboard && pnpm install && pnpm build && cd ..   # without this, the server has no web UI
./gradlew :cosky-rest-api:distTar
tar -xvf cosky-rest-api/build/distributions/cosky-rest-api-6.2.0.tar
cd cosky-rest-api-6.2.0
COSKY_SECURITY_KEY="$(openssl rand -base64 32)" bin/cosky-rest-api \
  --spring.data.redis.url=redis://localhost:6379
```

| Path | Content |
|---|---|
| `bin/cosky-rest-api` | Start script; extra JVM options go in `JAVA_OPTS` |
| `config/application.yaml`, `config/bootstrap.yaml` | Configuration, read instead of the defaults bundled in the jar |
| `ui/` | Dashboard |
| `logs/` | Application log (`cosky-rest-api.log`) and GC logs |
| `data/` | Heap dumps on `OutOfMemoryError` |

Run the script from the distribution directory: it resolves `config/`, `logs/` and `data/` relative to the working directory.

### JVM options

The start script (and the Docker image) runs with a fixed 512 MiB heap, ZGC, GC logging to `logs/` and heap dumps to `data/`. Add options through `JAVA_OPTS`, for example a larger heap (`-Xmx1g`), or remote JMX with authentication and TLS when you need it.

## Configuring the server

Any property can be set as an environment variable (`SPRING_DATA_REDIS_URL`), a command-line argument (`--spring.data.redis.url=…`) or in `config/application.yaml`. The ones operators usually set:

| Environment variable | Default | Purpose |
|---|---|---|
| `SPRING_DATA_REDIS_URL` | `redis://localhost:6379` | Redis connection; or `SPRING_DATA_REDIS_HOST`, `_PORT`, `_PASSWORD` |
| `SPRING_DATA_REDIS_CLUSTER_NODES` | — | Redis Cluster nodes, comma-separated |
| `COSKY_SECURITY_KEY` | — (required) | JWT signing secret |
| `COSKY_SUPER_INIT` | `false` | `true` resets the super user's password at every start; set it once to recover access, then remove it |
| `COSKY_NAMESPACE` | `cosky-{system}` | Namespace of the server's own config and registration |
| `COSKY_AUTO_REGISTRY` | `true` | Register the server itself as service `cosky-rest-api` |
| `SERVER_PORT` | `8080` | HTTP port |

The full list is in [Configuration Properties](./properties.md#rest-api-server).

### First start

On first start the server creates the super user `cosky` with a random password and prints it to the log:

```bash
docker logs cosky | grep "init super user"        # or: grep "init super user" logs/cosky-rest-api.log
```

Sign in at `http://<server>:8080`, then create users and roles: see [Security](./security.md).

## Preparing Redis

| Concern | Recommendation |
|---|---|
| Version and mode | Any maintained Redis release, standalone, Sentinel or Cluster. No modules or Redis Functions are needed. |
| Persistence | Enable AOF (`appendonly yes`) or RDB snapshots: configs, users and roles exist only in Redis. Instances are re-registered by their renewals after a restart. |
| Eviction | Use `maxmemory-policy noeviction`, or keep CoSky in a Redis without `maxmemory`: an evicted key is a lost config or instance. |
| Cluster | Every key of a namespace carries the namespace's hash tag (the starters add `{…}` automatically), so a namespace lives in one slot and Lua scripts stay valid. |
| Security | Require a password (`requirepass` or ACLs) and keep Redis on a private network; anyone who can write to it can change configs and registrations. |

## Upgrading

Upgrade servers one at a time; the key schema is shared across versions. Read [Upgrading to 6.0](./upgrade-6.md) when coming from 5.x.
