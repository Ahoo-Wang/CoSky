---
title: 性能
description: CoSky 的读写速度、测量方法，以及如何复现基准测试。
---

# 性能

应用从**本地缓存**读取配置和实例，一次读取只是一次映射查找，而不是一次 Redis 往返。写入和缓存未命中会访问 Redis，受其延迟限制。

## 结果

JMH 吞吐量，8 线程，Redis 与测试同机（Apple M4 Pro、JDK 17.0.20、Redis 8.10、JMH 1.37），测试对象为 CoSky 6.0.0。原始结果：[`docs/jmh`](https://github.com/Ahoo-Wang/CoSky/tree/main/docs/jmh)。

| 操作 | 途径 | 吞吐量 |
|---|---|---|
| 读取配置 | 本地缓存（`RedisConsistencyConfigService`） | **706,727,232 ops/s** |
| 读取配置 | Redis（`RedisConfigService`） | 58,787 ops/s |
| 保存配置 | Redis | 20,498 ops/s |
| 列出服务 | 本地缓存（`ConsistencyRedisServiceDiscovery`） | **771,907,860 ops/s** |
| 列出服务的实例 | 本地缓存 | **64,012,445 ops/s** |
| 列出服务 | Redis（`RedisServiceDiscovery`） | 71,555 ops/s |
| 列出服务的实例 | Redis | 9,818 ops/s |
| 注册 / 续约 / 注销实例 | Redis | 43,796 / 45,653 / 55,646 ops/s |

如何解读这些数字：

- **缓存读取**随 CPU 核数扩展；这是应用每次访问配置属性、每次负载均衡调用时付出的代价。
- **Redis 操作**主要取决于网络延迟和 Redis 本身。经过真实网络时会更低；在共享的 CI 机器上大约减半。
- 每个数字都是一次 10 秒预热后的单次 10 秒测量：用来判断数量级、在同一台机器上比较不同版本，而不是容量保证。

## 缓存读取为什么便宜

缓存读取返回的是值上次变化时构建好的不可变快照：没有 I/O、没有锁，配置读取甚至不分配内存。保持快照最新的代价是每次变更一条 Redis Pub/Sub 消息，加上重新加载变化的那个键。[架构](./architecture.md#一致性模型)描述了这一机制。

## 复现

在 `localhost:6379` 运行 Redis 后：

```bash
./gradlew :cosky-config:jmh :cosky-discovery:jmh -PjmhThreads=8
# 只运行一个基准测试类
./gradlew :cosky-discovery:jmh -PjmhIncludes=ConsistencyRedisServiceDiscoveryBenchmark
```

结果写入 `<模块>/build/results/jmh/results.json`。可选参数：`-PjmhThreads`、`-PjmhIncludes`、`-PjmhExcludes`、`-PjmhMode`（默认 `thrpt`）。

[基准测试工作流](https://github.com/Ahoo-Wang/CoSky/blob/main/.github/workflows/benchmark-test.yml)在每次推送时运行这两组测试，便于发现提交之间的性能回退。
