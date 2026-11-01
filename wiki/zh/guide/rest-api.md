---
title: REST API
description: REST API 的接口及其共同约定。
---

# REST API

管理 API，位于 8080 端口。`./gradlew :api:generateOpenApi` 把完整的 OpenAPI 3.1 文档写到 `api/build/openapi/cosky.yaml`，schema 与 gRPC 相同，因为两者来自同一份 proto。

## 约定

**认证**：`Authorization: Bearer <token>`，`GET /v1/auth/config` 和 [Actuator](./security.md#没有保护的内容) 除外。

**资源**由共享的元数据和一个按类型区分的 spec 组成，使用 proto3 JSON。空字段会被省略，revision 这样的 64 位数字是字符串。

```json
{
  "meta": {
    "namespace": "prod",
    "name": "app.yaml",
    "revision": "42",
    "createRevision": "7",
    "updatedAt": "2026-08-18T01:00:00Z",
    "updatedBy": "serviceaccount:admin"
  },
  "config": {"format": "CONFIG_FORMAT_YAML", "content": "a: 1", "sha256": "..."}
}
```

**名称**：`{name}` 是一个路径段，所以 Config 名称中的 `/` 要写成 `%2F`。

**写入**替换整个对象，只读字段会被忽略。

| 请求头 | 含义 |
|---|---|
| `If-Match: "42"` | 仅当它仍在 revision 42 |
| `If-None-Match: *` | 仅当它不存在 |

`GET` 和 `PUT` 把 revision 作为 `ETag` 返回。`PUT` 的内容与现有相同时返回 `{"changed": false}`，不创建 revision。

**列表**分页返回：把 `nextPageToken` 作为 `pageToken` 传回，直到它为空。`pageSize` 默认 500，最大 1000，对象较大时一页可能更少。响应中的 `revision` 和 `epoch` 可用于从列表结束的位置开始 Watch。

**读取自己的写入**：副本可能稍有落后。`?minRevision=42` 会等到它达到这个 revision，3 秒后返回 `504`。

**错误**带有不随版本变化的原因：

```json
{
  "status": "ABORTED",
  "message": "The revision does not match.",
  "detail": {"reason": "ERROR_REASON_REVISION_CONFLICT"}
}
```

| 原因 | HTTP |
|---|---|
| `RESOURCE_NOT_FOUND`、`NAMESPACE_NOT_FOUND` | 404 |
| `INVALID_ARGUMENT` | 400，违规项在 `detail` 中 |
| `UNAUTHENTICATED` | 401 |
| `PERMISSION_DENIED` | 403 |
| `REVISION_CONFLICT`、`NAMESPACE_NOT_EMPTY` | 409 |
| `LEASE_NOT_FOUND` | 412 |
| `WATCH_QUOTA_EXCEEDED` | 429 |
| `STORE_UNAVAILABLE`、`REVISION_REGRESSION` | 503 |
| `MIN_REVISION_TIMEOUT` | 504 |

## 接口

### Config

| 方法 | 路径 | |
|---|---|---|
| GET | `/v1/namespaces/{ns}/configs` | 列表。`?labelSelector=a=1,b=2` |
| GET、PUT、DELETE | `/v1/namespaces/{ns}/configs/{name}` | |
| GET | `/v1/namespaces/{ns}/configs/{name}/history` | 新的在前。`?limit=&pageToken=` |
| POST | `/v1/namespaces/{ns}/configs/{name}:rollback` | `{"toRevision": "41"}`。把那份内容写成新 revision，也能恢复已删除的 Config。 |
| POST | `/v1/namespaces/{ns}/configs:import` | 请求体是 zip 文件；每个条目的路径是 Config 名称 |
| GET | `/v1/namespaces/{ns}/configs:export` | zip 文件 |

### Instance

| 方法 | 路径 | |
|---|---|---|
| GET | `/v1/namespaces/{ns}/instances` | 列表。`?service=order&labelSelector=` |
| GET、PUT、DELETE | `/v1/namespaces/{ns}/instances/{name}` | 没有 `meta.lease` 时会一直保留，直到被删除。服务不可修改 |

租约只通过 gRPC 提供：应用使用[客户端](./sdk.md)。

### Watch

`GET /v1/namespaces/{ns}/watch?kind=KIND_CONFIG&name=&service=&fromRevision=&fromEpoch=`

Server-sent events，每个 `data:` 行是一条 proto3 JSON 消息：

| 消息 | 含义 |
|---|---|
| `snapshot` | `fromRevision` 为 0 时的当前状态，带有用于续传的 `epoch`。 |
| `events` | 一个 revision 的变化 |
| `progress` | 书签，空闲时每 10 秒一次 |
| `gone` | 你的位置已失效：不带位置重新开始 |
| `canceled` | 流结束，并给出原因 |

较大的快照和 revision 会分成多条消息，用 `more` 标记。`EventSource` 无法发送 token：请像 Dashboard 那样使用 `fetch` 和流读取器。

### 视图

由 server 内存计算，只读。

| 路径 | |
|---|---|
| `/v1/namespaces/{ns}/views/services` | 服务及其实例数 |
| `/v1/namespaces/{ns}/views/topology` | 谁依赖谁 |
| `/v1/namespaces/{ns}/views/stats` | 服务、实例和 Config 的数量 |

### 全局对象

| 方法 | 路径 | |
|---|---|---|
| GET | `/v1/namespaces` | |
| GET、PUT、DELETE | `/v1/namespaces/{name}` | 最简单的请求体是 `{"namespace":{}}`。只有空的 namespace 才能删除。 |
| GET | `/v1/serviceaccounts` | |
| GET、PUT、DELETE | `/v1/serviceaccounts/{name}` | 最简单的请求体是 `{"serviceAccount":{}}`。请求体中的 `tokens` 被忽略。 |
| POST | `/v1/serviceaccounts/{name}/tokens` | 只返回一次明文。`{"expiresAt": "..."}` 可选。 |
| DELETE | `/v1/serviceaccounts/{name}/tokens/{tokenId}` | |
| GET | `/v1/roles` | |
| GET、PUT、DELETE | `/v1/roles/{name}` | |
| GET | `/v1/rolebindings` | |
| GET、PUT、DELETE | `/v1/rolebindings/{name}` | |

### 身份

| 路径 | |
|---|---|
| `/v1/me` | 你是谁，以及你的有效规则 |
| `/v1/auth/config` | 如何登录。不需要 token，只含公开值。 |

### 运维

| 路径 | |
|---|---|
| `/actuator/health/liveness`、`/actuator/health/readiness` | 探针 |
| `/actuator/prometheus` | [指标](./deployment.md#可观测性) |
