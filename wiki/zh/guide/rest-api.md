---
title: REST API
description: REST API 的接口及其共同约定。
---

# REST API

管理 API，位于 8080 端口。它的 OpenAPI 3.1 文档与 gRPC 使用相同的 schema，因为两者来自同一份 proto。server 在 `/v3/api-docs` 提供这份文档（YAML 格式在 `/v3/api-docs.yaml`），并在 `/swagger-ui.html` 用 Swagger UI 展示，可以在 **Authorize** 中填入 token。`./gradlew :api:generateOpenApi` 把它写到 `api/build/openapi/cosky.yaml`，其中还有 Watch 事件流的条目 schema，server 上的副本没有这一项。

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

**写入**替换整个对象，只读字段会被忽略。请求体可以省略 `meta.namespace` 和 `meta.name`，但不能写成与路径不同的位置。对象没有的成员返回 `400`，而不是被丢弃：否则拼错的 `lables` 会清空标签。

| 请求头 | 含义 |
|---|---|
| `If-Match: "42"` | 仅当它仍在 revision 42 |
| `If-None-Match: *` | 仅当它不存在。只接受 `*`，也不能与 `If-Match` 同时使用 |

条件不满足的写入返回 `409` `REVISION_CONFLICT`，而不是 RFC 9110 的 `412`，与 Kubernetes 相同：这是 revision 的冲突，原因和详情与 gRPC 一致。

`GET` 和 `PUT` 把 revision 作为 `ETag` 返回。`PUT` 的内容与现有相同时返回 `{"changed": false}`，不创建 revision；这时 `revision` 和 `ETag` 是对象已有的 revision，可用于下一次 `If-Match`。`DELETE` 的对象不存在时返回 `deleted` 为 false，带 namespace 的 revision。两者的 `revision` 都可用作 `minRevision`。

**列表**分页返回：把 `nextPageToken` 作为 `pageToken` 传回，直到它为空。`pageSize` 默认 500，最大 1000，对象较大时一页可能更少。响应中的 `revision` 和 `epoch` 可用于从列表结束的位置开始 Watch。

**读取自己的写入**：副本可能稍有落后。`?minRevision=42` 会等到它达到这个 revision，3 秒后返回 `504`。

`400` 中的**违规项**与 gRPC 一样，按请求中的 proto 字段路径指明字段：参数和请求头用它设置的字段（`pageSize` 是 `page_size`，`labelSelector` 是 `label_selector`，`minRevision` 是 `min_revision`，`If-Match` 和 `If-None-Match` 是 `expected_revision`，`toRevision` 是 `to_revision`，`expiresAt` 是 `expires_at`，Watch 的 `kind` 是 `create.kind`），请求体用它承载的字段：`PUT` 是 `resource`，对象没有的成员则给出它的路径，例如 `resource.meta.lables`。

**错误**是 problem details（[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)），以 `application/problem+json` 返回。除了 `type`、`title`、`status`、`detail` 和 `instance`，`code` 是 gRPC 状态，`reason` 是不随版本变化的原因。有些原因还带一个成员，说明更多信息。`type` 指向下表中该原因所在的行。

```json
{
  "type": "https://cosky.ahoo.me/guide/rest-api#revision-conflict",
  "title": "Revision conflict",
  "status": 409,
  "detail": "KIND_CONFIG prod/app.yaml: expected revision 42, current revision 43.",
  "instance": "/v1/namespaces/prod/configs/app.yaml",
  "code": "ABORTED",
  "reason": "ERROR_REASON_REVISION_CONFLICT",
  "revisionConflict": {
    "key": {"namespace": "prod", "kind": "KIND_CONFIG", "name": "app.yaml"},
    "expectedRevision": "42",
    "currentRevision": "43"
  }
}
```

| 原因 | HTTP | 更多信息 |
|---|---|---|
| <a id="resource-not-found"></a>`RESOURCE_NOT_FOUND` | 404 | `resourceNotFound`：对象的键 |
| <a id="namespace-not-found"></a>`NAMESPACE_NOT_FOUND` | 404 | `namespace` |
| <a id="invalid-argument"></a>`INVALID_ARGUMENT` | 400 | `badRequest`：违规项 |
| <a id="unauthenticated"></a>`UNAUTHENTICATED` | 401 |  |
| <a id="permission-denied"></a>`PERMISSION_DENIED` | 403 |  |
| <a id="revision-conflict"></a>`REVISION_CONFLICT` | 409 | `revisionConflict`：对象的键、期望的和当前的 revision |
| <a id="namespace-not-empty"></a>`NAMESPACE_NOT_EMPTY` | 409 | `namespace` |
| <a id="lease-not-found"></a>`LEASE_NOT_FOUND` | 412 | `leaseNotFound` |
| <a id="watch-quota-exceeded"></a>`WATCH_QUOTA_EXCEEDED` | 429 | `watchQuotaExceeded`：上限 |
| <a id="store-unavailable"></a>`STORE_UNAVAILABLE` | 503 | 连不上存储 |
| <a id="identity-provider-unavailable"></a>`IDENTITY_PROVIDER_UNAVAILABLE` | 503 | 连不上 OIDC 提供方或取不到它的密钥；token 本身可能没有问题 |
| <a id="replica-not-ready"></a>`REPLICA_NOT_READY` | 503 | `namespace`：这台 server 还在加载它，或者落后；其他 server 可能可以应答 |
| <a id="revision-regression"></a>`REVISION_REGRESSION` | 503 | `namespace` |
| <a id="min-revision-timeout"></a>`MIN_REVISION_TIMEOUT` | 504 | `namespace` |

不是 CoSky 自身的错误（例如路径不存在）同样是 problem details，类型为 `about:blank`，没有 `code` 和 `reason`。

## 接口

### Config

| 方法 | 路径 | |
|---|---|---|
| GET | `/v1/namespaces/{ns}/configs` | 列表。`?labelSelector=a%3D1&labelSelector=b%3D2`：每个参数一个 `key=value`，全部满足才匹配。不按逗号拆分，值里可以有逗号 |
| GET、PUT、DELETE | `/v1/namespaces/{ns}/configs/{name}` | |
| GET | `/v1/namespaces/{ns}/configs/{name}/history` | 新的在前。`?limit=` 是所有页加起来最多返回的版本数，`?pageToken=` 取下一页 |
| POST | `/v1/namespaces/{ns}/configs/{name}:rollback` | `{"toRevision": "41"}`。把那份内容写成新 revision，也能恢复已删除的 Config。历史中没有的 revision 返回 `400`，字段为 `to_revision`。 |
| POST | `/v1/namespaces/{ns}/configs:import` | 请求体是 zip 文件；每个条目的路径是 Config 名称 |
| GET | `/v1/namespaces/{ns}/configs:export` | zip 文件 |

### Instance

| 方法 | 路径 | |
|---|---|---|
| GET | `/v1/namespaces/{ns}/instances` | 列表。`?service=order&labelSelector=zone%3Deast` |
| GET、PUT、DELETE | `/v1/namespaces/{ns}/instances/{name}` | 没有 `meta.lease` 时会一直保留，直到被删除。服务不可修改 |

租约只通过 gRPC 提供：应用使用[客户端](./sdk.md)。

### Watch

`GET /v1/namespaces/{ns}/watch?kind=KIND_CONFIG&name=&service=&fromRevision=&fromEpoch=`

`kind` 使用枚举名。Namespace、ServiceAccount、Role 和 RoleBinding 在 `_system` 中 watch：`/v1/namespaces/_system/watch?kind=KIND_ROLE`。

Server-sent events，每个 `data:` 行是一条 proto3 JSON 消息：

| 消息 | 含义 |
|---|---|
| `snapshot` | `fromRevision` 为 0 时的当前状态，带有用于续传的 `epoch`。 |
| `events` | 一个 revision 的变化 |
| `progress` | 书签，空闲时每 10 秒一次 |
| `positionGone` | 你的位置已失效，流随之结束：不带位置重新开始。其中有现在的 `earliestRevision` 和 `epoch`，以及原因 `reason`：`GONE_REASON_COMPACTED`（早于窗口）、`GONE_REASON_SLOW`（掉出窗口）、`GONE_REASON_EPOCH`（换了时间线）或 `GONE_REASON_REGRESSION`（来自丢失的时间线） |
| `canceled` | 流结束，并给出原因 |

较大的快照和 revision 会分成多条消息，用 `more` 标记。`EventSource` 无法发送 token：请像 Dashboard 那样使用 `fetch` 和流读取器。

### 视图

由 server 内存计算，只读。服务和拓扑像列表一样按 `pageSize` 和 `pageToken` 分页；不带 `pageSize` 时在大小上限允许的范围内用尽量少的页返回全部，通常只有一页。

| 路径 | |
|---|---|
| `/v1/namespaces/{ns}/views/services` | 服务及其实例数 |
| `/v1/namespaces/{ns}/views/topology` | 谁依赖谁 |
| `/v1/namespaces/{ns}/views/stats` | 服务、实例和 Config 的数量 |

### 全局对象

| 方法 | 路径 | |
|---|---|---|
| GET | `/v1/namespaces` | |
| GET、PUT、DELETE | `/v1/namespaces/{name}` | 最简单的请求体是 `{"namespace":{}}`。只有空的 namespace 才能删除。删除进行中时 `namespace.terminating` 为 `true`，`namespace.terminatingEpoch` 指明删除针对的那一代，写入返回 `404`。删除途中发现 namespace 已被重建时以 `409` 停止，不做任何改动。 |
| GET | `/v1/serviceaccounts` | |
| GET、PUT、DELETE | `/v1/serviceaccounts/{name}` | 最简单的请求体是 `{"serviceAccount":{}}`。请求体中的 `tokens` 被忽略。 |
| POST | `/v1/serviceaccounts/{name}/tokens` | 只返回一次明文，并带上账号新的 `revision`。`{"expiresAt": "..."}` 可选。 |
| DELETE | `/v1/serviceaccounts/{name}/tokens/{tokenId}` | 返回 `{"revision", "deleted"}`；账号没有这个 token 时 `deleted` 为 false，不算错误。两个 token 接口都接受带账号 revision 的 `If-Match`。 |
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
