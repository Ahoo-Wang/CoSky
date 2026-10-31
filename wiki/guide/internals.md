---
title: Internals
description: How the repository is organized and tested, for those working on CoSky itself.
---

# Internals

For working on CoSky itself. [Architecture](./architecture.md) explains the system, the [design document](https://github.com/Ahoo-Wang/CoSky/blob/main/docs/architecture-redesign.md) the reasoning, and `CONTRIBUTING.md` how to send a change.

## Modules

| Module | Holds | Depends on |
|---|---|---|
| `api` | Protos, generated code, validation, errors, the OpenAPI document | Nothing |
| `store` | The Store interface, and an implementation in memory | `api` |
| `store-redis` | The Redis Store: the only module with Lua | `store` |
| `server` | gRPC, REST, the Watch cache, the reaper, auth, views | `api`, `store`, `store-redis` |
| `client` | Connections, Watch, leases, snapshots, load balancing. No Spring and no Redis. | `api` |
| `spring-boot-starter` | ConfigData loading, refresh, discovery, registry | `client` |
| `conformance-test` | The suites the Store and the protocol must pass | `store`, `client` |
| `migration` | `cosky-migrate`. The only module that knows 6.x. | `api` |
| `examples` | A provider and a consumer, and the test that runs them | The starter |
| `dashboard` | The React frontend, with a build of its own | The REST API |
| `wiki` | This site | |

Two rules hold this together. Nothing but the server touches the Store, and clients never see Redis. And both protocols are one contract: REST is mapped from the same protos as gRPC.

## Building

```bash
./gradlew build
```

The server's tests need JDK 25 and Docker, and Gradle provides the JDKs. `./gradlew :server:run --args='--dev'` starts a server in memory.

The server bundles the Dashboard when `dashboard/dist` exists:

```bash
pnpm --dir dashboard install && pnpm --dir dashboard build
```

## Testing

| Level | Where | Against |
|---|---|---|
| Store conformance | `conformance-test`, run by `store` and `store-redis` | Memory, Redis, Redis Cluster |
| Protocol conformance | `conformance-test`, run by `server` | The real services over gRPC |
| Parity of the protocols | `ProtocolParityTest` | The same request over gRPC and REST ends the same way |
| Faults | `FaultInjectionTest` | A real server behind Toxiproxy: partitions, slow links, a hung store |
| Failover | `RedisStoreFailoverTest` | A real Redis losing writes |
| End to end | `examples/e2e` | The server, a provider and a consumer as separate processes |
| TLS | `TlsEndToEndTest` | Both ports, with a generated certificate |
| Dashboard | `dashboard/tests/ui` | A mock answering as the server does |
| Dashboard against the image | `dashboard/tests/ui/real-backend.spec.ts` | The page the image hosts, on that image |
| Migration | `migration` | A real Redis and a real server |

Line coverage has a floor of 90%, enforced by `./gradlew build`. New behaviour should fail a test when you take it out, so try that.

Every pull request also runs Redis 7.0 and Valkey 8, CodeQL, and an image build that starts the image and drives it.

## Lua

All of it is in `store-redis`. Each script keeps to one namespace's hash tag, and declares its keys. The layout is in the design's section on keys.

## Conventions

- Coroutines, carried by virtual threads. Reactor stays in the adapters, which Detekt enforces.
- Errors carry a stable reason, mapped to a gRPC code and an HTTP status in one place.
- A change to behaviour updates the design, and a decision gets a D-number in its table.
- Tests assert behaviour through public interfaces, against real dependencies where it matters.
