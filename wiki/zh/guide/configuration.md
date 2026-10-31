---
title: 配置管理
description: 由 CoSky 提供 Spring 属性，实时刷新，并保留 revision。
---

# 配置管理

**Config** 是 namespace 中一份有名称的文档，包含名称、格式和内容，内容最大 1 MiB。

| 字段 | 规则 |
|---|---|
| 名称 | 字母、数字、`.`、`_`、`-` 和 `/`，最长 253 个字符。`shared/datasource.yaml` 是一个名称。 |
| 格式 | `YAML`、`JSON`、`PROPERTIES` 或 `TEXT`，随 Config 声明。只有导入 zip 文件时才按扩展名推断。 |
| 内容 | UTF-8。server 保存它的 SHA-256，你可以用来比较，但不能设置。 |

## 加载到 Spring

```yaml
spring:
  config:
    import: "optional:cosky:order-service.yaml,cosky:shared/datasource.yaml"
cosky:
  endpoints: cosky.internal:9090
  token: ${COSKY_TOKEN}
  namespace: prod
```

- `cosky:` 不带 `optional:` 时，Config 加载失败会让应用启动失败，包括连不上任何 server 且没有快照的情况。
- YAML 和 JSON 按 YAML 解析，properties 按 properties 解析。TEXT 类型的 Config 成为一个以 Config 名命名的属性。
- `cosky.config.load-timeout`（5 秒）限制一次导入等待 server 的时间。

## 实时刷新

classpath 中有 `spring-cloud-context` 时，被导入的 Config 发生变化会发布 Spring Cloud 的 `RefreshEvent`，`@RefreshScope` 中的 bean 和 `@ConfigurationProperties` bean 都能看到新值。启动时还不存在的 Config 也一样。用 `cosky.config.refresh.enabled: false` 可以关闭。

应用以加载时看到的 revision 为基准比较，所以加载之后、开始监听之前的变化也不会漏掉。

## 写入

使用 [Dashboard](./dashboard.md) 或 [REST API](./rest-api.md)。一次写入替换整个 Config：

```bash
curl -X PUT localhost:8080/v1/namespaces/prod/configs/shared%2Fdatasource.yaml \
  -H "Authorization: Bearer $COSKY_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'If-Match: "42"' \
  -d '{"config":{"format":"CONFIG_FORMAT_YAML","content":"url: jdbc:mysql://db/orders"}}'
```

| 请求头 | 含义 |
|---|---|
| `If-Match: "42"` | 仅当它仍在 revision 42。否则返回 `409`，不做任何改动。 |
| `If-None-Match: *` | 仅当它不存在。 |
| 都不带 | 直接写入。 |

Dashboard 总是发送前两者之一，所以不会覆盖别人的修改。写入相同的内容不会有任何变化：没有新 revision，也没有事件。

名称中的 `/` 在路径里要编码成 `%2F`，因为名称是一个路径段。

## 历史与回滚

每个 Config 保留最近 20 个 revision，新的在前：

```bash
curl localhost:8080/v1/namespaces/prod/configs/app.yaml/history
```

回滚是把较早 revision 的内容写成一个新 revision，所以历史只增不减：

```bash
curl -X POST localhost:8080/v1/namespaces/prod/configs/app.yaml:rollback \
  -H 'Content-Type: application/json' -d '{"toRevision":"41"}'
```

删除 Config 后，它的历史会保留到重新创建或 namespace 被删除，回滚可以把它恢复回来。

## 导入与导出

zip 文件可以一次移动多个 Config。每个条目的路径就是 Config 名称，扩展名决定格式。

```bash
curl -o configs.zip localhost:8080/v1/namespaces/prod/configs:export
```

```bash
curl -X POST localhost:8080/v1/namespaces/staging/configs:import --data-binary @configs.zip
```

导入会覆盖同名 Config，一次最多 10,000 个条目。

## 保管机密

Config 内容没有加密，无论是在 server 上，还是在客户端落盘的[快照](./sdk.md#快照)里。用 `cosky.snapshot.include-config: false` 可以让快照不包含 Config。真正的机密请放在专门的系统中。
