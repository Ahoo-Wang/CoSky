---
title: Troubleshooting
description: What an operator of the server sees when something goes wrong, what causes it, and what to do.
---

# Troubleshooting

This page is for operators of the server. Each entry starts from what you see, a log line, a metric or an error reason, and says what causes it and what to do. [Deployment](./deployment.md#observability) lists the metrics, and [REST API](./rest-api.md#conventions) the error reasons.

## The server does not start

The server says why at the end of its log, under `APPLICATION FAILED TO START`, with a description and an action:

| The description begins | Do |
|---|---|
| `The secret of the bootstrap token, after its second _, has ... characters` | Set `COSKY_BOOTSTRAP_TOKEN` to a new token, such as `cosky_$(openssl rand -hex 8)_$(openssl rand -hex 16)`, or remove it. With authentication on, the server checks it at every start, even once it no longer reads it. |
| `The bootstrap token must look like cosky_<id>_<secret>` | The same. |
| `cosky.lease.reaper-interval is 0s: it must be longer than 0.` | A setting that cannot work. The action names the property, its environment variable and its default. A duration without a unit is in seconds. |
| `CoSky cannot reach Redis` | Check `COSKY_STORE_REDIS_URI`: the host, the port, the password, and `rediss://` for TLS. |

Two failures end in a stack trace instead. Look for its last cause:

- **`This Redis cannot back CoSky:`**, followed by what is wrong, such as `maxmemory_policy is allkeys-lru, and eviction silently deletes data; it must be noeviction.` or a version older than 7.0. Change Redis, as [Deployment](./deployment.md#redis) says. A managed Redis may need its parameter group changed.
- **`With an OIDC issuer set, cosky.auth.oidc.audience or client-id is required.`** Set `cosky.auth.oidc.client-id`.

A Redis certificate the JVM does not trust also stops the start: see [Credentials and TLS](./deployment.md#credentials-and-tls).

Three messages come at start without stopping it:

- **`Cannot check the Redis prerequisites, since INFO failed`**: the Redis user may not run `INFO`, so nothing was checked. Give it `+info`.
- **`No RoleBinding exists and no bootstrap token is set, so nobody can sign in.`** Set a [bootstrap token](./security.md#the-first-token) and restart.
- **`Authentication is off (cosky.auth.enabled=false)`**, an error: every caller is admin, and `cosky_auth_enabled` is 0. Turn it on.

An unknown `cosky.*` key stops nothing, so a misspelt property is silently ignored: check the spelling against [Configuration Properties](./properties.md).

## A replica is not ready

The readiness probe, `/actuator/health/readiness`, answers `OUT_OF_SERVICE` with status 503 until the replica has loaded the system namespace, `_system`, and then every namespace it lists. That takes seconds: 50,000 instances and 10,000 Configs [took 2 to 3.5](./performance.md). Meanwhile a request for a namespace still loading is refused with `REPLICA_NOT_READY`, and clients go to another replica.

To see what it waits for, set `management.endpoint.health.show-details=always`. The `coSkyReadiness` component of the readiness group then lists those namespaces under `loading`. `_system` alone means the replica has not read the access rules yet, so every call with a token is refused with `REPLICA_NOT_READY` too.

If it stays that way, the replica cannot read Redis. Its log repeats `Following namespace <name> failed; loading it again.` with the cause, and its `cosky_store_failures_total` rises. Check its network path to Redis, and Redis itself.

A replica that is ready stays ready when Redis goes away. That is the next entry.

## A replica cut off from Redis

A replica stays ready while Redis is away, on purpose: Redis is shared, and a failover would otherwise take every replica out of the Service at once. So a replica cut off on its own keeps serving what it last knew. Its reads answer from that, and its writes fail with `STORE_UNAVAILABLE`.

It shows as:

- `rate(cosky_store_failures_total[1m]) > 0` on that replica, even when it is idle, since its reaper asks Redis for a lock every second;
- its `cosky_cache_revision{namespace}` staying behind the other replicas';
- `Reading the log of namespace <name> failed; trying again` in its log.

If one replica shows it, its own path to Redis is broken: restart it, or move it to another node. If every replica shows it, Redis itself is down or failing over, which is the next entry.

## Writes fail with STORE_UNAVAILABLE

`STORE_UNAVAILABLE` (503) means that the server could not reach Redis, or that Redis did not answer within 5 seconds. Reads go on from the replicas' memory. Applications keep their registrations, and renew them once Redis answers again. The reaper then waits 30 seconds before it removes anything, so that they can.

`cosky_store_failures_total{operation}` says what failed: `apply` for writes, `tryLock` for the reaper's lock. The usual causes:

- **Redis is down, or failing over.** A failover of a Cluster or Sentinel took [10 to 16 seconds](./performance.md#a-redis-cluster-failover) when measured.
- **`min-replicas-to-write` on a primary without its replicas.** Redis refuses writes until one is back, as it should: see [Backup and Restore](./backup.md#what-redis-must-do). `INFO replication` on the primary shows the replicas it has.
- **Redis at `maxmemory`.** With `noeviction`, Redis refuses writes rather than drop anything. Give it more memory, or see [Sizing](./deployment.md#sizing) for what takes it.

## Errors that answer 503 or 504

| Reason | Means | Do |
|---|---|---|
| `STORE_UNAVAILABLE` | Redis cannot be reached, or did not answer within 5 seconds | See [the entry above](#writes-fail-with-store-unavailable). |
| `REPLICA_NOT_READY` | This replica is still loading the namespace the error names, or is behind the position a client asked for. `_system` stands for the access rules. | Nothing, if it passes: clients go to another replica. If one replica keeps answering it, see [A replica is not ready](#a-replica-is-not-ready). |
| `IDENTITY_PROVIDER_UNAVAILABLE` | The OIDC provider, or its keys, cannot be reached. The token may be fine. | Check the provider, and the server's way to the issuer. Applications with ServiceAccount tokens go on; sign in with such a token meanwhile. |
| `REVISION_REGRESSION` | Redis lost writes, and the replica is loading the namespace again | It passes in moments. See [Redis lost writes](#redis-lost-writes). |
| `MIN_REVISION_TIMEOUT` (504) | A read with `minRevision` waited 3 seconds for this replica to reach that revision | The replica is behind: check its `cosky_cache_lag_seconds`, and whether it is [cut off from Redis](#a-replica-cut-off-from-redis). |

The four reasons of 503 mean the same to a client: try another replica, or try later. They differ to tell you whether to look at Redis, the identity provider or the replica.

## Calls refused for their token

`cosky_auth_failures_total{reason}` counts them. Each is also a line in the [audit log](./security.md#audit), with the result `UNAUTHENTICATED` and the caller's address in `remote`.

| Reason | Means | Do |
|---|---|---|
| `missing` | No token | Find the caller by `remote`: an application without `cosky.token`, or a script. |
| `invalid` | A token that is malformed, unknown, revoked or expired, or an ID token the OIDC checks refuse | Usually a token revoked or rotated while still in use. |
| `unavailable` | It could not be decided: the access rules are loading, or the identity provider is out of reach | It comes with an outage, which `REPLICA_NOT_READY` on `_system` or `IDENTITY_PROVIDER_UNAVAILABLE` shows. |

An application whose token is refused does not start. It ends with `CoSky refused to register service <name> in namespace <namespace>: UNAUTHENTICATED`. One whose token is revoked while it runs loses its streams, and stays `DEGRADED`.

`PERMISSION_DENIED` (403) is not counted there: the token is valid, and its roles do not allow the call. `GET /v1/me` with that token shows the rules it has.

## Applications stay DEGRADED

The starter's health indicator `cosky` stays `UP`, with `connection: DEGRADED`, while the client is not connected to a server, because a restart would not help. The application serves what it knows meanwhile. Its log says why at each attempt: `The watch stream to <endpoint> failed:`, followed by the status.

| The status | Cause |
|---|---|
| `UNAVAILABLE: io exception (Connection refused ...)` | Nothing listens at `cosky.endpoints`. The port is the gRPC one, 9090, not 8080. |
| `UNAUTHENTICATED` | The token was revoked or has expired: see [the entry above](#calls-refused-for-their-token). |
| `UNAVAILABLE: This replica cannot load namespace <namespace> from its store.`, or one that `has not loaded` it or `is behind` | The replica is [not ready](#a-replica-is-not-ready), or behind. The client opens a new connection, which a Kubernetes Service may route to another replica. |

The client backs off up to 10 seconds between attempts. A server with [TLS](./security.md#tls) needs `cosky.tls.enabled` in the application.

## An application starts with old values

The client keeps a [snapshot](./sdk.md#snapshots) of what it watches in `~/.cosky`, and starts from it, `DEGRADED`, until it reaches a server. A read waits up to a second for the server to answer, so it serves what its last run saw only while no server answers, or one answers slower than that: a Config's old content, or an old list of instances. An empty list fails a call by that service's name. Once connected, it follows the server, and a refresh replaces the old values.

Snapshots are kept per set of endpoints and token. So a development server at the same address as the one before, such as `localhost:9090`, finds the applications starting from what the earlier one served, though it kept nothing. To start clean, delete `~/.cosky`, or set `cosky.snapshot.enabled=false`.

## Clients start over: cosky_watch_gone_total rises

A client that cannot resume where it was reads a fresh snapshot of what it watches. That costs a read, and loses nothing. The reason says why:

- **`slow`**: it fell out of the window while following, because delivery could not keep up.
- **`compacted`**: it came back from a revision older than the window, as after a reconnection during a rolling update.
- **`epoch`**: the namespace's timeline was replaced, because Redis lost writes or data, or the namespace was deleted and created again.
- **`regression`**: it came back from a position Redis no longer has.

For `slow` and `compacted`, the window is too small for your rate of writes and your reconnection gaps. Raise `cosky.watch.window`, 10,000 events by default, to at least the peak events per second times the longest reconnection gap in seconds, and `cosky.watch.window-bytes` too if your Configs are large. Both also bound the log in Redis, so Redis and each replica keep more: see [Sizing](./deployment.md#sizing). [Performance](./performance.md#the-event-window) shows when it happens.

For `epoch` and `regression`, the window is not the cause: see [Redis lost writes](#redis-lost-writes).

## Reaping is paused

`cosky_lease_protection_active{namespace}` is 1, and the log has, as an error, `<n> of <m> leases expired in namespace <namespace>; pausing the reaper.` More than half of the namespace's leases (`cosky.lease.protection-threshold`) expired at once, out of at least 10 (`protection-min-leases`). That looks like a partition between applications and servers, so the reaper keeps their instances rather than remove live ones. Only the reaper's leader reports the gauge: take `max by (namespace)` over the replicas.

Check whether the applications reach the servers: their logs say `Renewing the lease of namespace <namespace> at <endpoint> failed`, and the network or load balancer in front of port 9090 may be the cause. Once they renew, the share falls, and reaping starts again after a grace of 30 seconds.

If the processes really are gone, as on nodes that failed, reaping starts again by itself after `cosky.lease.protection-max-duration`, 5 minutes. Until then their instances stay listed, and callers need retries.

## cosky_reaper_failures_total rises

The store failed on that namespace while the reaper was reaping or checking it. The reaper goes on with the other namespaces, and this one waits out a grace of its own. The log says `The store failed on namespace <namespace>; it waits out a grace, and the others go on.`, and later `The store answers on namespace <namespace> again`.

On a Redis Cluster, look at the shard that holds the namespace, and its failover. [Service Discovery](./service-discovery.md#when-things-fail) says how a shard's failure affects reaping. When the store fails as a whole, the log says `A reaper tick failed.` instead.

## Redis lost writes

`cosky_revision_regression_total` rises, and a replica logs as an error `Namespace <namespace> went back from ...; replacing the epoch.` or `Namespace <namespace> diverged, now at ...; loading it again.` Redis lost writes it had acknowledged, in a failover or because an older snapshot was restored.

CoSky replaces the namespace's epoch. Every replica loads the namespace again, every watcher starts over, `cosky_watch_gone_total{reason="epoch"}` rises, and applications register again what is missing. What Redis lost stays lost: [Backup and Restore](./backup.md#when-redis-loses-data) says how to find it.

`The system namespace has no state: the store lost its data.`, as an error, means that Redis lost everything. The server sets it up again as on a first start: see [Backup and Restore](./backup.md#when-redis-loses-everything).

`cosky_anti_entropy_drift_total` rising, with `<n> objects of namespace <namespace> differ from the store; replacing the epoch.`, means that a replica's view differed from Redis. It should never happen, and recovers as above. Please [report it](https://github.com/Ahoo-Wang/CoSky/issues/new/choose) with the log.

## The Dashboard behind a reverse proxy

[Dashboard](./dashboard.md#behind-a-reverse-proxy) says what the proxy needs. Each missing piece has its own symptom:

| You see | The proxy |
|---|---|
| A blank page, or its files not found, at `/cosky` | Does not redirect `/cosky` to `/cosky/`, so the browser resolves the page's addresses against `/` |
| The page, but every call fails with 404 | Does not pass `/cosky/v1/` on to the server's `/v1/` |
| Changes only after a reload | Buffers responses, which holds back the stream of changes. In nginx, `proxy_buffering off`. |
| Calls blocked by CORS, with the files served from elsewhere | Serves the API from another origin than the page. The server has no CORS, so serve `v1/` next to the page. |

After signing in with OIDC, the provider returns to the address it was given. Register the Dashboard's address with its trailing slash, such as `https://example.com/cosky/`.
