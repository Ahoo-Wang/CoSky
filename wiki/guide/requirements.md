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
| Building from source | JDK 21 and JDK 25 | Gradle does not download them |
| Spring Boot | 4.2.0 | For the starter, which is built and tested on it. A release that manages an older gRPC than the client is built on mixes gRPC versions, which fails at runtime |
| Spring Cloud | 2026.0 | Declares Spring Boot 4.2, so its compatibility verifier can stay on |
| Redis | 7.0 or later, with `maxmemory-policy noeviction` | The server checks both at start, and refuses to run otherwise |
| Valkey | 8 | |
| Kubernetes | 1.27 or later, with Kustomize | The manifests use `matchLabelKeys` and `unhealthyPodEvictionPolicy`, on by default from 1.27, and meet Pod Security's "restricted" level. CI deploys them to k3s 1.31 for every pull request. |
| Browser | A current Chrome, Edge, Firefox or Safari | Only Chrome is tested |

Every pull request runs against Redis 7.0, Redis 7.4 and Valkey 8, on a single node and a Redis Cluster.

## Dependencies

| Library | Version |
|---|---|
| Kotlin | 2.4, compiled to Java 21. Compiling against it takes Kotlin 2.3 or later. |
| gRPC | 1.84 |
| Protobuf | 4.36 |
| Spring gRPC | 1.1 |
| Lettuce | 7.5 |

`cosky-bom` manages the versions of the CoSky modules. Spring Boot's and Spring Cloud's own BOMs manage the rest.
