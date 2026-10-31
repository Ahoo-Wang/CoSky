---
title: Requirements
description: The versions CoSky needs and is tested with.
---

# Requirements

| Component | Version | Notes |
|---|---|---|
| Server | JDK 25 | The image includes it |
| Client and starter | JDK 21 or later | |
| `cosky-migrate` | JDK 21 or later | |
| Spring Boot | 4.1 | For the starter |
| Spring Cloud | 2025.1 | Declares Spring Boot 4.0, so set `spring.cloud.compatibility-verifier.enabled: false` until 2026.0 |
| Redis | 7.0 or later, with `maxmemory-policy noeviction` | The server checks both at start, and refuses to run otherwise |
| Valkey | 8 | |
| Kubernetes | Anything with Kustomize | The manifests were checked with `kubectl kustomize`, and have not run on a real cluster yet |
| Browser | A current Chrome, Edge, Firefox or Safari | Only Chrome is tested |

Every pull request runs against Redis 7.0, Redis 7.4 and Valkey 8, on a single node and a Redis Cluster.

## Dependencies

| Library | Version |
|---|---|
| Kotlin | 2.x, compiled to Java 21 |
| gRPC | 1.81 |
| Protobuf | 4.34 |
| Spring gRPC | 1.1 |
| Lettuce | 7.5 |

`cosky-bom` manages the versions of the CoSky modules. Spring Boot's and Spring Cloud's own BOMs manage the rest.
