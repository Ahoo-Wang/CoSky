---
title: Performance
description: The targets of CoSky 8.0, what was measured, and what was not.
---

# Performance

Measured through real gRPC and serialization, with the server in a JVM of its own on JDK 25 with compact object headers, against a Redis 7.4 container. **Load generator and server shared one 10-core laptop, over loopback**, so there was no real network unless [one was emulated](#over-an-emulated-network), and the generator competed with the server for CPU. Treat these as an indication, and measure on your own hardware.

## Results

| Target | Result | Verdict |
|---|---|---|
| 50,000 instances and 10,000 Configs in a namespace | Written in 7 seconds at 64 concurrent writers. A second replica loaded them and was ready in 2 to 3.5 seconds. | Met |
| About 1 KB per instance | 86 MB for 60,000 objects, about 1.5 KB each, with the index by service and the event window | Slightly over |
| Write p99 under 20 ms | 2 ms at about 12,000 writes per second with 16 writers; 8 ms at most. Over an emulated network with Redis and clients each 6 ms away, 15 ms with one writer. | Met, but not where 1% of packets are lost |
| 20,000 connections and 100,000 watches on a replica | All succeeded, in 7 seconds, and the heap grew by about 370 MB. | Met |
| Propagation p99 under 500 ms | With every write going to every connection: 89 ms to 5,000 connections, 135 ms to 10,000, and 154 to 189 ms to 20,000 in 4 runs, 400 ms at most. Over an emulated network, 348 ms to 5,000 clients 58 ms away that lose 0.5% of packets. | Met |
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

## Many namespaces, and a Redis Cluster

Three replicas followed many namespaces of 50 Instances each, on Redis 7.4 alone and on a Redis Cluster of three masters and three replicas, all in one container on the same laptop. 16 writers wrote 32,000 Configs through one replica, each to a namespace picked at random, and a client on another replica watched 100 of the namespaces.

| | 1,000 namespaces, one Redis | 1,000 namespaces, Cluster | 5,000 namespaces, one Redis | 5,000 namespaces, Cluster |
|---|---|---|---|---|
| The second and third replicas ready | 3.3 and 4.0 s | 3.4 and 4.1 s | 4.7 and 6.7 s | 5.2 and 7.5 s |
| Redis at rest | 21,000 commands/s, 11% CPU | 6,700 commands/s and 4% CPU on each master | 129,000 commands/s, 30% CPU | 42,000 commands/s and 11% CPU on each master |
| Writes | 8,354/s, p99 5 ms | 7,809/s, p99 7 ms | 6,739/s, p99 9 ms | 6,240/s, p99 9 ms |
| Redis while writing | 77% CPU | 39 to 43% CPU on each master | 84% CPU | 48 to 55% CPU on each master |
| A write reaching the watcher on another replica, p99 | 14 ms | 23 ms | 8 ms | 16 ms |

At rest, each replica reads each namespace once a second, should a hint have been lost: about seven Redis commands a read. That load grows with the namespaces times the replicas, and not with clients. On one Redis it reached 30% of its CPU at 5,000 namespaces and three replicas; a Cluster spreads it over its masters, and a longer `cosky.store.redis.tail-poll-interval` lowers it, at the cost of a slower recovery from a lost hint.

A write cost about 35 Redis commands with three replicas: the write itself, and one read of the namespace by each replica. In a Cluster, the hint that a write publishes reaches every node, which showed as 4,000 to 5,000 messages a second received by each master, at no visible cost. Here the laptop, not Redis, limited the writes: one Redis was close to saturated, while the masters of the Cluster had half their CPU left.

The targets above, measured again on a Cluster, matched one Redis: one namespace of 50,000 Instances and 10,000 Configs, 17,500 writes per second with 16 writers at p99 2 ms, 5,000 connections watching, propagation p99 103 ms, and a second replica ready in 2.9 seconds.

## Over an emulated network

The targets above, measured again with a network between the server and Redis, and between the clients and the server: 5,000 connections watching, one namespace of 50,000 Instances and 10,000 Configs. Each link is a [Toxiproxy](https://github.com/Shopify/toxiproxy) relay in a container, whose interface `netem` shapes, so that each direction is delayed once. The round trips are measured one request at a time, as `netem` here delays a packet alone on the link by milliseconds more than it is told ([how it was run](#how-it-was-run)).

| | Loopback | Redis and clients 6 ms away | The same, 0.1% of packets lost | 7 ms with jitter, 1% lost | Clients 58 ms away, 0.5% lost |
|---|---|---|---|---|---|
| `netem`, each direction | none | 0.1 ms | 0.1 ms, 0.1% loss | 1 ± 0.5 ms, 1% loss | Redis 0.1 ms; clients 25 ± 5 ms, 0.5% loss |
| Round trip to Redis, p50 | 0.3 ms | 5.9 ms | 5.9 ms | 7.1 ms | 5.8 ms |
| Round trip to the server, p50 | 0.4 ms | 5.7 ms | 5.9 ms | 6.1 ms | 57.8 ms |
| Writes, one writer, p50 and p99 | under 1 ms | 9 and 15 ms | 9 and 15 ms | 15 and 232 ms | 62 and 294 ms |
| Writes, 16 writers | 17,422/s, p99 2 ms | 4,973/s, p99 9 ms | 4,454/s, p99 11 ms | 1,177/s, p99 24 ms | 253/s, p99 96 ms |
| Propagation to 5,000 connections, p50 and p99 | 57 and 120 ms | 76 and 127 ms | 79 and 254 ms | 91 and 361 ms | 110 and 348 ms |
| A second replica ready | 3.4 s | 3.6 s | 3.4 s | 6.4 s | 3.5 s |

Through the relays with no delay, the results matched loopback: propagation p99 115 ms against 120 ms.

One data center is 0.1 to 0.5 ms across, between loopback and these 6 ms. One writer, whose writes cross the links alone, met the 20 ms target: p99 15 ms. Propagation p99 was 127 ms, against 120 ms on loopback. 16 writers write less often the farther away they are, as each waits for its answer: that is the load generator, not the server.

Loss costs more than distance. TCP often resends a lost packet only after a timeout, at least 200 ms on Linux. A packet lost on a write's way, from the writer through the server to Redis and back, holds up that write's event for every connection. Of 100 writes measured, one such write sets the p99, and 0.1% loss doubled it, to 254 ms. With 1% lost on every link, one writer's p99 was 232 ms: a write crosses a link four times, in packets of its own, so more than 1% of writes lose one and wait for its resending. Propagation p99 stayed under 500 ms on every network here; the slowest event took 779 ms, with 1% lost.

Over the link to those clients, loading the namespace took 60 seconds instead of 6, and opening the watches 224 seconds instead of 14: the load generator writes 64 at a time and waits 58 ms for each, and all the snapshots of 5,000 clients crossed one relay and one `netem` queue, where real clients would each have a path of their own.

### Leases

100 clients, each with an Instance on its lease (a TTL of 30 seconds, renewed every 10), over a link, for 300 seconds, while a client on loopback watched their Instances:

| The clients' link | Registered in | Fewest Instances seen | Leases expired |
|---|---|---|---|
| 25 ± 5 ms each way, 0.5% lost: a round trip of 58 ms | 4.0 s | 100 of 100 | 0 |
| 100 ± 20 ms each way, 5% lost: a round trip of about 200 ms | 17.5 s | 100 of 100 | 3 |

On the second link, a call could wait for several resendings, which is why registering took 17.5 seconds. The three leases that expired held no Instance: the watcher saw all 100 throughout, looking every 100 ms, while an Instance whose lease runs out stays gone until its client finds out at its next renewal, up to 10 seconds later, and registers it again. They fit leases granted to calls whose answer never reached the client, which then asked again: it kept the lease it was given, and the server let the other run out.

### How it was run

The load generator and the server ran in a Linux container on the laptop's Docker VM (OrbStack), on the network of the VM itself, beside Redis and the relays in containers of their own, so that only the VM's kernel stood between them. Through the VM's port forwarding to macOS, packets were lost even without `netem`, and about 5% of the events to 100 connections came 200 ms late, a resending. In this VM, `netem` delays a packet alone on the link by up to a few milliseconds more than told, as the timer that releases it fires late, while under steady traffic packets leave closer to on time: that is why 0.1 ms became a round trip of 6 ms for one request, and why 16 writers saw shorter writes than one. Its jitter also reorders packets, which a real path seldom does. And the same 10 cores ran the server, Redis, the relays and the load generator.

## Not measured

- A real network, or a real data center: the network above was emulated, on one laptop.
- More than one server replica under connection load.
- A Cluster of more than three masters, or more than three replicas.
- Long runs: memory over days, and leaks.

## Reproducing

```bash
COSKY_MEASURE=load COSKY_MEASURE_HEAP=4g ./gradlew :server:test --tests '*LoadMeasurement*'
```

`COSKY_LOAD_CONNECTIONS`, `COSKY_LOAD_WATCHES_PER_CONNECTION`, `COSKY_LOAD_PROPAGATION_PAUSE_MS`, `COSKY_LOAD_RATES`, `COSKY_LOAD_WINDOW` and `COSKY_LOAD_WINDOW_CLIENTS` set the shape. `COSKY_LOAD_SERVER_HEAP` sizes the server, and `COSKY_LOAD_REDIS_URI` uses a Redis you started instead of a container of its own. With `COSKY_LOAD_REDIS_CLUSTER=true` it is a node of a Redis Cluster, whose nodes must announce addresses the run can reach. Many namespaces on several replicas is another test, shaped by `COSKY_LOAD_NAMESPACES`, `COSKY_LOAD_OBJECTS_PER_NAMESPACE`, `COSKY_LOAD_REPLICAS` and `COSKY_LOAD_WRITES`:

```bash
COSKY_MEASURE=load COSKY_MEASURE_HEAP=2g COSKY_LOAD_SERVER_HEAP=2g ./gradlew :server:test --tests '*LoadMeasurement.many namespaces*'
```

`COSKY_LOAD_REDIS_NETEM` and `COSKY_LOAD_CLIENT_NETEM` put a link between the server and Redis, and between the clients and the server, taking the arguments of `tc qdisc ... netem`. Leases over such a link are another test, shaped by `COSKY_LOAD_LEASE_CLIENTS` and `COSKY_LOAD_LEASE_SECONDS`. On a laptop, run them in a Linux container on the Docker host's network, with JDK 21 to build and a Linux JDK 25 for the server, which Gradle finds in `org.gradle.java.installations.paths`:

```bash
docker run --rm --network host -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/work -v /path/to/jdk-25:/opt/jdk25 -w /work \
  -e TESTCONTAINERS_HOST_OVERRIDE=localhost -e COSKY_MEASURE=load -e COSKY_MEASURE_HEAP=3g -e COSKY_LOAD_SERVER_HEAP=2g \
  -e COSKY_LOAD_CLIENT_NETEM='delay 25ms 5ms loss 0.5%' eclipse-temurin:21-jdk \
  ./gradlew :server:test --tests '*LoadMeasurement.the targets*' -Porg.gradle.java.installations.paths=/opt/jdk25
```

The hot paths of the client are JMH benchmarks:

```bash
./gradlew :client:jmh
```
