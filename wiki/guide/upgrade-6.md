---
title: Upgrading to 6.0
description: Breaking changes in CoSky 6.0 and the steps to upgrade servers and clients from 5.x.
---

# Upgrading to 6.0

CoSky 6.0 rebuilds the consistency layer, hardens security and bounds the data kept in Redis. The Redis key schema is unchanged, so 5.x and 6.0 clients can share a namespace during a rolling upgrade.

## Recommended order

1. **Upgrade the REST API servers** — set a JWT secret first (see below), then roll out 6.0.
2. **Grant namespace roles** to users who are not admins, and update a stored policy if you have one.
3. **Upgrade the applications** using the starters or the SDK, fixing the API changes below.
4. **Once every client runs 6.0**, optionally enable `renew-event-carries-ttl-at`.

## REST API server

### A JWT secret is required

With security enabled (the default), the server refuses to start unless `cosky.security.key` is set. Tokens used to be signed with a secret published in the repository.

```bash
export COSKY_SECURITY_KEY="$(openssl rand -base64 32)"
```

Use the same secret on every server instance. Changing it invalidates all issued tokens. The Docker, Kubernetes and standalone examples in this wiki already pass `COSKY_SECURITY_KEY`.

### Users need roles to read namespaces

The default policy used to let every authenticated user read every namespace. It now only grants `GET /v1/namespaces` (which lists the namespaces the user's roles cover) and `GET /v1/namespaces/current`; everything else needs a role. Give each non-admin user a role with read (`r`) or read-write (`rw`) access to the namespaces they work with — see [Security](./security.md).

If you stored your own policy (config `cosky-policy.json` in the `cosky-{system}` namespace), it overrides the bundled one: replace the `namespace` statement's pattern `/v1/namespaces/**` with `["/v1/namespaces", "/v1/namespaces/current"]`.

### Other server changes

| Change | What to do |
|---|---|
| Passwords are hashed with BCrypt | Nothing: existing hashes keep working and are upgraded on each user's next login. New passwords are limited to 72 bytes. |
| `PUT /v1/namespaces/current/{namespace}` removed | It switched the namespace of the whole server for every user. Pass the namespace in each request path instead. |
| Config history keeps the latest 10 versions | Older versions are trimmed on the next change of each config. |
| The audit log keeps the newest 100,000 entries | Tune with `cosky.security.audit-log.max-size`. |
| Config imports are limited | Zip files up to 10 MiB, 64 MiB once unzipped. |

## Applications (starters and SDK)

### Configuration

- `spring.cloud.cosky.enabled=false` now disables discovery and registration too.
- New: `spring.cloud.cosky.discovery.topology-enabled` (default `true`) controls whether the application records itself as a consumer of the services it discovers.
- `spring.cloud.cosky.discovery.registry.is-ephemeral` still binds.

### API changes

These only affect code that uses the CoSky classes directly, not applications that use Spring Cloud's `DiscoveryClient` or `@Value`/`@ConfigurationProperties`.

| 5.x | 6.0 |
|---|---|
| `ServiceDiscovery.getServices(namespace)` / `getInstances(namespace, serviceId)` return `Flux` | They return `Mono<List<…>>` snapshots. An unchanged snapshot is the same list instance. |
| Overloads without a namespace, `NamespacedContext`, `ServiceInstanceContext` | Removed: pass the namespace explicitly. A namespace must not be blank or contain `:`. |
| `EventListenerContainer` is `AutoCloseable` with `receive(topic)` | `listen(topic)` emits the event stream once the subscription is active; `receive(topic)` remains. |
| Load balancers take a `ServiceDiscovery` and an `InstanceEventListenerContainer` | They take only the `ServiceDiscovery`, e.g. `RandomLoadBalancer(serviceDiscovery)`. |
| Reset hooks on the consistency services | Removed: watch changes with `ConfigWatcher.watchConfig(namespace, configId)`. |
| `RedisServiceTopology(redisTemplate)` | `RedisServiceTopology(redisTemplate, consumer)`, where `consumer` is the local `NamespacedServiceId`. |
| `ServiceRegistry.setMetadata(…, key, value)` replaced all metadata | It now sets that key only; `setMetadata(…, map)` still replaces all metadata. |
| `ServiceInstance` equality compares the address (instance id, schema, host, port) | Equality is structural: weight, ephemeral, ttlAt and metadata count too. |

## Mixed versions

- Instances registered by 5.x clients keep being cleaned up when they expire. 6.0 clients also index ephemeral instances by expiry, so `expired` events no longer depend on the REST API server.
- `spring.cloud.cosky.discovery.registry.renew-event-carries-ttl-at` (default `false`) publishes renew events with the new expiry time, which saves every subscriber a Redis read per renewal. 5.x clients reject that payload: enable it only after all clients run 6.0.
