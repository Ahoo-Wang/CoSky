---
title: 从 6.x 迁移
description: 用 cosky-migrate 把 CoSky 6.x 一次性整体切换到 8.0。
---

# 从 6.x 迁移

CoSky 8.0 是一套新系统，6.x 的任何东西都不会自动延续。6.x 的应用直连 Redis，8.0 的应用连接 server，所以**两代应用互相看不到对方的实例**。没有桥接：应用一起迁移，一次切换完成。

如果迁移期间确实有服务需要跨代调用，请并行运行两套环境，并在整个环境之间切换流量。

## 有哪些变化

| | 6.x | 8.0 |
|---|---|---|
| 应用连接 | 直连 Redis | 通过 gRPC 连接 server |
| 应用认证 | 无 | 每次调用都带 token |
| Starter | `cosky-spring-cloud-starter-config`、`cosky-spring-cloud-starter-discovery` | `cosky-spring-boot-starter` |
| 加载配置 | `bootstrap.yaml` | `spring.config.import` |
| 配置项 | `spring.cloud.cosky.*`、`spring.data.redis.*` | `cosky.*` |
| Namespace | 任意名称，常见 `{dev}` 或 `cosky-{default}` | DNS 标签，如 `dev` 或 `default` |
| 服务名 | 任意 | DNS 风格的小写名称 |
| Config 格式 | 按扩展名推断 | 随 Config 声明 |
| 保持存活 | 每个实例自己的 TTL | 每个进程一个租约 |
| 没有 Web 服务器的应用 | 设置 `registry.port` 后会注册 | 不会自动注册，需调用客户端的 `instances.register` |
| 让实例停止接收流量 | `ServiceRegistry.setStatus` | 把权重设为 `0` |
| 人员 | 带密码的用户 | OIDC 或 ServiceAccount token |
| 权限 | 按 namespace 读或写 | 角色可细到类型、动作和名称前缀 |
| 审计 | Dashboard 中的页面 | 日志中的 JSON 行 |
| Redis | 任意版本 | 7.0 或更高，`noeviction` |
| JDK | 17 | 客户端 21，server 25 |

## 迁移什么

| | 方式 |
|---|---|
| Config，可选带历史 | `cosky-migrate` |
| 自行注册的实例 | 切换后自动在 8.0 上注册 |
| 固定实例 | 按报告手工重建 |
| 用户和角色 | 按报告手工重建 |
| 审计日志和拓扑 | 不迁移。审计日志留在 6.x 的 Redis 中，拓扑会自行重建。 |

revision 和时间戳重新开始。在 6.x 中已删除的 Config 没有当前版本，不会迁移。

## 构建工具

```bash
./gradlew :migration:installDist
```

工具位于 `migration/build/install/cosky-migrate/bin/cosky-migrate`，需要 JDK 21 或更高版本。

## 切换步骤

**1. 启动 8.0。** 如果 6.x 的 Redis 满足[需求](./requirements.md)，可以共用：两者的键不重叠。

**2. 在 6.x 仍运行时演练。** 这会读取两边，不写入任何内容，并生成一份报告：

```bash
export COSKY_TOKEN=<bootstrap token>
cosky-migrate --source redis://6x-redis:6379 --target http://cosky:8080 --dry-run
```

阅读 `cosky-migration-report.md`：

- **要改名的服务。** `OrderService` 无法在 8.0 上注册，请修改 `spring.application.name`。
- **8.0 无法命名的 namespace。** `cosky-{default}` 对应 `default`，`{dev}` 对应 `dev`，其他不合法的名称会让运行停止。用 `--namespace 'Old_Name=new-name'` 指定名称，或用 `--namespace 'Old_Name='` 排除。6.x 从未登记过的 namespace 也要这样指定。
- **8.0 拒绝的 Config**，比如名称中带 `:` 或内容超过 1 MiB。

**3. 重建人员和权限。** 报告列出每个 6.x 角色及其每条授权对应的 8.0 规则，以及每个用户和他们的角色。创建这些角色和绑定，并为每个应用创建一个带 token 的 ServiceAccount。在开启认证的 server 上，没有 token 的应用什么也做不了。

**4. 准备应用。** 替换 starter，把 `bootstrap.yaml` 的内容移到 `application.yaml`，并设置 token。在 Spring Boot 4.1 上，在 Spring Cloud 2026.0 发布之前还要设置 `spring.cloud.compatibility-verifier.enabled: false`：

```yaml
spring:
  application:
    name: order-service
  config:
    import: "cosky:order-service.yaml"
cosky:
  endpoints: cosky.internal:9090
  token: ${COSKY_TOKEN}
  namespace: prod
```

**5. 停止向 6.x 写入 Config，然后导入。**

```bash
cosky-migrate --source redis://6x-redis:6379 --target http://cosky:8080 --history
```

退出码 `0` 表示全部到达，`1` 表示有些 Config 需要处理，`2` 表示无法运行或提前停止。再次运行会继续被中断的导入，不会改动其他内容。

**6. 切换。** 把应用部署到 8.0，并停止 6.x。然后按报告创建固定实例：没有任何东西为它们续约，也就没有任何东西会重新注册它们。

## 工具不会做的事

- **覆盖修改。** 如果 Config 在 8.0 上的内容与 6.x 现存的任何版本都不一致，说明它在 8.0 上被改过，工具会保留它并写入报告。如果确实要导入，请先在 8.0 上删除它。
- **改动 6.x。** 工具只读，也从不读取密码哈希。
- **把机密写进报告。** 报告只含名称、地址和权限，不含 Config 内容或元数据的值。

Redis 带密码时，请用 `COSKY_SOURCE_URI` 代替 `--source`，这样 `ps` 看不到。其他选项见 `cosky-migrate --help`。

## 回退

在停止 6.x 之前，回退就是把应用重新部署到 6.x。没有任何东西写入它的键，但切换后在 8.0 上修改的 Config 只留在 8.0。
