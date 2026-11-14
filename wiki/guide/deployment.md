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

If Redis loses everything, the server sets it up again by itself within about 30 seconds, as on a first start: the system namespace, `default`, and the admin from the bootstrap token, so leave that token set. The rest is gone: other namespaces, Configs, Roles, RoleBindings, and ServiceAccounts with their tokens. Create them again, or import a [zip export](./configuration.md#import-and-export) of your Configs. With auth on, applications keep serving what they know and stay unregistered until they have a new token. With auth off, they follow what is there once their namespace exists, and register themselves.

### Credentials and TLS

They go in `cosky.store.redis.uri`:

| Redis with | URI |
|---|---|
| A password | `redis://:password@redis:6379` |
| An ACL user | `redis://cosky:password@redis:6379` |
| TLS | `rediss://cosky:password@redis:6380` |
| Sentinel | `redis-sentinel://cosky:password@s1:26379,s2:26379,s3:26379#mymaster`. The credentials are those of the master and its replicas; Sentinels without a password were tried. |
| A Cluster | `redis://cosky:password@node:6379`, with `cosky.store.redis.cluster=true` |

Over TLS, the server checks Redis's certificate against the JVM's trusted authorities. For a private one, give the image a trust store: `JAVA_OPTS="-Djavax.net.ssl.trustStore=/tls/trust.p12 -Djavax.net.ssl.trustStorePassword=..."`. A certificate the server does not trust stops it at start. `?verifyPeer=NONE` skips the check, for a test only.

An ACL user needs CoSky's keys and channel, which all begin with `cosky:`, and no dangerous command but `INFO`:

```
ACL SETUSER cosky on >password ~cosky:* &cosky:* +@all -@dangerous +info
```

It was tried on a single node with every kind of write, Watch and leases expiring, and on Sentinel and a Cluster through a failover. Without `+info`, the server cannot check the prerequisites above: it says so in its log, and starts.

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
  ahoowang/cosky:8.0.0-beta.3
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

A replica stays ready while Redis is away, on purpose. Redis is shared, so a failover would otherwise take every replica out of the Service at once, while they can still serve what they know. The cost is that a replica cut off from Redis on its own goes on serving what it last knew. Replicas that disagree on `cosky_cache_revision{namespace}` for more than a minute show this, and restarting that one ends it.

## Rolling updates

In Kubernetes a stopping replica first waits 5 seconds, for the Service to stop sending it connections. Then it ends its streams, after up to 5 seconds, and clients reconnect to another one and resume where they were. If they fell out of the Watch window meanwhile, they read a fresh snapshot, which costs a little more and loses nothing. `cosky_watch_gone_total` shows whether this happens, and `cosky.watch.window` is the setting to raise if it does, along with `cosky.watch.window-bytes` if your Configs are large.

Draining a node stops its replica the same way. The PodDisruptionBudget lets one replica go at a time, so draining two nodes at once waits for the first replacement to be ready. On a k3s cluster of four nodes, draining each node in turn and then two at once failed no write, no read of a Config in an application and no registration.

## The Dashboard on its own

The image serves the Dashboard at `/`. To host it elsewhere, put it behind a reverse proxy that also forwards `/v1` to the server: the page expects the API on its own origin, and the server has no CORS.

## Observability

Metrics are at `/actuator/prometheus`:

| Metric | Watch for |
|---|---|
| `cosky_cache_lag_seconds{namespace}` | Writes taking long to reach a replica |
| `cosky_cache_revision{namespace}` | Replicas that disagree for long: one of them is cut off from Redis |
| `cosky_store_apply_seconds` | Write latency, a histogram: `histogram_quantile(0.99, sum by (le) (rate(cosky_store_apply_seconds_bucket[5m])))` is the p99 over every replica |
| `cosky_watch_active` | Watches on this replica |
| `cosky_watch_events_sent_total` | Events sent to watchers |
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

Large Configs need their own thought, because what is kept for resuming includes their content:

- **A replica** keeps at most `cosky.watch.window-bytes` (64 MB) of recent changes per namespace, on top of every object. Past that, a client that fell behind reads a fresh snapshot.
- **Redis** keeps the same in its log, plus the last 20 revisions of each Config and every object. Writing one 1 MB Config 800 times took 92 MB: 64 MB of log and 20 MB of history. At `maxmemory`, writes fail rather than anything being evicted.
