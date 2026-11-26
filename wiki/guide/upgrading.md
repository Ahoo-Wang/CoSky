---
title: Upgrading
description: Move from one CoSky 8.x release to another, what stays compatible, and in what order to upgrade servers and applications.
---

# Upgrading

This page is for those who run CoSky 8.x and move to a later 8.x release. To move from 6.x, which shares nothing with 8.0, see [Moving from 6.x](./upgrade-8.md).

## What stays compatible

From `8.0.0-rc.1` on, these only grow:

- the gRPC API, error reasons included;
- REST;
- the Kotlin API of `cosky-api`, `cosky-client` and `cosky-spring-boot-starter`;
- the properties of the server and the starter;
- the metrics in [Deployment](./deployment.md#observability), with their labels;
- the data in Redis.

A field, call, property or metric may be added, but none is removed, renumbered, renamed or given another meaning. On every pull request, CI holds the protos, the Kotlin API, REST, the properties and the metrics to this. What `cosky-api` marks `@InternalCoSkyApi`, and the Kotlin API of the other modules, may change in any release. A new major version, such as 9.0, may change anything, and comes with an upgrade guide. [`CONTRIBUTING.md`](https://github.com/Ahoo-Wang/CoSky/blob/main/CONTRIBUTING.md#releases-and-versioning) has the whole rule.

The alphas and betas made no such promise. Each one's notes say how to upgrade from the one before.

The promise runs one way: a later server reads what an earlier one wrote, and serves earlier applications. Going back to an earlier release is not part of it, so read the notes of the release you leave first.

## In what order

1. **Read the release notes** of each release from yours to the new one, on the [releases page](https://github.com/Ahoo-Wang/CoSky/releases). Their section "Upgrading from …" says what to do, and "Known limits" what to expect.
2. **Upgrade the servers**, one replica at a time. The Dashboard comes with them.
3. **Then the applications**, at their own pace: raise `cosky-bom`, build and deploy them as usual.

During a rolling update, replicas of both releases share Redis and serve clients side by side. In Kubernetes, set the new tag, as `newTag` in your overlay's `kustomization.yaml`, and apply it: the Deployment replaces one pod at a time, and clients reconnect to the others and resume where they were, as [Rolling updates](./deployment.md#rolling-updates) explains. With Docker, replace one container at a time, and wait for the new one to be ready before the next.

While it runs, `cosky_watch_gone_total` shows clients that had to read a fresh snapshot instead of resuming, and [Troubleshooting](./troubleshooting.md#clients-start-over-cosky-watch-gone-total-rises) says what to do if it rises.

## From an 8.0 beta to rc.1

rc.1 settled what the promise keeps, so it changed more than a beta did. The data in Redis is unchanged: upgrade the servers in place, then the applications, in one window. A beta application and an rc.1 server, or the reverse, end a watch whose position is gone in ways the other does not know, and such a watch only starts again when the application restarts.

What to check, in short:

- **The bootstrap token**: the server refuses to start with one that is malformed, or whose secret has fewer than 22 characters.
- **Settings**: the server refuses to start with one that cannot work, and a duration without a unit is now in seconds, not milliseconds.
- **Access**: an account that writes Roles, RoleBindings or tokens must hold what it grants.
- **The starter's health indicator** is now `cosky`, switched off by `management.health.cosky.enabled`.
- **Alerts and dashboards**: new values of `cosky_auth_failures_total{reason}`, `max by (namespace)` for `cosky_lease_active`, and a `namespace` label on `cosky_lease_expired_total`.
- **The Kubernetes manifests** put Actuator on port 8081, so scrape the pods there, and request 2Gi of memory.
- **The image** runs as `999:999`, and `JAVA_OPTS` adds to its JVM flags instead of replacing them.
- **REST and gRPC clients of your own**: stricter requests, paged views and a new way a watch ends.
- **OIDC**: register the Dashboard's address with a trailing slash.

The [release notes of rc.1](https://github.com/Ahoo-Wang/CoSky/releases/tag/v8.0.0-rc.1) give the details of each. From an earlier beta, read the notes of each release in turn: the data in Redis carries over from beta.1 on.
