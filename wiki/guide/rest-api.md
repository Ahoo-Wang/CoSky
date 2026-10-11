---
title: REST API
description: Endpoints of the CoSky REST API server, authentication and error format.
---

# REST API

The REST API server exposes everything the dashboard does. The machine-readable contract is served by the server itself: OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

## Conventions

- **Authentication**: sign in with `POST /v1/authenticate/{username}/login` and send `Authorization: Bearer <accessToken>` on every other request. See [Security](./security.md#tokens).
- **Authorization**: requests on `/v1/namespaces/{namespace}/…` need a role granting the namespace; user, role, namespace and audit endpoints need the `admin` role.
- **Namespaces** in paths are the full names, e.g. `{dev}` (URL-encoded: `%7Bdev%7D`). Instance ids contain `@` and `#`: URL-encode them too.
- **Errors** return `{"code": "0001", "msg": "<reason>"}` with status 400 for invalid requests, 401 without a valid token, 403 when not permitted, 404 for unknown routes.

## Authentication

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/v1/authenticate/{username}/login` | `{"password": "…"}` | `{"accessToken", "refreshToken"}` |
| POST | `/v1/authenticate/{username}/refresh` | `{"accessToken", "refreshToken"}` | a new token pair |

## Namespaces

| Method | Path | Description |
|---|---|---|
| GET | `/v1/namespaces` | Namespaces you can access (all of them for administrators) |
| GET | `/v1/namespaces/current` | The server's own namespace |
| PUT | `/v1/namespaces/{namespace}` | Add a namespace |
| DELETE | `/v1/namespaces/{namespace}` | Remove a namespace from the list |

## Configs

Prefix: `/v1/namespaces/{namespace}/configs`.

| Method | Path | Description |
|---|---|---|
| GET | *(prefix)* | Config ids of the namespace |
| GET | `/{configId}` | Current config: `configId`, `data`, `hash`, `version`, `createTime` |
| PUT | `/{configId}` | Save (body: the raw content, `text/plain`); creates a version |
| DELETE | `/{configId}` | Remove |
| GET | `/{configId}/versions` | Versions in the history |
| GET | `/{configId}/versions/{version}` | Content of a version |
| PUT | `/{configId}/to/{targetVersion}` | Roll back to a version |
| GET | `/export` | All configs as a zip |
| POST | *(prefix)* `?policy=skip\|overwrite` | Import a zip (multipart field `importZip`); returns `{"total", "succeeded"}` |

## Services and instances

Prefix: `/v1/namespaces/{namespace}/services`.

| Method | Path | Description |
|---|---|---|
| GET | *(prefix)* | Service ids |
| PUT | `/{serviceId}` | Add a service without instances |
| DELETE | `/{serviceId}` | Remove a service and its instance index |
| GET | `/{serviceId}/instances` | Live instances |
| PUT | `/{serviceId}/instances` | Register an instance (body below) |
| DELETE | `/{serviceId}/instances/{instanceId}` | Deregister an instance |
| PUT | `/{serviceId}/instances/{instanceId}/metadata` | Replace an instance's metadata (body: a string map) |
| GET | `/{serviceId}/lb` | One instance chosen by weighted random |
| GET | `/stats` | Instance count per service |

Registering an instance:

```json
{ "schema": "http", "host": "10.0.0.5", "port": 8080, "weight": 1, "isEphemeral": false, "metadata": { "zone": "a" } }
```

An ephemeral instance registered through the API expires after the TTL unless something renews it; register endpoints that cannot renew as fixed (`"isEphemeral": false`).

## Statistics

| Method | Path | Description |
|---|---|---|
| GET | `/v1/namespaces/{namespace}/stat` | Counts of namespaces, configs, services (total and with instances) and instances |
| GET | `/v1/namespaces/{namespace}/stat/topology` | Consumers and the services they depend on |

Service statistics are refreshed every 10 seconds by one server instance, elected through Redis.

## Users, roles and audit

| Method | Path | Description |
|---|---|---|
| GET | `/v1/users` | Users with their roles and lock state |
| POST | `/v1/users/{username}` | Create (`{"password": "…"}`) |
| DELETE | `/v1/users/{username}` | Remove |
| PATCH | `/v1/users/{username}/password` | Change password (`{"oldPassword", "newPassword"}`) |
| PATCH | `/v1/users/{username}/role` | Replace roles (body: `["role", …]`) |
| PUT | `/v1/users/{username}/lock` | Lock |
| DELETE | `/v1/users/{username}/unlock` | Unlock |
| GET | `/v1/roles` | Roles |
| PUT | `/v1/roles/{roleName}` | Create or replace (`{"desc", "resourceActionBind": [{"namespace", "action": "r\|w\|rw"}]}`) |
| GET | `/v1/roles/{roleName}/bind` | Namespaces a role grants |
| DELETE | `/v1/roles/{roleName}` | Remove |
| GET | `/v1/audit-log?offset&limit&query&from&to&successful` | Audit entries, newest first; `from` / `to` in epoch milliseconds |
| GET | `/v1/audit-log/export?query&from&to&successful` | Matching entries as CSV |
