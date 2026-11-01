---
title: 内部实现
description: 仓库如何组织和测试，供开发 CoSky 本身的人参考。
---

# 内部实现

面向开发 CoSky 本身的人。系统说明见[架构](./architecture.md)，决策理由见[设计文档](https://github.com/Ahoo-Wang/CoSky/blob/main/docs/architecture-redesign.md)，如何提交修改见 `CONTRIBUTING.md`。

## 模块

| 模块 | 内容 | 依赖 |
|---|---|---|
| `api` | proto、生成的代码、校验、错误、OpenAPI 文档 | 无 |
| `store` | Store 接口和内存实现 | `api` |
| `store-redis` | Redis Store：唯一包含 Lua 的模块 | `store` |
| `server` | gRPC、REST、Watch 缓存、回收器、鉴权、视图 | `api`、`store`、`store-redis` |
| `client` | 连接、Watch、租约、快照、负载均衡。不含 Spring 和 Redis。 | `api` |
| `spring-boot-starter` | ConfigData 加载、刷新、发现、注册 | `client` |
| `conformance-test` | Store 和协议必须通过的测试套件 | `store`、`client` |
| `migration` | `cosky-migrate`。唯一了解 6.x 的模块。 | `api` |
| `examples` | 一个 provider、一个 consumer，以及运行它们的测试 | starter |
| `dashboard` | React 前端，有自己的构建 | REST API |
| `wiki` | 本站 | |

两条规则把这些维系在一起：除了 server，没有任何东西接触 Store，客户端也看不到 Redis；两种协议是同一份契约，REST 由与 gRPC 相同的 proto 映射而来。

## 构建

```bash
./gradlew build
```

构建需要 JDK 21，server 的测试和 `:server:run` 需要 JDK 25，测试还需要 Docker。两个 JDK 都要自行安装：Gradle 不会下载它们。`./gradlew :server:run --args='--dev'` 启动一个内存模式的 server。

`dashboard/dist` 存在时，server 会把 Dashboard 打包进去：

```bash
pnpm --dir dashboard install && pnpm --dir dashboard build
```

## 测试

| 层次 | 位置 | 针对 |
|---|---|---|
| Store 一致性 | `conformance-test`，由 `store` 和 `store-redis` 运行 | 内存、Redis、Redis Cluster |
| 协议一致性 | `conformance-test`，由 `server` 运行 | 通过 gRPC 访问真实服务 |
| 协议对等 | `ProtocolParityTest` | 同一请求经 gRPC 和 REST 的结果相同 |
| 故障 | `FaultInjectionTest` | Toxiproxy 后面的真实 server：分区、慢链路、挂起的存储 |
| 故障切换 | `RedisStoreFailoverTest` | 丢失写入的真实 Redis |
| 端到端 | `examples/e2e` | 作为独立进程运行的 server、provider 和 consumer |
| TLS | `TlsEndToEndTest` | 两个端口，使用生成的证书 |
| Dashboard | `dashboard/tests/ui` | 行为与 server 相同的 mock |
| 针对镜像的 Dashboard | `dashboard/tests/ui/real-backend.spec.ts` | 镜像托管的页面，连接该镜像 |
| 迁移 | `migration` | 真实 Redis 和真实 server |

行覆盖率下限是 90%，由 `./gradlew build` 强制。新行为应该在去掉实现时让某个测试失败，请实际试一下。

每个 PR 还会运行 Redis 7.0 和 Valkey 8、CodeQL，以及构建镜像、启动并驱动它的任务。

## Lua

全部在 `store-redis` 中。每个脚本只使用一个 namespace 的哈希标签，并声明它用到的键。键的布局见设计文档中关于键的章节。

## 约定

- 协程，由虚拟线程承载。Reactor 只留在适配层，由 Detekt 强制。
- 错误带有稳定的原因，在一处映射到 gRPC 状态码和 HTTP 状态。
- 修改行为时同步更新设计文档，决策在其表格中有 D 编号。
- 测试通过公开接口断言行为，关键之处使用真实依赖。
