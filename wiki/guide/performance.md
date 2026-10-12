---
title: Performance
description: How fast CoSky reads and writes, how it was measured, and how to reproduce the benchmarks.
---

# Performance

Applications read configs and instances from a **local cache**, so a read costs a map lookup instead of a Redis round trip. Writes and cache misses go to Redis and are bounded by its latency.

## Results

JMH throughput, 8 threads, against Redis on the same machine (Apple M4 Pro, JDK 17.0.20, Redis 8.10, JMH 1.37), measured on CoSky 6.1.0. Raw results: [`docs/jmh`](https://github.com/Ahoo-Wang/CoSky/tree/main/docs/jmh).

| Operation | Through | Throughput |
|---|---|---|
| Read a config | local cache (`RedisConsistencyConfigService`) | **750,901,545 ops/s** |
| Read a config | Redis (`RedisConfigService`) | 48,160 ops/s |
| Save a config | Redis | 35,479 ops/s |
| List services | local cache (`ConsistencyRedisServiceDiscovery`) | **1,791,707,975 ops/s** |
| List instances of a service | local cache | **232,887,361 ops/s** |
| List services | Redis (`RedisServiceDiscovery`) | 49,117 ops/s |
| List instances of a service | Redis | 38,013 ops/s |
| Register / renew / deregister an instance | Redis | 36,691 / 40,740 / 44,275 ops/s |

How to read these numbers:

- **Cached reads** scale with CPU cores; they are what applications pay on every config property access and every load-balanced call.
- **Redis operations** depend mostly on network latency and Redis itself. Over a real network expect fewer; on shared CI runners, roughly half.
- Each figure is a single 10-second measurement after a 10-second warm-up: use them for orders of magnitude and for comparing versions on the same machine, not as capacity guarantees.

## Why cached reads are cheap

A cached read returns an immutable snapshot prepared when the value last changed: no I/O, no lock, and for configs no allocation. Keeping the snapshot current costs one Redis Pub/Sub message per change, plus a reload of the changed key. [Architecture](./architecture.md#consistency-model) describes the mechanism.

## Reproducing

With Redis running on `localhost:6379`:

```bash
./gradlew :cosky-config:jmh :cosky-discovery:jmh -PjmhThreads=8
# one benchmark class
./gradlew :cosky-discovery:jmh -PjmhIncludes=ConsistencyRedisServiceDiscoveryBenchmark
```

Results are written to `<module>/build/results/jmh/results.json`. Options: `-PjmhThreads`, `-PjmhIncludes`, `-PjmhExcludes`, `-PjmhMode` (default `thrpt`).

The [benchmark workflow](https://github.com/Ahoo-Wang/CoSky/blob/main/.github/workflows/benchmark-test.yml) runs both suites on every push, which makes regressions visible between commits.
