---
title: Deployment
description: Run the CoSky server in production on Docker or Kubernetes, with what it asks of Redis.
---

# Deployment

The server is one image, `ahoowang/cosky` (also `ghcr.io/ahoo-wang/cosky`), holding the Dashboard too. Run at least two replicas: they share nothing but Redis, and clients move between them by themselves.

| Port | Serves |
|---|---|
| `8080` | REST API, Dashboard, and Actuator unless it has [a port of its own](#actuator-on-its-own-port) |
| `9090` | gRPC, for applications |

## Redis

CoSky needs **Redis 7.0 or later, or Valkey 8**, with `maxmemory-policy noeviction`. The server checks both at start and refuses to run otherwise, because an eviction would silently lose objects and Watch history. It works on a single node, Sentinel or a Cluster, and each namespace stays in one slot.

Everything that lasts is in Redis. Turn its AOF on, keep its data on a disk that outlives the container, and set `min-replicas-to-write 1` on a primary with replicas. [Backup and Restore](./backup.md) says why, how to back it up, and what happens when Redis loses writes or everything.

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

`JAVA_OPTS` adds to the JVM flags the image comes with (heap at 75% of the container's memory, exit on out-of-memory, and what JDK 25 needs), rather than replacing them. The image is Eclipse Temurin's JRE 25 on Ubuntu 24.04 LTS, and runs as user and group 999.

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
docker run -d --name redis --network cosky -v cosky-redis:/data redis:7.4 --appendonly yes
```

```bash
export COSKY_BOOTSTRAP_TOKEN="cosky_$(openssl rand -hex 8)_$(openssl rand -hex 16)"
echo "$COSKY_BOOTSTRAP_TOKEN"
```

```bash
docker run -d --name cosky --network cosky -p 8080:8080 -p 9090:9090 \
  -e COSKY_STORE_REDIS_URI=redis://redis:6379 \
  -e COSKY_BOOTSTRAP_TOKEN \
  ahoowang/cosky:8.0.0-rc.2
```

Keep that token: it is the admin of a new installation, as [Security](./security.md#the-first-token) explains. Its secret takes at least 22 letters and digits, and the server refuses to start with a shorter one. For a Redis Cluster, add `-e COSKY_STORE_REDIS_CLUSTER=true`.

Any [property](./properties.md) can be set as an environment variable or an argument. `--dev` runs in memory without sign-in, for trying things out only.

## Actuator on its own port

Actuator's health, info and metrics need no token, and the metrics name your namespaces. On port 8080 they are open to anyone who reaches the API, so in production give them a port that only the probes and Prometheus reach. `MANAGEMENT_SERVER_PORT` (`management.server.port`) moves all of Actuator there. Port 8080 then answers 404 for `/actuator`, and the new port answers nothing but `/actuator`:

```bash
docker run -d --name cosky --network cosky -p 8080:8080 -p 9090:9090 -p 127.0.0.1:8081:8081 \
  -e COSKY_STORE_REDIS_URI=redis://redis:6379 \
  -e COSKY_BOOTSTRAP_TOKEN \
  -e MANAGEMENT_SERVER_PORT=8081 \
  ahoowang/cosky:8.0.0-rc.2
```

- **Kubernetes:** `k8s/base` does this. It probes the port named `management` (8081) and leaves it out of the Service, so nothing that reaches the API through the Service, such as an Ingress, reaches Actuator. Prometheus scrapes the pods directly, by the `prometheus.io/*` annotations or with a PodMonitor on the port `management`, and needs no Service for it.
- **The image's health check** follows `MANAGEMENT_SERVER_PORT`. Set the port with that variable, or give the container a check of its own with `--health-cmd`.
- **The default stays 8080** in 8.0, so probes and scrapers already pointed there keep working until you move them.
- **The probes then ask the management server**, not port 8080 itself. To ask 8080 too, `management.endpoint.health.probes.add-additional-paths=true` serves liveness and readiness there, at `/livez` and `/readyz`, with the status alone.
- **With TLS,** Actuator's port speaks HTTPS too, unless you turn it off there: see [Security](./security.md#tls).

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

Each replica requests half a CPU, with no CPU limit, and 2 GiB of memory, which is also its memory limit, as [Sizing](#sizing) explains: change both memory values together. Replicas spread across nodes where they can, and the comments in the manifests say why each setting is there.

Probes use `/actuator/health/liveness` and `/actuator/health/readiness`, on [Actuator's own port](#actuator-on-its-own-port). A replica reports ready once it has loaded the namespaces, which for 50,000 instances and 10,000 Configs [took 2 to 3.5 seconds](./performance.md).

A replica stays ready while Redis is away, on purpose. Redis is shared, so a failover would otherwise take every replica out of the Service at once, while they can still serve what they know. The cost is that a replica cut off from Redis on its own goes on serving what it last knew. Replicas that disagree on `cosky_cache_revision{namespace}` for more than a minute show this, and restarting that one ends it. [Troubleshooting](./troubleshooting.md#a-replica-cut-off-from-redis) has the other signs.

## Rolling updates

[Upgrading](./upgrading.md) says in what order to upgrade servers and applications. In Kubernetes a stopping replica first waits 5 seconds, for the Service to stop sending it connections. Then it ends its streams, after up to 5 seconds, and clients reconnect to another one and resume where they were. If they fell out of the Watch window meanwhile, they read a fresh snapshot, which costs a little more and loses nothing. `cosky_watch_gone_total` shows whether this happens, and `cosky.watch.window` is the setting to raise if it does, along with `cosky.watch.window-bytes` if your Configs are large.

Clients keep their connection, so after a rolling update or a scale-out the replicas that started last have few of them. Each replica ends a connection after about 30 minutes, give or take a tenth, and gives its streams 5 seconds, so clients spread out again: they reconnect through the Service, which is all the endpoint an application needs, and resume where they were. `spring.grpc.server.keepalive.connection.max-age` sets this, and `grace-period` under it the 5 seconds.

Draining a node stops its replica the same way. The PodDisruptionBudget lets one replica go at a time, so draining two nodes at once waits for the first replacement to be ready. On a k3s cluster of four nodes, draining each node in turn and then two at once failed no write, no read of a Config in an application and no registration.

## The Dashboard on its own

The image serves the Dashboard at `/`. To serve it under a path of its own, such as `https://example.com/cosky/`, or to host its files elsewhere, put it behind a reverse proxy that also forwards `v1/` under the same path to the server's `/v1`: the page expects the API next to it, on its own origin, and the server has no CORS. [Dashboard](./dashboard.md#behind-a-reverse-proxy) has what the proxy needs, with an nginx example.

## Observability

Metrics are at `/actuator/prometheus`. [Troubleshooting](./troubleshooting.md) says what to do when one of them moves.

| Metric | Watch for |
|---|---|
| `cosky_cache_lag_seconds{namespace}` | Writes taking long to reach a replica |
| `cosky_cache_revision{namespace}` | Replicas that disagree for long: one of them is cut off from Redis |
| `cosky_store_apply_seconds` | Write latency, a histogram: `histogram_quantile(0.99, sum by (le) (rate(cosky_store_apply_seconds_bucket[5m])))` is the p99 over every replica |
| `cosky_store_failures_total{operation}` | This replica failing to reach the store, by the store's operation, such as `tryLock` or `apply`. The replica stays ready meanwhile, and every replica's reaper asks the store for its lock each second, so one cut off from Redis counts even when idle: alert on `rate(cosky_store_failures_total[1m]) > 0` for a few minutes |
| `cosky_watch_active` | Watches on this replica |
| `cosky_watch_events_sent_total` | Events sent to watchers |
| `cosky_watch_gone_total{reason}` | Clients having to start over: `slow`, `compacted`, `epoch`, `regression` |
| `cosky_lease_active{namespace}`, `cosky_lease_expired_total{namespace}` | Registered processes and expiries. Only the reaper's leader reports them, so take `max by (namespace)` of the gauge over the replicas, which also covers a change of leader |
| `cosky_lease_protection_active{namespace}` | Reaping paused by a mass expiry, reported by the reaper's leader as well |
| `cosky_reaper_failures_total{namespace}` | The store failing on one namespace, such as one on a Redis Cluster shard that is down, while the reaper goes on with the others |
| `cosky_revision_regression_total` | Redis lost writes |
| `cosky_anti_entropy_drift_total{namespace}` | A replica's view differed from the store, which should never happen |
| `cosky_auth_failures_total{reason}` | Calls refused for their token: `missing` came without one, `invalid` with one that is malformed, unknown, revoked or expired, or an ID token the OIDC checks refuse, and `unavailable` could not be decided, while the access rules load or the identity provider is out of reach. Alert on `missing` and `invalid`, which point at a client; `unavailable` comes with an outage that other alerts catch |
| `cosky_auth_enabled` | `0`: authentication is off, and every caller is admin. The server also logs an error at start when it runs on Redis this way |

A namespace's series go when it is deleted.

**Traces** cover gRPC and HTTP at a 10% sample. To export them, set `management.opentelemetry.tracing.export.otlp.endpoint`, such as `http://collector:4318/v1/traces`.

**Audit**: every write and every authentication failure is one JSON line in the log. Collect those with the rest of your logs. See [Security](./security.md#audit).

## Sizing

[Performance](./performance.md) has the measurements. As a starting point, 512 MB of heap holds a namespace of 10,000 objects with room to spare, and memory grows by about 1.5 KB per object. The image sets `-XX:MaxRAMPercentage=75` and compact object headers, so the heap is three quarters of the container's memory limit: give the container a third more than the heap should be, and request as much memory as the limit, as `k8s/base` does with 2 GiB for a heap of 1.5 GiB.

Large Configs need their own thought, because what is kept for resuming includes their content:

- **A replica** keeps at most `cosky.watch.window-bytes` (64 MB) of recent changes per namespace, on top of every object. Past that, a client that fell behind reads a fresh snapshot.
- **Redis** keeps the same in its log, plus the last 20 revisions of each Config and every object. Writing one 1 MB Config 800 times took 92 MB: 64 MB of log and 20 MB of history. At `maxmemory`, writes fail rather than anything being evicted.
