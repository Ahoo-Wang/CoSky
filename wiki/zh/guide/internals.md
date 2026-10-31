---
title: 内部实现
description: 面向贡献者的代码地图、代码所守护的不变量，以及调试方法。
---

# 内部实现

本页面向贡献者。流程（环境、规范、测试、PR、发版）见 [CONTRIBUTING.md](https://github.com/Ahoo-Wang/CoSky/blob/v6/CONTRIBUTING.md)；设计见[架构](./architecture.md)。

## 代码在哪里

| 要修改… | 看这里 |
|---|---|
| 一致性缓存 | `cosky-core`：`cache/ReactiveWatchCache.kt` |
| Lua 脚本加载与 `-- @include` | `cosky-core`：`redis/LuaScripts.kt` |
| 配置存储、版本 | `cosky-config`：`redis/RedisConfigService.kt`、`src/main/resources/config_*.lua`、`cosky/config/config_lib.lua` |
| 注册、续约、过期 | `cosky-discovery`：`redis/RedisServiceRegistry.kt`、`RenewInstanceService.kt`、`registry_*.lua`、`cosky/discovery/discovery_lib.lua` |
| 带缓存的服务发现 | `cosky-discovery`：`redis/ConsistencyRedisServiceDiscovery.kt` |
| Redis 键名 | `ConfigKeyGenerator`、`DiscoveryKeyGenerator`（以及 Lua 库中的键函数） |
| Spring 装配与属性 | `cosky-spring-cloud-*`：`*AutoConfiguration.kt`、`*Properties.kt` |
| REST 接口 | `cosky-rest-api`：`*Controller.kt`；路径在 `support/RequestPathPrefix.kt` |
| 安全 | `cosky-rest-api`：`security/` 与 `src/main/resources/cosky-policy.json` |
| 控制台 | `dashboard/`（React；见其 README） |
| 文档 | `wiki/`（VitePress；规范见 `wiki/AGENTS.md`） |

## 不变量与守护它们的测试

| 不变量 | 守护者 |
|---|---|
| 每次 Redis 修改都是一个发布变更的 Lua 脚本 | 代码评审；`ConsistencyRedisServiceDiscoveryTest`、`RedisConsistencyConfigServiceTest` 观察事件 |
| Lua 与 Kotlin 中的键名一致 | `ConfigLuaKeysTest`、`DiscoveryLuaKeysTest` |
| 缓存先订阅再加载，并按顺序应用事件 | `ReactiveWatchCacheTest` |
| 加载失败永远不会被缓存 | `ReactiveWatchCacheTest`、`*ErrorTest` |
| 不依赖服务器即可清理过期实例 | `InstanceExpiryReaperTest` |
| REST API 执行认证与 RBAC | `RestApiIntegrationTest` |

[需求](./requirements.md)把每个用户可见的行为对应到验证它的测试。

## 修改 Redis 数据模型

键结构和 Lua 脚本是同时运行的不同客户端版本之间的契约，因此：

1. 同时在键生成器**和** Lua 库中添加键，并扩展对应的 `*LuaKeysTest`。
2. 脚本操作的每个键都通过 `KEYS` 传入（Redis Cluster 需要），并在被修改键的频道上发布消息。
3. 考虑旧版本客户端：新的事件内容或字段不能让它们出错，否则就必须是可选开启的（就像 `renew-event-carries-ttl-at`）。
4. 在[架构](./architecture.md#数据模型)中描述变更；如果影响升级，还要写进升级指南。

## 测试

- 测试针对 `localhost:6379` 上的真实 Redis 运行；继承 `AbstractReactiveRedisTest`（`cosky-test` 模块）即可获得 template 与清理逻辑。
- 断言使用 `me.ahoo.test.asserts.assert`，协作者使用 MockK。
- `./gradlew check` 还会运行 detekt、许可证头检查和覆盖率门槛。

## 调试

```bash
redis-cli --scan --pattern '{dev}:*'              # 命名空间中的键
redis-cli hgetall '{dev}:svc_itc:order-service@http#10.0.0.5#8080'
redis-cli psubscribe '{dev}:*'                    # 观察命名空间的所有事件
redis-cli zrange '{dev}:svc_itc_exp' 0 -1 withscores   # 按过期时间排列的临时实例
```

把 `logging.level.me.ahoo.cosky` 调到 `debug`，可以记录缓存加载、事件和续约。

## 基准测试

JMH 基准测试位于 `cosky-config` 和 `cosky-discovery` 的 `src/jmh` 下；见[性能](./performance.md#复现)。修改热点路径时，请在同一台机器上对比修改前后。
