---
title: Performance
description: The targets of CoSky 8.0, what was measured, and what was not.
---

# Performance

Measured through real gRPC and serialization, with the server in a JVM of its own on JDK 25 with compact object headers, against a Redis 7.4 container. **Load generator and server shared one 10-core laptop, over loopback**, so there was no real network, and the generator competed with the server for CPU. Treat these as an indication, and measure on your own hardware.

## Results

| Target | Result | Verdict |
|---|---|---|
| 50,000 instances and 10,000 Configs in a namespace | Written in 7 seconds at 64 concurrent writers. A second replica loaded them and was ready in 2 to 3.5 seconds. | Met |
| About 1 KB per instance | 86 MB for 60,000 objects, about 1.5 KB each, with the index by service and the event window | Slightly over |
| Write p99 under 20 ms | 2 ms at about 12,000 writes per second with 16 writers; 8 ms at most. Without a real network. | Met, to be measured again in one data center |
| 20,000 connections and 100,000 watches on a replica | All succeeded, in 7 seconds, and the heap grew by about 370 MB. | Met |
| Propagation p99 under 500 ms | With every write going to every connection: 89 ms to 5,000 connections, 135 ms to 10,000, and 154 to 189 ms to 20,000 in 4 runs, 400 ms at most. | Met |
| `choose()` and reading a Config allocate nothing | `choose()` 24 to 58 million operations per second, reading a Config about 1,500 million, at about 0 bytes each | Met |

Propagation is measured in the worst case: every connection watches the same service, and the generator receives every copy on the same machine. In real traffic, an event goes only to those who depend on that service.

Earlier runs at 20,000 connections sometimes had a tail of 1 to 10 seconds. It came from the generator: its gRPC client ran callbacks on a thread for each busy stream, and some 900 threads competed with the server for the 10 cores, while the server had no GC pause and Redis no slow command. With the callbacks on the transport's threads, the tail went away.

The connection figures come from a Linux container on the same laptop, because on macOS a client runs out of ports at about 16,000 connections. That is what stopped earlier runs at about 15,000. The load generator and the server still share 10 cores.

## The event window

A replica was killed under load, and 100 clients reconnected to another one, all watching one service. Falling out of the window means reading a fresh snapshot of that service, which costs little.

| Writes per second | Window | Clients | Deliveries per second | `gone` |
|---|---|---|---|---|
| About 160 | 10,000 | 100 | About 16,000 | 0 |
| About 1,600 | 10,000 | 5 / 20 | About 8,000 / 32,000 | 0 |
| About 1,600 | 10,000 | 100 | About 160,000 | 97 to 100 |
| About 1,500 | 50,000 | 100 | About 150,000 | 0 |

So `gone` appears only when delivery cannot keep up, around 160,000 per second. The default stays at 10,000, and `cosky_watch_gone_total` after a rolling update shows whether yours should be larger.

## Large Configs

A server with 512 MB of heap, with one 1 MB Config written over and over:

| Limit in bytes | Writes | Heap | Redis |
|---|---|---|---|
| None | Started failing after about 500 | Reached 510 MB | 509 MB, after 484 |
| 64 MB, in memory only | All 800 succeeded | Steady at about 120 MB | 825 MB, after 800 |
| 64 MB, in memory and in the log | All 800 succeeded | Steady at about 120 MB | 92 MB, after 800 |

It restarted on that log in 4 seconds, and took 50 more writes. Trimming the log made no visible difference to write speed: in alternating runs, 16 writers reached about 11,400 and 9,600 writes per second, against 11,500 and 10,400 before.

## A Redis Cluster failover

On a cluster of three masters and three replicas in Docker, with a node timeout of 5 seconds, one server wrote a Config every 100 ms to a namespace while its master was stopped:

| The master | The namespace unavailable |
|---|---|
| Killed (`docker kill`) | 15.8 seconds |
| Frozen (`docker pause`) | 15.7 seconds |

That is the node timeout, the replica's election, and then up to 10 seconds: the server reads the topology every 5 seconds, and each read waits up to 5 seconds for the stopped node. Meanwhile each request on that namespace failed with `STORE_UNAVAILABLE` after 5 seconds. 8.0.0-alpha.5 left it unavailable for 53 seconds after a kill, and after a freeze until the master answered again, as it never read the topology again.

## Not measured

- A real network, or a real data center.
- More than one server replica under connection load.
- Redis Cluster at this scale.
- Long runs: memory over days, and leaks.

## Reproducing

```bash
COSKY_MEASURE=load COSKY_MEASURE_HEAP=4g ./gradlew :server:test --tests '*LoadMeasurement*'
```

`COSKY_LOAD_CONNECTIONS`, `COSKY_LOAD_WATCHES_PER_CONNECTION`, `COSKY_LOAD_PROPAGATION_PAUSE_MS`, `COSKY_LOAD_RATES`, `COSKY_LOAD_WINDOW` and `COSKY_LOAD_WINDOW_CLIENTS` set the shape. `COSKY_LOAD_SERVER_HEAP` sizes the server, and `COSKY_LOAD_REDIS_URI` uses a Redis you started, which a run inside a container needs. The hot paths of the client are JMH benchmarks:

```bash
./gradlew :client:jmh
```
