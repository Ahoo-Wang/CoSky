---
title: Deployment
description: Run the CoSky server in production on Docker or Kubernetes, with what it asks of Redis.
---

# Deployment

The server is one image, `ahoowang/cosky` (also `ghcr.io/ahoo-wang/cosky`), holding the Dashboard too. Run at least two replicas: they share nothing but Redis, and clients move between them by themselves.

| Port | Serves |
|---|---|
| `8080` | REST API, Dashboard, Actuator |
| `9090` | gRPC, for applications |

## Redis

CoSky needs **Redis 7.0 or later, or Valkey 8**, with `maxmemory-policy noeviction`. The server checks both at start and refuses to run otherwise, because an eviction would silently lose objects and Watch history. It works on a single node, Sentinel or a Cluster, and each namespace stays in one slot.

Redis replication is asynchronous, so a failover can lose the last writes. To narrow that window, set `min-replicas-to-write 1` on the primary. CoSky notices a lost timeline and recovers, as [Architecture](./architecture.md#when-redis-loses-writes) explains, and clients register again. A Config written just before the failover may be gone.

## Docker

```bash
docker run -d --name cosky -p 8080:8080 -p 9090:9090 \
  -e COSKY_STORE_REDIS_URI=redis://redis:6379 \
  -e COSKY_BOOTSTRAP_TOKEN="cosky_$(openssl rand -hex 8)_$(openssl rand -hex 16)" \
  ahoowang/cosky
```

Keep that token: it is the admin of a new installation, as [Security](./security.md#the-first-token) explains. For a Redis Cluster, add `-e COSKY_STORE_REDIS_CLUSTER=true`.

Any [property](./properties.md) can be set as an environment variable or an argument. `--dev` runs in memory without sign-in, for trying things out only.

## Kubernetes

The repository holds [Kustomize](https://kustomize.io) manifests: a Deployment of three replicas, a Service and a PodDisruptionBudget.

```bash
kubectl create secret generic cosky-redis --from-literal=uri=redis://redis:6379
```

```bash
kubectl create secret generic cosky-auth --from-literal=bootstrap-token="cosky_$(openssl rand -hex 8)_$(openssl rand -hex 16)"
```

```bash
kubectl apply -k k8s/base
```

For a Redis Cluster, apply `k8s/overlays/redis-cluster` instead. Your own overlay of a few lines can set the namespace, the image tag or the resources: that one shows the shape.

Probes use `/actuator/health/liveness` and `/actuator/health/readiness`. A replica reports ready once it has loaded the namespaces, which for 50,000 instances and 10,000 Configs [took 2 to 3.5 seconds](./performance.md).

## Rolling updates

On shutdown a replica ends its streams, after up to 5 seconds, and clients reconnect to another one and resume where they were. If they fell out of the Watch window meanwhile, they read a fresh snapshot, which costs a little more and loses nothing. `cosky_watch_gone_total` shows whether this happens, and `cosky.watch.window` is the setting to raise if it does.

## The Dashboard on its own

The image serves the Dashboard at `/`. To host it elsewhere, put it behind a reverse proxy that also forwards `/v1` to the server: the page expects the API on its own origin, and the server has no CORS.

## Observability

Metrics are at `/actuator/prometheus`:

| Metric | Watch for |
|---|---|
| `cosky_cache_lag_seconds{namespace}` | Writes taking long to reach a replica |
| `cosky_store_apply_seconds` | Write latency |
| `cosky_watch_active` | Watches on this replica |
| `cosky_watch_gone_total{reason}` | Clients having to start over: `slow`, `compacted`, `epoch`, `regression` |
| `cosky_lease_active{namespace}`, `cosky_lease_expired_total` | Registered processes and expiries |
| `cosky_lease_protection_active{namespace}` | Reaping paused by a mass expiry |
| `cosky_revision_regression_total` | Redis lost writes |
| `cosky_anti_entropy_drift_total{namespace}` | A replica's view differed from the store, which should never happen |
| `cosky_auth_failures_total{reason}` | Failed sign-ins |

**Traces** cover gRPC and HTTP at a 10% sample. To export them, set `management.opentelemetry.tracing.export.otlp.endpoint`, such as `http://collector:4318/v1/traces`.

**Audit**: every write and every authentication failure is one JSON line in the log. Collect those with the rest of your logs. See [Security](./security.md#audit).

## Sizing

[Performance](./performance.md) has the measurements. As a starting point, 512 MB of heap holds a namespace of 10,000 objects with room to spare, and memory grows by about 1.5 KB per object. The image sets `-XX:MaxRAMPercentage=75` and compact object headers, so give the container what the heap should be, plus a quarter.
