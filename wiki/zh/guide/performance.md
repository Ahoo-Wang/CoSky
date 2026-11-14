---
title: 性能
description: CoSky 8.0 的目标、测量结果，以及没有测量的部分。
---

# 性能

测量经过真实的 gRPC 和序列化，server 单独运行在一个 JVM 中（JDK 25，紧凑对象头），连接 Redis 7.4 容器。**压测端和 server 共用一台 10 核笔记本，走本机回环**，除了[模拟网络](#模拟网络)一节，都没有真实网络，压测端还会和 server 争抢 CPU。这些数字仅供参考，请在自己的硬件上测量。

## 结果

| 目标 | 结果 | 判断 |
|---|---|---|
| 单 namespace 50,000 实例和 10,000 Config | 64 个并发写入者 7 秒写完。第二个副本加载后 2 到 3.5 秒就绪。 | 达到 |
| 每个实例约 1 KB | 60,000 个对象占 86 MB，每个约 1.5 KB，含按服务的索引和事件窗口 | 略超 |
| 写入 p99 低于 20 毫秒 | 16 个写入者约 12,000 次/秒时为 2 毫秒，最大 8 毫秒。在模拟网络上，Redis 和客户端各相距 6 毫秒时，单个写入者为 15 毫秒。 | 达到，但丢包 1% 时达不到 |
| 单副本 20,000 连接和 100,000 watch | 全部成功，用时 7 秒，堆增加约 370 MB。 | 达到 |
| 传播 p99 低于 500 毫秒 | 每次写入都投递到所有连接时：5,000 连接 89 ms，10,000 连接 135 ms，20,000 连接 4 次运行为 154 到 189 ms，最大 400 ms。在模拟网络上，投递到 5,000 个相距 58 毫秒、丢包 0.5% 的客户端为 348 ms。 | 达到 |
| `choose()` 和读 Config 不分配内存 | `choose()` 每秒 2,400 万到 5,800 万次，读 Config 每秒约 15 亿次，每次约 0 字节 | 达到 |

传播是按最坏情况测的：所有连接都 watch 同一个服务，压测端还要在同一台机器上收下每一份。真实流量中，一个事件只发给依赖该服务的客户端。

早先 20,000 连接时偶尔出现 1 到 10 秒的长尾，原因在压测端：它的 gRPC 客户端为每条繁忙的流开一个线程执行回调，约 900 个线程与 server 争抢同样的 10 个核，而 server 当时没有 GC 停顿，Redis 也没有慢命令。回调改在传输线程上执行后，长尾消失了。

这些连接数据来自同一台笔记本上的 Linux 容器。在 macOS 上，客户端大约 16,000 个连接就会用完端口，这正是之前的运行停在约 15,000 的原因。压测端和 server 仍然共用 10 个核。

## 事件窗口

在负载下杀掉一个副本，100 个客户端重连到另一个副本，都 watch 同一个服务。掉出窗口意味着重新读取这个服务的快照，代价很小。

| 每秒写入 | 窗口 | 客户端 | 每秒投递 | `gone` |
|---|---|---|---|---|
| 约 160 | 10,000 | 100 | 约 16,000 | 0 |
| 约 1,600 | 10,000 | 5 / 20 | 约 8,000 / 32,000 | 0 |
| 约 1,600 | 10,000 | 100 | 约 160,000 | 97 到 100 |
| 约 1,500 | 50,000 | 100 | 约 150,000 | 0 |

所以 `gone` 只在投递跟不上时出现，大约在每秒 160,000 次。默认值保持 10,000，滚动更新后看 `cosky_watch_gone_total` 就知道你的窗口是否需要调大。

## 大 Config

server 使用 512 MB 堆，反复写同一个 1 MB 的 Config：

| 字节上限 | 写入 | 堆 | Redis |
|---|---|---|---|
| 无 | 约 500 次后开始失败 | 达到 510 MB | 509 MB（484 次后） |
| 64 MB，仅内存 | 800 次全部成功 | 稳定在约 120 MB | 825 MB（800 次后） |
| 64 MB，内存和日志 | 800 次全部成功 | 稳定在约 120 MB | 92 MB（800 次后） |

它在这份日志上 4 秒完成重启，之后又写入了 50 次。裁剪日志对写入速率没有可见影响：交替运行时，16 个写者分别约为每秒 11,400 和 9,600 次，之前是 11,500 和 10,400 次。

## Redis Cluster 故障切换

在 Docker 中三主三从、node timeout 为 5 秒的集群上，一个 server 每 100 ms 向一个 namespace 写一次 Config，同时停掉这个 namespace 所在的主节点：

| 主节点 | namespace 不可用 |
|---|---|
| 被杀掉（`docker kill`） | 15.8 秒 |
| 被冻结（`docker pause`） | 15.7 秒 |

其中是 node timeout、副本的选举，再加最多 10 秒：server 每 5 秒读一次拓扑，每次读取最多等停掉的节点 5 秒。在此期间，这个 namespace 上的每个请求都在 5 秒后以 `STORE_UNAVAILABLE` 失败。8.0.0-alpha.5 在 kill 之后不可用 53 秒；它不会再次读取拓扑，所以主节点被冻结时，要等到这个节点重新应答才恢复。

## 很多 namespace 与 Redis Cluster

三个副本跟随很多个 namespace，每个 50 个 Instance，分别连单个 Redis 7.4 和三主三从的 Redis Cluster，都在同一台笔记本上的一个容器里。16 个写入方经一个副本写 32,000 个 Config，每次写到随机挑选的 namespace，另一个副本上的客户端 watch 其中 100 个 namespace。

| | 1,000 个 namespace，单个 Redis | 1,000 个 namespace，Cluster | 5,000 个 namespace，单个 Redis | 5,000 个 namespace，Cluster |
|---|---|---|---|---|
| 第二、第三个副本就绪 | 3.3 和 4.0 秒 | 3.4 和 4.1 秒 | 4.7 和 6.7 秒 | 5.2 和 7.5 秒 |
| 空闲时 Redis | 每秒 21,000 条命令，CPU 11% | 每个主节点每秒 6,700 条，CPU 4% | 每秒 129,000 条，CPU 30% | 每个主节点每秒 42,000 条，CPU 11% |
| 写入 | 每秒 8,354 次，p99 5 ms | 每秒 7,809 次，p99 7 ms | 每秒 6,739 次，p99 9 ms | 每秒 6,240 次，p99 9 ms |
| 写入时 Redis | CPU 77% | 每个主节点 CPU 39% 到 43% | CPU 84% | 每个主节点 CPU 48% 到 55% |
| 写入到达另一个副本上的 watcher，p99 | 14 ms | 23 ms | 8 ms | 16 ms |

空闲时，为防提示丢失，每个副本每秒读每个 namespace 一次，每次约七条 Redis 命令。这部分负载随 namespace 数乘以副本数增长，与客户端数量无关。单个 Redis 在 5,000 个 namespace、三个副本时用到 30% 的 CPU；Cluster 把它分摊到各主节点，调大 `cosky.store.redis.tail-poll-interval` 也能降低它，代价是提示丢失后恢复得更慢。

三个副本时，一次写入约 35 条 Redis 命令：写入本身，加上每个副本对该 namespace 的一次读取。在 Cluster 中，写入发布的提示会到达每个节点，表现为每个主节点每秒收到 4,000 到 5,000 条消息，没有看到明显代价。这里限制写入的是笔记本而不是 Redis：单个 Redis 已接近饱和，而 Cluster 的主节点还剩一半 CPU。

在 Cluster 上重新测量上面的目标，结果与单个 Redis 一致：一个 namespace 中 50,000 个 Instance 和 10,000 个 Config，16 个写入方每秒 17,500 次写入、p99 2 ms，5,000 个连接 watch，传播 p99 103 ms，第二个副本 2.9 秒就绪。

## 模拟网络

在 server 与 Redis 之间、客户端与 server 之间各加一段网络，重新测量上面的目标：5,000 个连接 watch，一个 namespace 中 50,000 个 Instance 和 10,000 个 Config。每段链路是容器里的一个 [Toxiproxy](https://github.com/Shopify/toxiproxy) 中继，由 `netem` 整形它的网卡，每个方向只延迟一次。往返时间是逐个请求实测的，因为这里的 `netem` 对链路上单独的一个包会比设定多延迟几毫秒（见[如何运行](#如何运行)）。

| | 本机回环 | Redis 和客户端各相距 6 ms | 同上，丢包 0.1% | 7 ms 带抖动，丢包 1% | 客户端相距 58 ms，丢包 0.5% |
|---|---|---|---|---|---|
| `netem`，每个方向 | 无 | 0.1 ms | 0.1 ms，丢包 0.1% | 1 ± 0.5 ms，丢包 1% | Redis 0.1 ms；客户端 25 ± 5 ms，丢包 0.5% |
| 到 Redis 的往返，p50 | 0.3 ms | 5.9 ms | 5.9 ms | 7.1 ms | 5.8 ms |
| 到 server 的往返，p50 | 0.4 ms | 5.7 ms | 5.9 ms | 6.1 ms | 57.8 ms |
| 写入，单个写入者，p50 和 p99 | 不到 1 ms | 9 和 15 ms | 9 和 15 ms | 15 和 232 ms | 62 和 294 ms |
| 写入，16 个写入者 | 每秒 17,422 次，p99 2 ms | 每秒 4,973 次，p99 9 ms | 每秒 4,454 次，p99 11 ms | 每秒 1,177 次，p99 24 ms | 每秒 253 次，p99 96 ms |
| 传播到 5,000 个连接，p50 和 p99 | 57 和 120 ms | 76 和 127 ms | 79 和 254 ms | 91 和 361 ms | 110 和 348 ms |
| 第二个副本就绪 | 3.4 秒 | 3.6 秒 | 3.4 秒 | 6.4 秒 | 3.5 秒 |

经过中继但不加延迟时，结果与本机回环一致：传播 p99 115 ms，回环是 120 ms。

同一个机房的往返是 0.1 到 0.5 毫秒，介于本机回环和这里的 6 毫秒之间。单个写入者的每次写入都单独经过链路，达到了 20 毫秒的目标：p99 15 ms。传播 p99 为 127 ms，回环是 120 ms。16 个写入者离得越远写得越少，因为每个都要等上一次的应答：这是压测端的限制，不是 server 的。

丢包比距离代价更大。TCP 常常要等一个超时才重发丢失的包，在 Linux 上至少 200 毫秒。一次写入从写入方经 server 到 Redis 再返回，途中丢一个包，就会让这次写入的事件在每个连接上都晚到。测量的 100 次写入中，有一次这样的写入就决定了 p99，0.1% 的丢包让它翻了一倍，到 254 ms。每段链路都丢包 1% 时，单个写入者的 p99 是 232 ms：一次写入要经过链路四次，各有自己的包，所以超过 1% 的写入会丢一个包，等它重发。在这里的每种网络上，传播 p99 都低于 500 ms；最慢的一个事件用了 779 ms，在丢包 1% 时。

经过到那些客户端的链路，加载这个 namespace 用了 60 秒（回环 6 秒），打开所有 watch 用了 224 秒（回环 14 秒）：压测端每次并发 64 个写入，每个都要等 58 ms；5,000 个客户端的所有快照都经过同一个中继、同一个 `netem` 队列，而真实的客户端各走各的路径。

### 租约

100 个客户端经一段链路，各在自己的租约上挂一个 Instance（TTL 30 秒，每 10 秒续约），持续 300 秒，同时本机回环上的一个客户端 watch 这些 Instance：

| 客户端的链路 | 注册用时 | 看到的 Instance 最少时 | 过期的租约 |
|---|---|---|---|
| 每个方向 25 ± 5 ms，丢包 0.5%：往返 58 ms | 4.0 秒 | 100 个中的 100 个 | 0 |
| 每个方向 100 ± 20 ms，丢包 5%：往返约 200 ms | 17.5 秒 | 100 个中的 100 个 | 3 |

在第二段链路上，一个调用可能要等几次重发，所以注册用了 17.5 秒。过期的三个租约上没有 Instance：watcher 每 100 毫秒看一次，始终看到全部 100 个；而租约过期的 Instance 会一直消失，直到它的客户端在下一次续约时（最多 10 秒后）得知，并重新注册。这符合这样的情况：某次授权调用的应答没有到达客户端，客户端又申请了一次；它留着拿到的那个租约，server 让另一个过期。

### 如何运行

压测端和 server 在笔记本 Docker 虚拟机（OrbStack）里的一个 Linux 容器中运行，用的是虚拟机自己的网络，Redis 和中继各在自己的容器里，所以它们之间只隔着虚拟机的内核。如果经过虚拟机到 macOS 的端口转发，即使不加 `netem` 也会丢包，投递到 100 个连接的事件约有 5% 因重发晚到 200 毫秒。在这个虚拟机里，`netem` 对链路上单独的一个包会比设定多延迟最多几毫秒，因为放行它的定时器触发得晚；流量持续时，包放行得更准时。这就是 0.1 ms 对单个请求变成 6 ms 往返的原因，也是 16 个写入者的写入反而比单个写入者快的原因。它的抖动还会让包乱序，真实路径很少这样。此外，server、Redis、中继和压测端共用同样的 10 个核。

## 没有测量的

- 真实网络或真实机房：上面的网络是在一台笔记本上模拟的。
- 连接负载下的多个 server 副本。
- 超过三个主节点的 Cluster，或超过三个副本。
- 长时间运行：几天的内存变化和泄漏。

## 复现

```bash
COSKY_MEASURE=load COSKY_MEASURE_HEAP=4g ./gradlew :server:test --tests '*LoadMeasurement*'
```

`COSKY_LOAD_CONNECTIONS`、`COSKY_LOAD_WATCHES_PER_CONNECTION`、`COSKY_LOAD_PROPAGATION_PAUSE_MS`、`COSKY_LOAD_RATES`、`COSKY_LOAD_WINDOW` 和 `COSKY_LOAD_WINDOW_CLIENTS` 控制规模。`COSKY_LOAD_SERVER_HEAP` 设置 server 的堆，`COSKY_LOAD_REDIS_URI` 使用你自己启动的 Redis，而不是测量自己启动的容器。加上 `COSKY_LOAD_REDIS_CLUSTER=true` 时，它是 Redis Cluster 的一个节点，各节点宣告的地址必须是运行测量的地方能连上的。多个副本跟随很多 namespace 是另一个测试，由 `COSKY_LOAD_NAMESPACES`、`COSKY_LOAD_OBJECTS_PER_NAMESPACE`、`COSKY_LOAD_REPLICAS` 和 `COSKY_LOAD_WRITES` 控制规模：

```bash
COSKY_MEASURE=load COSKY_MEASURE_HEAP=2g COSKY_LOAD_SERVER_HEAP=2g ./gradlew :server:test --tests '*LoadMeasurement.many namespaces*'
```

`COSKY_LOAD_REDIS_NETEM` 和 `COSKY_LOAD_CLIENT_NETEM` 分别在 server 与 Redis 之间、客户端与 server 之间加一段链路，取值是 `tc qdisc ... netem` 的参数。经过这样一段链路的租约是另一个测试，由 `COSKY_LOAD_LEASE_CLIENTS` 和 `COSKY_LOAD_LEASE_SECONDS` 控制规模。在笔记本上，请在 Linux 容器中、用 Docker 主机的网络运行它们，用 JDK 21 构建，server 用 Linux 版的 JDK 25，Gradle 从 `org.gradle.java.installations.paths` 找到它：

```bash
docker run --rm --network host -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/work -v /path/to/jdk-25:/opt/jdk25 -w /work \
  -e TESTCONTAINERS_HOST_OVERRIDE=localhost -e COSKY_MEASURE=load -e COSKY_MEASURE_HEAP=3g -e COSKY_LOAD_SERVER_HEAP=2g \
  -e COSKY_LOAD_CLIENT_NETEM='delay 25ms 5ms loss 0.5%' eclipse-temurin:21-jdk \
  ./gradlew :server:test --tests '*LoadMeasurement.the targets*' -Porg.gradle.java.installations.paths=/opt/jdk25
```

客户端的热路径是 JMH 基准：

```bash
./gradlew :client:jmh
```
