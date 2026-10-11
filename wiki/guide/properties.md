---
title: Configuration Properties
description: Every property of the CoSky starters and of the REST API server, with defaults.
---

# Configuration Properties

Durations accept Spring Boot's formats (`10s`, `1m`, `PT10S`). Properties that control which config to load must be set in `bootstrap.yaml`.

## Common

| Property | Default | Description |
|---|---|---|
| `spring.cloud.cosky.enabled` | `true` | Enables CoSky (configuration and discovery). |
| `spring.cloud.cosky.namespace` | `cosky-{default}` | Namespace of the application's config and registration. A value without `{…}` is wrapped in a hash tag: `dev` becomes `{dev}`. |
| `spring.data.redis.*` | Spring Boot defaults | Redis connection (`url`, `host`, `port`, `password`, `cluster.nodes`, …), shared with the application. |

## Configuration

| Property | Default | Description |
|---|---|---|
| `spring.cloud.cosky.config.enabled` | `true` | Enables the config starter. |
| `spring.cloud.cosky.config.config-id` | `${spring.application.name}.${file-extension}` | Config to load. |
| `spring.cloud.cosky.config.file-extension` | `yaml` | Format of a config id without extension: `yaml`, `yml`, `properties` or `xml`. |
| `spring.cloud.cosky.config.timeout` | `2s` | Maximum wait for the config at startup. |

## Service discovery

| Property | Default | Description |
|---|---|---|
| `spring.cloud.cosky.discovery.enabled` | `true` | Enables discovery and registration. |
| `spring.cloud.cosky.discovery.order` | `0` | Order of CoSky's `DiscoveryClient` among several. |
| `spring.cloud.cosky.discovery.timeout` | `2s` | Maximum wait of a blocking `DiscoveryClient` call. |
| `spring.cloud.cosky.discovery.topology-enabled` | `true` | Records the application as a consumer of the services it discovers. |
| `spring.cloud.cosky.discovery.filter-only-up-instances` | `true` | Returns only instances whose `instance_status` is `UP` or absent. |
| `spring.cloud.service-registry.auto-registration.enabled` | `true` | Registers the application at startup (Spring Cloud). |

### Registration

Prefix: `spring.cloud.cosky.discovery.registry`.

| Property | Default | Description |
|---|---|---|
| `service-id` | `spring.application.name` | Service the instance belongs to. |
| `host` | first non-loopback IP | Address other services call. |
| `port` | web server port | Port other services call. |
| `schema` | `http` | URI scheme. |
| `weight` | `1` | Relative weight, used by CoSky's load balancers and Spring Cloud LoadBalancer's weighted configuration. |
| `is-ephemeral` | `true` | `true`: expires unless renewed. `false`: fixed, never expires. |
| `ttl` | `60s` | Lifetime of an ephemeral instance without renewal. |
| `renew.initial-delay` | `1s` | Delay before the first renewal. |
| `renew.period` | `10s` | Renewal interval; keep it well below `ttl`. |
| `metadata.<key>` | — | Metadata registered with the instance. |
| `initial-status` | `UP` | Value of the `instance_status` metadata at registration. |
| `timeout` | `2s` | Maximum wait for registration, deregistration and status changes. |
| `renew-event-carries-ttl-at` | `false` | Publishes renewals with the new expiry time, saving consumers a Redis read. Enable only once every client runs 6.0 or later. |

## REST API server

| Property | Default | Description |
|---|---|---|
| `cosky.security.key` | — (required) | JWT signing secret; environment variable `COSKY_SECURITY_KEY`. |
| `cosky.security.enabled` | `true` | Enables authentication and authorization. |
| `cosky.security.enforce-init-super-user` | `false` | Resets the super user's password at every start; environment variable `COSKY_SUPER_INIT`. |
| `cosky.security.audit-log.action` | `write` | Requests to record: `read`, `write` or `rw`. |
| `cosky.security.audit-log.max-size` | `100000` | Newest audit entries kept. |
| `cosec.jwt.token-validity.access` | `15m` | Access token lifetime. |
| `cosec.jwt.token-validity.refresh` | `3H` | Refresh token lifetime. |
| `cosky.namespace` | `cosky-{system}` | Namespace of the server's own config and registration; environment variable `COSKY_NAMESPACE`. |
| `cosky.auto-registry` | `true` | Registers the server as service `cosky-rest-api`; environment variable `COSKY_AUTO_REGISTRY`. |
| `server.port` | `8080` | HTTP port. |

The server is a Spring Boot application: standard Spring Boot properties (logging, actuator, Redis) apply as well.
