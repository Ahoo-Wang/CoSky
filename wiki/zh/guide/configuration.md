---
title: 配置管理
description: 把 CoSky 中的配置加载进 Spring 并实时刷新，管理版本、回滚、导入与导出。
---

# 配置管理

本页面向使用 config starter 的应用开发者，以及在控制台中管理配置的人。接入步骤见[快速开始](./getting-started.md)。

## 应用加载哪个配置

启动时 starter 加载一个配置，由**命名空间**和**配置 ID** 确定：

| | 属性 | 默认值 |
|---|---|---|
| 命名空间 | `spring.cloud.cosky.namespace` | `cosky-{default}` |
| 配置 ID | `spring.cloud.cosky.config.config-id` | `${spring.application.name}.${file-extension}` |
| 默认扩展名 | `spring.cloud.cosky.config.file-extension` | `yaml` |

这些属性要写在 `bootstrap.yaml` 中，并且应用需要依赖 `spring-cloud-starter-bootstrap`（见[快速开始](./getting-started.md#_2-添加依赖)）。

- **格式**取决于配置 ID 的扩展名：`.yaml` / `.yml`、`.properties` 或 `.xml`。没有扩展名的配置 ID 使用 `file-extension`。
- 支持**多文档 YAML**（`---`），后面的文档覆盖前面的。
- **优先级**：与任何 Spring Cloud bootstrap 属性源一样，CoSky 中的属性覆盖 `application.yaml` 等本地来源。
- **配置不存在**：应用照常启动，打印警告，不带任何 CoSky 属性。
- **Redis 不可达**：最多等待 `spring.cloud.cosky.config.timeout`（2 秒）。

多个服务可以把 `config-id` 指向同一个配置来共享设置。

## 实时刷新

配置变更后，所有使用它的应用通过 Redis Pub/Sub 收到变更（通常在毫秒内），starter 随即发布 Spring Cloud 的 `RefreshEvent`。哪些值会更新取决于读取方式：

| 读取方式 | 变更后是否更新 |
|---|---|
| `@ConfigurationProperties` bean | ✅ 重新绑定 |
| `@RefreshScope` bean 中的 `@Value` | ✅ 下次使用时重建 bean |
| 普通单例中的 `@Value` | ❌ 保持启动时的值 |
| `Environment.getProperty(...)` | ✅ |

刷新事件只在本地缓存已持有新值之后才发布，所以刷新后的 bean 不会读到旧版本。应用如果漏掉了某条消息（例如 Redis 重连期间），它的缓存会在 5 分钟内重新同步。

## 版本与回滚

每次改变内容的保存都会产生新版本；保存相同内容不会有任何变化。CoSky 为每个配置保留**最近 10 个版本**，可以回滚到其中任意一个；回滚会把旧内容保存为新版本，所以回滚本身也能撤销。已删除的配置同样可以回滚。

在控制台中，**Config → History** 列出各个版本，并显示与当前内容的差异。通过 API：

```bash
GET  /v1/namespaces/{namespace}/configs/{configId}/versions             # 版本列表
GET  /v1/namespaces/{namespace}/configs/{configId}/versions/{version}   # 某个版本的内容
PUT  /v1/namespaces/{namespace}/configs/{configId}/to/{targetVersion}   # 回滚
```

## 导入与导出

- **导出**（`GET /v1/namespaces/{namespace}/configs/export`）把命名空间中的所有配置下载为 zip，每个配置一个文件。
- **导入**（`POST /v1/namespaces/{namespace}/configs`，multipart 字段 `importZip`）为每个文件创建一个配置。`policy=skip`（默认）保留已有配置，`policy=overwrite` 覆盖它们。
- **从 Nacos 迁移**：Nacos 导出的 zip 可以直接导入。`DEFAULT_GROUP/` 前缀会被去掉，其余 `/` 替换为 `-`。
- **限制**：zip 最大 10 MiB，解压后最大 64 MiB。目录和隐藏文件会被跳过。

## 关闭

`spring.cloud.cosky.config.enabled=false` 关闭 config starter；`spring.cloud.cosky.enabled=false` 关闭整个 CoSky。

## 另见

- [配置属性](./properties.md)：全部属性。
- [使用 SDK](./sdk.md#配置)：不用 Spring 读取和监听配置。
- [架构](./architecture.md#一致性模型)：缓存如何保持一致。
