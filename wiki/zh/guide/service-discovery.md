---
title: 服务发现
description: 把实例挂在租约上注册，通过 Spring Cloud 查找，并了解出故障时会发生什么。
---

# 服务发现

**Instance** 是服务的一个运行副本，包含服务名、端点、权重和元数据。服务就是共用一个名称的一组 Instance。

## 注册

使用 starter 时，应用在 Web 服务器就绪后注册，关闭时自行移除。

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

- 服务名就是 `spring.application.name`，必须是 DNS 风格的名称：小写字母、数字、`-` 和 `.`，最长 63 个字符。`OrderService` 会被拒绝。
- 单独的管理端口不会注册。
- `spring.cloud.service-registry.auto-registration.enabled: false` 可以关闭注册。

## 租约

一个进程在一个 namespace 中只持有一个租约，无论它注册多少实例，并在每过 TTL（默认 30 秒）的三分之一时续约一次。续约停止后，它的实例就会消失。

| 事件 | 结果 |
|---|---|
| 正常关闭 | 3 秒内撤销租约，实例立即消失。 |
| 崩溃 | 租约到期，再过 TTL 的三分之一后实例消失，默认共 40 秒。 |
| 租约丢失，比如 Redis 故障切换之后 | 客户端发现后最多等待 5 秒，避免所有客户端同时回来，然后重新注册全部实例。 |
| 客户端重连 | 立即续约，不等下一个周期。 |

允许的 TTL 是 5 到 300 秒。

## 查找服务

`DiscoveryClient` 和 `ReactiveDiscoveryClient` 读取本地视图。首次读取一个服务时最多等待 `cosky.discovery.load-timeout`（5 秒），避免把"还不知道"当成"没有实例"。如果 server 拒绝（没有权限或 namespace 不存在），读取会立即抛出 `CoSkyException`。之后的读取不需要网络往返。

加入 `spring-cloud-starter-loadbalancer` 后，`@LoadBalanced` 客户端从 CoSky 获取实例，由 Spring Cloud 自己的算法选择。

## 权重

权重为 `0` 的实例仍然注册，但不接收流量，用来摘流：负载均衡器会排除它，`DiscoveryClient` 仍会列出它。Kotlin 客户端的负载均衡器按权重比例随机选择。Spring Cloud LoadBalancer 默认的轮询对其他权重一视同仁。

运维人员也可以对应用注册的实例这样设置，效果持续到应用重新注册为止，也就是重启或丢失租约之前。

## 固定实例

不挂租约的 Instance 会一直保留，直到有人删除它。适用于无法自行注册的对象，比如数据库：

```bash
curl -X PUT localhost:8080/v1/namespaces/prod/instances/orders-db \
  -H 'Content-Type: application/json' \
  -d '{"instance":{"service":"orders-db","endpoint":{"scheme":"tcp","host":"10.0.0.5","port":3306}}}'
```

Dashboard 把实例标为 **Leased** 或 **Fixed**。没有任何机制检查固定实例是否存活。

## 出故障时

CoSky 宁可保留一个过期的实例，也不错删活着的实例，所以调用方应该重试并使用熔断。

| 故障 | 应用看到什么 |
|---|---|
| server 不可达 | 继续读取最后的视图；健康指示器保持 `UP`，并带 `connection: DEGRADED`，因为重启也没有帮助。 |
| server 不可用时重启 | 从磁盘[快照](./sdk.md#快照)恢复视图。 |
| server 或网络中断超过 TTL | 回收器恢复后等待 30 秒，让存活的客户端有机会续约，而不是被移除。 |
| 一个 namespace 超过一半的租约同时到期 | 暂停回收，因为这看起来像网络分区。比例回落后恢复，最多暂停 5 分钟。 |
| Redis 故障切换并丢失最近的写入 | 每个副本都会发现 revision 回退，客户端从新的快照重新开始。丢失的写入不会回来。 |

这些都可以在 [`cosky.lease`](./properties.md#server) 下配置。在这样的暂停期间真正崩溃的进程，最多会多列出 5 分钟。

## 谁在调用谁

客户端 watch 一个服务就算作依赖它。[Dashboard](./dashboard.md) 的拓扑图和 `/views/topology` 显示这些边，使用 `spring.application.name` 作为名称，没有时使用登录主体。最后一个副本看到 watch 之后 90 秒这些边就会过期，所以已停止的消费者会自行消失。
