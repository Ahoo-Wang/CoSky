---
title: 需求
description: CoSky 需要并经过测试的版本。
---

# 需求

| 组件 | 版本 | 说明 |
|---|---|---|
| Server | JDK 25 | 镜像已包含 |
| 客户端和 starter | JDK 21 或更高 | |
| `cosky-migrate` | JDK 21 或更高 | |
| 从源码构建 | JDK 21 和 JDK 25 | Gradle 不会下载它们 |
| Spring Boot | 4.1 | starter 需要 |
| Spring Cloud | 2025.1 | 声明的是 Spring Boot 4.0，所以在 2026.0 之前请设置 `spring.cloud.compatibility-verifier.enabled: false` |
| Redis | 7.0 或更高，`maxmemory-policy noeviction` | server 启动时检查这两项，不满足就拒绝运行 |
| Valkey | 8 | |
| Kubernetes | 支持 Kustomize 即可 | 清单用 `kubectl kustomize` 检查过，还没有在真实集群上运行过 |
| 浏览器 | 当前版本的 Chrome、Edge、Firefox 或 Safari | 只测试过 Chrome |

每个 PR 都在 Redis 7.0、Redis 7.4 和 Valkey 8 上运行，包括单节点和 Redis Cluster。

## 依赖

| 库 | 版本 |
|---|---|
| Kotlin | 2.4，编译到 Java 21。基于它编译需要 Kotlin 2.3 或更高版本。 |
| gRPC | 1.81 |
| Protobuf | 4.34 |
| Spring gRPC | 1.1 |
| Lettuce | 7.5 |

`cosky-bom` 管理 CoSky 各模块的版本，其余由 Spring Boot 和 Spring Cloud 自己的 BOM 管理。
