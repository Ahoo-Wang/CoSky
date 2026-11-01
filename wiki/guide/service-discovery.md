---
title: Service Discovery
description: Register instances under a lease, find them through Spring Cloud, and understand what happens when things fail.
---

# Service Discovery

An **Instance** is one running copy of a service: a service name, an endpoint, a weight and metadata. A service is simply the group of Instances that share a name.

## Registering

With the starter, an application registers once its web server is ready, and removes itself on shutdown.

```yaml
spring:
  application:
    name: order-service
cosky:
  registry:
    enabled: true
    weight: 1
    metadata:
      zone: az1
```

- The service is `spring.application.name`, which must be a DNS-style name: lowercase letters, digits, `-` and `.`, up to 63 characters. `OrderService` is refused.
- A separate management port is not registered.
- `spring.cloud.service-registry.auto-registration.enabled: false` turns registering off.

## Leases

A process holds one lease per namespace, however many instances it registers, and renews it every third of its TTL (30 seconds by default). When renewals stop, its instances go.

| Event | What happens |
|---|---|
| Clean shutdown | The lease is revoked within 3 seconds, and the instances go at once. |
| Crash | The lease expires, and the instances go a third of the TTL later: 40 seconds by default. |
| Lease lost, such as after a Redis failover | The client notices, waits up to 5 seconds so that not everyone returns at once, and registers everything again. |
| Client reconnects | It renews at once, without waiting for the next period. |

Allowed TTLs are 5 to 300 seconds.

## Finding services

`DiscoveryClient` and `ReactiveDiscoveryClient` read the local view. The first read of a service waits up to `cosky.discovery.load-timeout` (5 seconds), so that "not known yet" is not mistaken for "empty". Afterwards, a read costs no network round trip.

Add `spring-cloud-starter-loadbalancer`, and `@LoadBalanced` clients get their instances from CoSky, with Spring Cloud's own algorithms choosing among them.

## Weights

A weight of `0` keeps an instance registered but takes it out of rotation, which is how to drain one: load balancers leave it out, while `DiscoveryClient` still lists it. The Kotlin client's load balancer picks at random in proportion to weight. Spring Cloud LoadBalancer's default round robin treats every other weight alike.

An operator can set it on an instance an application registered. It lasts until the application registers again, after restarting or losing its lease.

## Fixed instances

An Instance without a lease stays until someone deletes it. Use this for something that cannot register itself, such as a database:

```bash
curl -X PUT localhost:8080/v1/namespaces/prod/instances/orders-db \
  -H 'Content-Type: application/json' \
  -d '{"instance":{"service":"orders-db","endpoint":{"scheme":"tcp","host":"10.0.0.5","port":3306}}}'
```

The Dashboard marks each instance **Leased** or **Fixed**. Nothing checks whether a fixed instance is alive.

## When things fail

CoSky favours keeping a stale instance over dropping a live one, and callers are expected to retry and use circuit breakers.

| Failure | What applications see |
|---|---|
| Server unreachable | Reads keep serving the last view, and the health indicator stays `UP` with `connection: DEGRADED`, because a restart would not help. |
| Restart while the server is away | The view comes back from the [snapshot](./sdk.md#snapshots) on disk. |
| Server or network was down for longer than the TTL | The reaper waits 30 seconds after recovering, so that live clients can renew rather than be removed. |
| More than half of a namespace's leases expire at once | Reaping pauses, as this looks like a partition. It resumes when the share falls, or after 5 minutes regardless. |
| Redis fails over and loses recent writes | Every replica notices the revision going backwards, and clients start over from a fresh snapshot. Writes that were lost stay lost. |

These all have settings under [`cosky.lease`](./properties.md#server). A process that really crashed during such a pause stays listed for up to 5 minutes longer.

## Who calls whom

A client that watches a service counts as consuming it. The topology on the [Dashboard](./dashboard.md) and at `/views/topology` shows these edges, under the name in `spring.application.name`, or the signed-in subject without one. They expire 90 seconds after the last replica saw the watch, so a stopped consumer disappears by itself.
