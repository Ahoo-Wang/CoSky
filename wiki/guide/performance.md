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
| 20,000 connections and 100,000 watches on a replica | 5,000 connections and 100,000 watches all succeeded, in 8 seconds. Of 15,000 and 105,000, 14,890 connections and 104,230 watches succeeded, in 12 seconds. | **20,000 not reached**: the laptop ran out of ephemeral ports. The cause of the 110 failed connections was not found. |
| Propagation p99 under 500 ms | 190 ms to 5,000 connections, 412 ms at most. About 750 ms to 15,000. | Met at 5,000, **missed at 15,000** |
| `choose()` and reading a Config allocate nothing | `choose()` 24 to 58 million operations per second, reading a Config about 1,500 million, at about 0 bytes each | Met |

The missed propagation target is the worst case: every connection watches the same service, and the generator receives every copy on the same machine. In real traffic, an event goes only to those who depend on that service.

## The event window

A replica was killed under load, and 100 clients reconnected to another one, all watching one service. Falling out of the window means reading a fresh snapshot of that service, which costs little.

| Writes per second | Window | Clients | Deliveries per second | `gone` |
|---|---|---|---|---|
| About 160 | 10,000 | 100 | About 16,000 | 0 |
| About 1,600 | 10,000 | 5 / 20 | About 8,000 / 32,000 | 0 |
| About 1,600 | 10,000 | 100 | About 160,000 | 97 to 100 |
| About 1,500 | 50,000 | 100 | About 150,000 | 0 |

So `gone` appears only when delivery cannot keep up, around 160,000 per second. The default stays at 10,000, and `cosky_watch_gone_total` after a rolling update shows whether yours should be larger.

## Not measured

- A real network, or a real data center.
- More than one server replica under connection load.
- Redis Cluster at this scale.
- Long runs: memory over days, and leaks.

## Reproducing

```bash
COSKY_MEASURE=load COSKY_MEASURE_HEAP=4g ./gradlew :server:test --tests '*LoadMeasurement*'
```

`COSKY_LOAD_CONNECTIONS`, `COSKY_LOAD_WATCHES_PER_CONNECTION`, `COSKY_LOAD_RATES`, `COSKY_LOAD_WINDOW` and `COSKY_LOAD_WINDOW_CLIENTS` set the shape. The hot paths of the client are JMH benchmarks:

```bash
./gradlew :client:jmh
```
