---
title: Configuration Properties
description: Every setting of the server and the Spring Boot starter, with its default.
---

# Configuration Properties

A duration takes a unit, such as `500ms`, `30s` or `5m`. Without one it is in seconds, on the server and in the starter alike, so `COSKY_LEASE_REAPER_GRACE=30` is 30 seconds. Both ship Spring Boot's configuration metadata, so an IDE completes and explains the `cosky.*` properties below.

## Server

Set these in `application.yaml`, as arguments, or as environment variables: `cosky.store.redis.uri` is `COSKY_STORE_REDIS_URI`.

A value that cannot work stops the start, with a message that names the property and the value:

- a duration, count or size of 0 or less. `reaper-grace` and `protection-max-duration` may be 0, which turns the grace or the pause off, as may `protection-min-leases`;
- a `protection-threshold` outside 0 to 1;
- a `lock-ttl` no longer than `reaper-interval`: the lock would lapse between two renewals, and leadership change hands.

Keys the server does not know are ignored, such as `COSKY_SERVICE_HOST` and `COSKY_PORT`, which Kubernetes sets for a Service named `cosky`.

### Store

| Property | Default | Meaning |
|---|---|---|
| `cosky.store.type` | `redis` | `redis`, or `memory` for development: nothing survives the process |
| `cosky.store.redis.uri` | `redis://localhost:6379` | Also takes Sentinel URIs, such as `redis-sentinel://s1:26379,s2:26379#mymaster`: the server follows the master the Sentinels name. A command waits at most 5 seconds for Redis, unless the URI sets its own, such as `?timeout=10s` |
| `cosky.store.redis.cluster` | `false` | The URI is a seed of a Redis Cluster |
| `cosky.store.redis.tail-poll-interval` | `1s` | How often each namespace is read when no hint arrives |

### Authentication

| Property | Default | Meaning |
|---|---|---|
| `cosky.auth.enabled` | `true` | Off, everyone may do everything. Only for development: on Redis the server logs an error at start, and `cosky_auth_enabled` is 0. |
| `cosky.auth.bootstrap-token` | `$COSKY_BOOTSTRAP_TOKEN` | Becomes the `admin` ServiceAccount while no role binding exists. Its secret takes at least 22 letters and digits, see [Security](./security.md#the-first-token) |
| `cosky.auth.namespace-accounts` | `true` | Gives every namespace a ServiceAccount of its own name, bound to `client` there, whose token its applications share |
| `cosky.auth.oidc.issuer` | — | Turns OIDC on, together with `client-id` |
| `cosky.auth.oidc.client-id` | — | The Dashboard's client |
| `cosky.auth.oidc.audience` | The client | What a token must be for |
| `cosky.auth.oidc.groups-claim` | `groups` | The claim holding group names |
| `cosky.auth.oidc.scopes` | `openid, profile` | What the Dashboard asks for. Some providers send groups only when asked. |

### Watch and reads

| Property | Default | Meaning |
|---|---|---|
| `cosky.watch.window` | `10000` | Events kept for resuming, and in the store's log. Should be at least the peak event rate times the longest reconnection gap. |
| `cosky.watch.window-bytes` | `64MB` | The same in bytes, per namespace, in memory and in the store's log. |
| `cosky.watch.progress-interval` | `10s` | Bookmarks sent on idle watches |
| `cosky.watch.max-per-stream` | `1000` | Watches on one stream |
| `cosky.watch.anti-entropy-interval` | `5m` | How often a replica compares its view with the store |
| `cosky.read.min-revision-timeout` | `3s` | How long a read or a resume waits for a replica to catch up |
| `cosky.view.topology-flush-interval` | `30s` | How often a replica records who watches whom. Edges expire after three times this. |

### Leases

| Property | Default | Meaning |
|---|---|---|
| `cosky.lease.reaper-interval` | `1s` | How often expired leases are looked for |
| `cosky.lease.reaper-grace` | `30s` | No reaping for this long after becoming leader, or after failing to reach the store; a failure in one namespace holds back that namespace only |
| `cosky.lease.reaper-max-per-tick` | `100` | Leases reaped per namespace per round |
| `cosky.lease.lock-ttl` | `15s` | How long the reaper's leadership lasts without renewal |
| `cosky.lease.protection-threshold` | `0.5` | Reaping pauses when more than this share of a namespace's leases expire at once |
| `cosky.lease.protection-min-leases` | `10` | Namespaces with fewer leases have no such pause |
| `cosky.lease.protection-max-duration` | `5m` | How long the pause lasts at most |

### From Spring

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | REST, Dashboard and Actuator |
| `management.server.port` | `server.port` | Actuator on [a port of its own](./deployment.md#actuator-on-its-own-port), as in `k8s/base` |
| `spring.grpc.server.port` | `9090` | gRPC |
| `server.ssl.bundle`, `spring.grpc.server.ssl.bundle` | — | [TLS](./security.md#tls) |
| `spring.grpc.server.shutdown.grace-period` | `5s` | How long open streams get on shutdown |
| `spring.grpc.server.keepalive.connection.max-age` | `30m` | A client connection is ended after this, give or take a tenth, so that clients [spread over the replicas again](./deployment.md#rolling-updates) |
| `spring.grpc.server.keepalive.connection.grace-period` | `5s` | How long the streams of such a connection get |
| `management.tracing.sampling.probability` | `0.1` | Share of requests traced |
| `management.opentelemetry.tracing.export.otlp.endpoint` | — | Where traces go |
| `springdoc.api-docs.enabled` | `true` | The REST API's document at `/v3/api-docs`, and Swagger UI on it at `/swagger-ui.html`; `springdoc.swagger-ui.enabled=false` keeps the document only |

### Fixed limits

These cannot be set in 8.0.

| Limit | Value |
|---|---|
| Config content | 1 MiB |
| Config history | The last 20 revisions |
| Lease TTL | 5 to 300 seconds, 30 by default |
| Instance metadata | 64 entries; keys up to 63 characters, values up to 1024 |
| Labels | 32 entries; keys up to 63 characters, values up to 253 |
| Description | 1024 characters |
| Tokens per ServiceAccount | 2 |
| One message | 2 MiB; larger results come in pages or parts |
| One page of a list | 500 by default, 1000 at most |
| A REST request's body | 12 MiB, what the largest object takes in JSON with every character escaped; an archive to import 64 MiB |

## Spring Boot starter

| Property | Default | Meaning |
|---|---|---|
| `cosky.enabled` | `true` | Everything below |
| `cosky.endpoints` | `localhost:9090` | Servers to try in turn |
| `cosky.token` | — | Sent on every call. A blank one counts as none, so `${COSKY_TOKEN:}` can stand beside `cosky.token-file`. |
| `cosky.token-file` | — | A file holding the token instead, such as a mounted secret. Read again once it changes, so a rotated token needs no restart. |
| `cosky.namespace` | `default` | |
| `cosky.tls.enabled` | `false` | Connect over TLS |
| `cosky.tls.trusted-certificates` | — | A PEM file to trust, for a private CA. The JVM's trust store decides otherwise. |
| `cosky.snapshot.enabled` | `true` | Keep views on disk |
| `cosky.snapshot.dir` | `~/.cosky` | |
| `cosky.snapshot.include-config` | `true` | Snapshots are not encrypted, so false keeps Config contents off the disk |
| `cosky.registry.enabled` | `true` | Register this application |
| `cosky.registry.weight` | `1` | `0` takes no traffic |
| `cosky.registry.metadata` | — | |
| `cosky.discovery.enabled` | `true` | `DiscoveryClient` and LoadBalancer |
| `cosky.discovery.load-timeout` | `5s` | How long the first read of a service waits |
| `cosky.config.load-timeout` | `5s` | How long importing a Config waits |
| `cosky.config.refresh.enabled` | `true` | Publish `RefreshEvent` on changes |
| `management.health.cosky.enabled` | `true` | The health indicator, `cosky` in `/actuator/health` |

The service name is `spring.application.name`. `spring.cloud.service-registry.auto-registration.enabled: false` also stops registering.

An application's own bean takes the place of the starter's: one named `coskyHealthIndicator`, or one of type `CoSkyDiscoveryClient`, `CoSkyReactiveDiscoveryClient`, `CoSkyServiceRegistry` or `CoSkyAutoServiceRegistration`.
