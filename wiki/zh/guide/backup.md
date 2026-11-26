---
title: 备份与恢复
description: CoSky 在 Redis 中保存了什么，怎样配置 Redis 才能保住它，怎样备份和恢复，以及恢复对应用意味着什么。
---

# 备份与恢复

本页面向运维人员。server 不在磁盘上保存任何东西：所有需要持久保存的内容都在 Redis 中。这里说明其中有什么，怎样让 Redis 保住它，以及怎样把它找回来。

## Redis 中有什么

CoSky 的所有键都以 `cosky:` 开头，每个 namespace 的键共用哈希标签 `cosky:{<namespace>}:`，所以一个 namespace 位于一个 slot 中。键的布局见[设计文档](https://github.com/Ahoo-Wang/CoSky/blob/main/docs/architecture-redesign.md)。

| 内容 | 说明 |
|---|---|
| 每个对象，以及它的 revision、时间和最后修改者 | Config 和 Instance 在它们的 namespace 中。Namespace、ServiceAccount、Role 和 RoleBinding 在 `_system` 中。 |
| 每个 Config 最近 20 个 revision | 它的历史，用于回滚 |
| 每个 namespace 的变更日志 | 最多 `cosky.watch.window` 条、`cosky.watch.window-bytes` 字节。客户端从这里续传。 |
| 每个 namespace 的 revision 和 epoch | 客户端位置的含义，详见[架构](./architecture.md#redis-丢失写入时) |
| 租约，以及它们的到期时间、TTL 和所有者 | 挂在租约上的实例随它到期而删除 |
| 拓扑 | 每个副本每 30 秒重新写入一次 |
| 回收器的锁 | 只持有几秒 |

token 只保存它的 SHA-256，所以备份中没有能用的 token。但备份中有原样的 Config 内容，没有加密：请像保护 Redis 一样保护备份。

有三样东西不在 Redis 中：[审计日志](./security.md#审计)，它在 server 的日志里；bootstrap token，它在你的环境变量或 Secret 里；客户端的[快照](./sdk.md#快照)，在它们自己的磁盘上。

## Redis 必须做到的

- **`maxmemory-policy noeviction`。** 没有它 server 拒绝启动，详见[部署](./deployment.md#redis)。
- **AOF：`appendonly yes`。** 它默认的 `appendfsync everysec` 在 Redis 崩溃时最多丢失大约最后一秒的写入。没有 AOF 时，重启会让 Redis 回到它最后一份 RDB 快照，按默认的保存点可能是一小时前的，此后写入的一切都会丢失。
- **一块比容器活得更久的磁盘**，存放 Redis 的数据目录。
- **`min-replicas-to-write 1`**，加上 `min-replicas-max-lag 10`，设置在有副本的主节点上。复制是异步的，所以故障切换可能丢失还没到达副本的写入。这两项缩小这个窗口。代价是：没有已连接且跟上的副本时，主节点拒绝写入，CoSky 返回 `STORE_UNAVAILABLE`，读取和正在运行的应用不受影响。

Redis 默认就是 `noeviction`。在 Docker 中，一个卷加一个参数就满足其余各项：

```bash
docker run -d --name redis --network cosky -v cosky-redis:/data redis:7.4 --appendonly yes
```

托管 Redis 有它自己的这些设置。请确认持久化已开启，淘汰策略是 `noeviction`。

## Redis 丢失数据时

### 故障切换丢失最后的写入时

故障切换前刚写入的 Config 可能已经丢失，token 的吊销或角色的修改也可能丢失。CoSky 会发现 revision 回退：`cosky_revision_regression_total` 上升，某个副本记录一条错误，写明 namespace 和它回退到的 revision。随后每个副本重新加载这个 namespace，每个客户端从 Redis 现有的内容重新开始，应用重新注册缺失的内容，详见[架构](./architecture.md#redis-丢失写入时)。

丢失的内容仍然丢失，Config 的历史中也没有了。审计日志仍然记着每一次已确认的写入，带 namespace、名称和 revision：这个 namespace 中高于回退位置的那些写入，就是需要重做的。

### Redis 丢失全部数据时

如果 Redis 丢失了全部数据，server 会在大约 30 秒内自行按首次启动的方式重建：系统 namespace、`default`，以及由 bootstrap token 生成的管理员。它会记录错误 `The system namespace has no state: the store lost its data.`。

所以按[安全](./security.md#第一个-token)的建议吊销 bootstrap token 之后，请把环境变量或 Secret 换成一个没人用过的新 token，作为应急凭据保管，或者删除它：否则已吊销的那个会重新成为管理员。

其余内容都已丢失：其他 namespace、Config、Role、RoleBinding，以及 ServiceAccount 和它们的 token。请恢复备份，或者重新创建它们，并导入 Config 的 [zip 导出](#把-config-导出为-zip-文件)。开启鉴权时，应用会继续提供已知的数据，但在拿到新 token 之前都不会重新注册。关闭鉴权时，等它的 namespace 重新存在后，应用会跟随其中的内容，并重新注册自己。

## 备份

### 用 RDB 快照备份全部数据

RDB 快照是某一时刻 Redis 中的全部数据。让 Redis 写一份，确认写完，再从它的数据目录复制 `dump.rdb`：

```bash
redis-cli BGSAVE
redis-cli LASTSAVE   # 快照写完后会变化
```

Redis Cluster 的每个主节点各有一份：每个主节点都要备份。托管 Redis 有它自己的快照。

### 把 Config 导出为 zip 文件

namespace 的[导出](./configuration.md#导入与导出)包含每个 Config 的名称和内容。它不包含格式、标签和历史，也不包含 Config 以外的任何东西。它可以在不同安装和版本之间迁移，并且可以直接阅读：

```bash
curl -o prod.zip -H "Authorization: Bearer $TOKEN" https://cosky.example.com/v1/namespaces/prod/configs:export
```

它只包含这个 token 能读的 Config，所以请用能读全部 Config 的 token。导入到还不存在的位置时，Config 采用扩展名对应的格式：`.yaml` 或 `.yml`、`.properties`、`.json`，其他都是文本。已经存在的 Config 保留它的格式和标签，换上新内容。

## 恢复

### 从 RDB 快照恢复

恢复期间请让至少一个 server 保持运行。它记得每个 namespace 的位置，能发现 revision 回退。

开启 AOF 时，Redis 启动时加载 AOF，忽略 `dump.rdb`；开启了 AOF 但还没有 AOF 文件时，它以空数据启动。所以：

1. 停止 Redis。
2. 把它的 `appendonlydir` 移走，把快照放到数据目录中，命名为 `dump.rdb`。
3. 以 `appendonly no` 启动 Redis。它会加载这份快照。
4. 执行 `redis-cli CONFIG SET appendonly yes`，Redis 会根据现有数据写一份新的 AOF；等 `INFO persistence` 显示 `aof_rewrite_in_progress:0`。
5. 在 Redis 读取配置的地方重新设置 `appendonly yes`，供它下次启动。

这在 Redis 7.4 上试过，server 在运行，一个应用保持连接。server 记录了 `Namespace default diverged`，并替换了 epoch。应用立即采用了它的 Config 恢复后的内容，并在一次续期之内重新注册了它的实例，因为这个实例比快照新。

### 恢复对应用意味着什么

恢复等于 Redis 丢失了快照之后的每一次写入，所以过程[同上](#故障切换丢失最后的写入时)，只是作用于每个 namespace：

- **Config** 回到原来的内容，应用以这些值刷新。
- **Instance**：此后注册的会消失，它们的进程会重新注册。此后删除的会回来：有租约的在租约到期后消失，固定实例会一直留着，直到你再删除一次。
- **租约**：快照中的租约到这时都已到期。活着的进程会像任何一次故障之后那样续期，回收器的宽限和[大面积到期保护](./architecture.md#租约)会让它不先回收它们。
- **token**：此后创建的会消失，使用它们的应用会被拒绝。**此后吊销的 token 会重新生效：请再吊销一次。** Role 和 RoleBinding 也会回到原来的状态。

如果每个 server 都随 Redis 一起重启，就没有谁记得 namespace 原来的位置。带着超出快照的 revision 回来的应用会被告知它的位置已失效，并重新开始。但如果新的写入已经让 revision 涨到它的位置，它可能在错误的时间线上续传，直到它定期的全新快照，大约每 30 分钟一次。所以恢复期间应当让一个 server 保持运行。

### 从 zip 文件恢复

导入到一个已经存在的 namespace：

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" https://cosky.example.com/v1/namespaces/prod/configs:import --data-binary @prod.zip
```

它覆盖同名的 Config，其他的保持不变。每次导入都是一次普通的写入，应用会跟随它。
