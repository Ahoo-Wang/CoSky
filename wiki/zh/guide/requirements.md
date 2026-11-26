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
| Spring Boot | 4.2.0 | starter 需要，它在这个版本上构建和测试。若某个版本管理的 gRPC 比客户端编译时用的旧，混用不同版本的 gRPC 会在运行时出错 |
| Spring Cloud | 2026.0 | 声明的是 Spring Boot 4.2，兼容性检查无需关闭 |
| Redis | 7.0 或更高，包括 Redis 8，`maxmemory-policy noeviction` | server 启动时检查这两项，不满足就拒绝运行 |
| Valkey | 8 | |
| Kubernetes | 支持 Kustomize 即可 | CI 在每个 PR 上把清单部署到 k3s 集群；清单满足 Pod Security 的 "restricted" 级别 |
| 浏览器 | 当前版本的 Chrome、Edge、Firefox 或 Safari | 只测试过 Chrome |

每个 PR 都在 Redis 7.0、Redis 7.4、Redis 8.12 和 Valkey 8 上运行，包括单节点和 Redis Cluster。

## 依赖

| 库 | 版本 |
|---|---|
| Kotlin | 2.4，编译到 Java 21。基于它编译需要 Kotlin 2.3 或更高版本。 |
| gRPC | 1.84 |
| Protobuf | 4.36 |
| Spring gRPC | 1.1 |
| Lettuce | 7.5 |

`cosky-bom` 管理 CoSky 各模块的版本，其余由 Spring Boot 和 Spring Cloud 自己的 BOM 管理。
