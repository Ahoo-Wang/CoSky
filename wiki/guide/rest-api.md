---
title: REST API
description: The endpoints of the REST API and the conventions they share.
---

# REST API

The management API, on port 8080. Its OpenAPI 3.1 document has the same schemas as gRPC, since both come from one set of protos. A server serves it at `/v3/api-docs` (`/v3/api-docs.yaml` as YAML), and shows it in Swagger UI at `/swagger-ui.html`, where **Authorize** takes a token. `./gradlew :api:generateOpenApi` writes it to `api/build/openapi/cosky.yaml`, with the item schema of the Watch's event stream as well, which the server's copy leaves out.

## Conventions

**Authentication**: `Authorization: Bearer <token>`, on everything except `GET /v1/auth/config` and [Actuator](./security.md#what-is-not-protected).

**Resources** have shared metadata and one kind-specific spec, in proto3 JSON. Empty fields are left out, and 64-bit numbers such as revisions are strings.

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

**Names**: `{name}` is one path segment, so a `/` in a Config name is sent as `%2F`.

**Writes** replace the whole object, and read-only fields are ignored.

| Header | Means |
|---|---|
| `If-Match: "42"` | Only if it is still at revision 42 |
| `If-None-Match: *` | Only if it does not exist |

`GET` and `PUT` return the revision as `ETag`. A `PUT` of what is already there answers `{"changed": false}` and creates no revision.

**Lists** come in pages: pass `nextPageToken` as `pageToken` until it is empty. `pageSize` is 500 by default and 1000 at most, and a page can hold fewer when the objects are large. The response's `revision` and `epoch` let you start a Watch from where the list ended.

**Reading your own writes**: a replica may be slightly behind. `?minRevision=42` waits until it has reached that revision, and answers `504` after 3 seconds.

**Errors** are problem details ([RFC 9457](https://www.rfc-editor.org/rfc/rfc9457)), as `application/problem+json`. Beside `type`, `title`, `status`, `detail` and `instance`, `code` is the gRPC status, and `reason` the reason, which does not change between versions. Some reasons add a member that says more. `type` leads to the reason's row below.

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

| Reason | HTTP | More |
|---|---|---|
| <a id="resource-not-found"></a>`RESOURCE_NOT_FOUND` | 404 | `resourceNotFound`: the key |
| <a id="namespace-not-found"></a>`NAMESPACE_NOT_FOUND` | 404 | `namespace` |
| <a id="invalid-argument"></a>`INVALID_ARGUMENT` | 400 | `badRequest`: the violations |
| <a id="unauthenticated"></a>`UNAUTHENTICATED` | 401 |  |
| <a id="permission-denied"></a>`PERMISSION_DENIED` | 403 |  |
| <a id="revision-conflict"></a>`REVISION_CONFLICT` | 409 | `revisionConflict`: the key, the expected and the current revision |
| <a id="namespace-not-empty"></a>`NAMESPACE_NOT_EMPTY` | 409 | `namespace` |
| <a id="lease-not-found"></a>`LEASE_NOT_FOUND` | 412 | `leaseNotFound` |
| <a id="watch-quota-exceeded"></a>`WATCH_QUOTA_EXCEEDED` | 429 | `watchQuotaExceeded`: the limit |
| <a id="store-unavailable"></a>`STORE_UNAVAILABLE` | 503 |  |
| <a id="revision-regression"></a>`REVISION_REGRESSION` | 503 | `namespace` |
| <a id="min-revision-timeout"></a>`MIN_REVISION_TIMEOUT` | 504 | `namespace` |

Failures that are not CoSky's own, such as a path that does not exist, are problem details too, of the type `about:blank`, without `code` or `reason`.

## Endpoints

### Configs

| Method | Path | |
|---|---|---|
| GET | `/v1/namespaces/{ns}/configs` | List. `?labelSelector=a=1,b=2` |
| GET, PUT, DELETE | `/v1/namespaces/{ns}/configs/{name}` | |
| GET | `/v1/namespaces/{ns}/configs/{name}/history` | Newest first. `?limit=&pageToken=` |
| POST | `/v1/namespaces/{ns}/configs/{name}:rollback` | `{"toRevision": "41"}`. Writes that content as a new revision, and brings back a deleted Config. |
| POST | `/v1/namespaces/{ns}/configs:import` | A zip file as the body; each entry path is a Config name |
| GET | `/v1/namespaces/{ns}/configs:export` | A zip file |

### Instances

| Method | Path | |
|---|---|---|
| GET | `/v1/namespaces/{ns}/instances` | List. `?service=order&labelSelector=` |
| GET, PUT, DELETE | `/v1/namespaces/{ns}/instances/{name}` | Without `meta.lease`, it stays until deleted. The service cannot change |

Leases are gRPC only: applications use the [client](./sdk.md).

### Watch

`GET /v1/namespaces/{ns}/watch?kind=KIND_CONFIG&name=&service=&fromRevision=&fromEpoch=`

Server-sent events, one proto3 JSON message per `data:` line:

| Message | Means |
|---|---|
| `snapshot` | The current state, when `fromRevision` is 0. Holds the `epoch` to resume with. |
| `events` | One revision's changes |
| `progress` | A bookmark, every 10 seconds while idle |
| `gone` | Your position is no longer valid: start over without one |
| `canceled` | The stream ends, for the reason given |

Large snapshots and revisions span several messages, marked by `more`. An `EventSource` cannot send a token: use `fetch` with a stream reader, as the Dashboard does.

### Views

Computed from the server's memory, read-only.

| Path | |
|---|---|
| `/v1/namespaces/{ns}/views/services` | Services and their instance counts |
| `/v1/namespaces/{ns}/views/topology` | Who consumes whom |
| `/v1/namespaces/{ns}/views/stats` | Counts of services, instances and Configs |

### Global objects

| Method | Path | |
|---|---|---|
| GET | `/v1/namespaces` | |
| GET, PUT, DELETE | `/v1/namespaces/{name}` | The smallest body is `{"namespace":{}}`. Only an empty namespace can be deleted. While a deletion is under way, `namespace.terminating` is `true`, `namespace.terminatingEpoch` names the namespace it is for, and a write gets `404`. A deletion that finds the namespace created again meanwhile stops with `409` and changes nothing. |
| GET | `/v1/serviceaccounts` | |
| GET, PUT, DELETE | `/v1/serviceaccounts/{name}` | The smallest body is `{"serviceAccount":{}}`. `tokens` in the body is ignored. |
| POST | `/v1/serviceaccounts/{name}/tokens` | Returns the plaintext once. `{"expiresAt": "..."}` is optional. |
| DELETE | `/v1/serviceaccounts/{name}/tokens/{tokenId}` | |
| GET | `/v1/roles` | |
| GET, PUT, DELETE | `/v1/roles/{name}` | |
| GET | `/v1/rolebindings` | |
| GET, PUT, DELETE | `/v1/rolebindings/{name}` | |

### Identity

| Path | |
|---|---|
| `/v1/me` | Who you are, and your effective rules |
| `/v1/auth/config` | How people sign in. Needs no token and holds public values only. |

### Operations

| Path | |
|---|---|
| `/actuator/health/liveness`, `/actuator/health/readiness` | Probes |
| `/actuator/prometheus` | [Metrics](./deployment.md#observability) |
