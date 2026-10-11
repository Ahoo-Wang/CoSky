---
title: Internals
description: A map of the codebase for contributors, the invariants it protects, and how to debug it.
---

# Internals

This page is for contributors. The process (setup, conventions, tests, pull requests, releases) is in [CONTRIBUTING.md](https://github.com/Ahoo-Wang/CoSky/blob/main/CONTRIBUTING.md); the design is in [Architecture](./architecture.md).

## Where things live

| To change… | Look at |
|---|---|
| The consistency cache | `cosky-core`: `cache/ReactiveWatchCache.kt` |
| Lua script loading and `-- @include` | `cosky-core`: `redis/LuaScripts.kt` |
| Config storage, versions | `cosky-config`: `redis/RedisConfigService.kt`, `src/main/resources/config_*.lua`, `cosky/config/config_lib.lua` |
| Registration, renewal, expiry | `cosky-discovery`: `redis/RedisServiceRegistry.kt`, `RenewInstanceService.kt`, `registry_*.lua`, `cosky/discovery/discovery_lib.lua` |
| Cached discovery | `cosky-discovery`: `redis/ConsistencyRedisServiceDiscovery.kt` |
| Redis key names | `ConfigKeyGenerator`, `DiscoveryKeyGenerator` (and the key functions of the Lua libraries) |
| Spring wiring and properties | `cosky-spring-cloud-*`: `*AutoConfiguration.kt`, `*Properties.kt` |
| REST endpoints | `cosky-rest-api`: `*Controller.kt`; paths in `support/RequestPathPrefix.kt` |
| Security | `cosky-rest-api`: `security/` and `src/main/resources/cosky-policy.json` |
| Dashboard | `dashboard/` (React; see its README) |
| Documentation | `wiki/` (VitePress; conventions in `wiki/AGENTS.md`) |

## Invariants and the tests that guard them

| Invariant | Guarded by |
|---|---|
| Every Redis mutation is one Lua script that publishes its change | Code review; `ConsistencyRedisServiceDiscoveryTest`, `RedisConsistencyConfigServiceTest` observe the events |
| Key names in Lua and in Kotlin agree | `ConfigLuaKeysTest`, `DiscoveryLuaKeysTest` |
| A cache subscribes before it loads, and applies events in order | `ReactiveWatchCacheTest` |
| A failed load is never cached | `ReactiveWatchCacheTest`, `*ErrorTest` |
| Expired instances are reaped without the server | `InstanceExpiryReaperTest` |
| The REST API enforces authentication and RBAC | `RestApiIntegrationTest` |

[Requirements](./requirements.md) maps every user-visible behavior to its tests.

## Changing the Redis data model

The key schema and the Lua scripts are a contract between client versions running side by side, so:

1. Add the key to the key generator **and** to the Lua library, and extend the matching `*LuaKeysTest`.
2. Pass every key a script touches through `KEYS` (needed for Redis Cluster), and publish on the changed key's channel.
3. Consider older clients: a new event payload or field must not break them, or must be opt-in (as `renew-event-carries-ttl-at` is).
4. Describe the change in [Architecture](./architecture.md#data-model) and, if it affects upgrades, in the upgrade guide.

## Tests

- Tests run against a real Redis on `localhost:6379`; extend `AbstractReactiveRedisTest` (module `cosky-test`) to get a template and clean-up.
- Use `me.ahoo.test.asserts.assert` for assertions and MockK for collaborators.
- `./gradlew check` also runs detekt, the license header check and the coverage floor.

## Debugging

```bash
redis-cli --scan --pattern '{dev}:*'              # keys of a namespace
redis-cli hgetall '{dev}:svc_itc:order-service@http#10.0.0.5#8080'
redis-cli psubscribe '{dev}:*'                    # watch every event of a namespace
redis-cli zrange '{dev}:svc_itc_exp' 0 -1 withscores   # ephemeral instances by expiry time
```

Raise `logging.level.me.ahoo.cosky` to `debug` to log cache loads, events and renewals.

## Benchmarks

JMH benchmarks live in `src/jmh` of `cosky-config` and `cosky-discovery`; see [Performance](./performance.md#reproducing). Compare before and after on the same machine when changing a hot path.
