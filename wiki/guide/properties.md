---
title: Configuration Properties
description: Every setting of the server and the Spring Boot starter, with its default.
---

# Configuration Properties

## Server

Set these in `application.yaml`, as arguments, or as environment variables: `cosky.store.redis.uri` is `COSKY_STORE_REDIS_URI`.

### Store

| Property | Default | Meaning |
|---|---|---|
| `cosky.store.type` | `redis` | `redis`, or `memory` for development: nothing survives the process |
| `cosky.store.redis.uri` | `redis://localhost:6379` | Also takes Sentinel URIs |
| `cosky.store.redis.cluster` | `false` | The URI is a seed of a Redis Cluster |
| `cosky.store.redis.tail-poll-interval` | `1s` | How often each namespace is read when no hint arrives |

### Authentication

| Property | Default | Meaning |
|---|---|---|
| `cosky.auth.enabled` | `true` | Off, everyone may do everything. Only for development. |
| `cosky.auth.bootstrap-token` | `$COSKY_BOOTSTRAP_TOKEN` | Becomes the `admin` ServiceAccount on the first start |
| `cosky.auth.oidc.issuer` | — | Turns OIDC on, together with `client-id` |
| `cosky.auth.oidc.client-id` | — | The Dashboard's client |
| `cosky.auth.oidc.audience` | The client | What a token must be for |
| `cosky.auth.oidc.groups-claim` | `groups` | The claim holding group names |
| `cosky.auth.oidc.scopes` | `openid, profile` | What the Dashboard asks for. Some providers send groups only when asked. |

### Watch and reads

| Property | Default | Meaning |
|---|---|---|
| `cosky.watch.window` | `10000` | Events kept for resuming, and in the store's log. Should be at least the peak event rate times the longest reconnection gap. |
| `cosky.watch.window-bytes` | `64MB` | The most a replica keeps of those events in memory, per namespace. It does not limit the store's log. |
| `cosky.watch.progress-interval` | `10s` | Bookmarks sent on idle watches |
| `cosky.watch.max-per-stream` | `1000` | Watches on one stream |
| `cosky.watch.anti-entropy-interval` | `5m` | How often a replica compares its view with the store |
| `cosky.read.min-revision-timeout` | `3s` | How long a read or a resume waits for a replica to catch up |
| `cosky.view.topology-flush-interval` | `30s` | How often a replica records who watches whom. Edges expire after three times this. |

### Leases

| Property | Default | Meaning |
|---|---|---|
| `cosky.lease.reaper-interval` | `1s` | How often expired leases are looked for |
| `cosky.lease.reaper-grace` | `30s` | No reaping for this long after becoming leader, or after failing to reach the store |
| `cosky.lease.reaper-max-per-tick` | `100` | Leases reaped per namespace per round |
| `cosky.lease.lock-ttl` | `15s` | How long the reaper's leadership lasts without renewal |
| `cosky.lease.protection-threshold` | `0.5` | Reaping pauses when more than this share of a namespace's leases expire at once |
| `cosky.lease.protection-min-leases` | `10` | Namespaces with fewer leases have no such pause |
| `cosky.lease.protection-max-duration` | `5m` | How long the pause lasts at most |

### From Spring

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | REST, Dashboard and Actuator |
| `spring.grpc.server.port` | `9090` | gRPC |
| `server.ssl.bundle`, `spring.grpc.server.ssl.bundle` | — | [TLS](./security.md#tls) |
| `spring.grpc.server.shutdown.grace-period` | `5s` | How long open streams get on shutdown |
| `management.tracing.sampling.probability` | `0.1` | Share of requests traced |
| `management.opentelemetry.tracing.export.otlp.endpoint` | — | Where traces go |

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

## Spring Boot starter

| Property | Default | Meaning |
|---|---|---|
| `cosky.enabled` | `true` | Everything below |
| `cosky.endpoints` | `localhost:9090` | Servers to try in turn |
| `cosky.token` | — | Sent on every call |
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

The service name is `spring.application.name`. `spring.cloud.service-registry.auto-registration.enabled: false` also stops registering.
