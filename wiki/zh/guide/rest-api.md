---
title: REST API
description: CoSky REST API 服务器的接口、认证方式与错误格式。
---

# REST API

REST API 服务器暴露了控制台能做的一切。机器可读的接口定义由服务器自身提供：OpenAPI 位于 `/v3/api-docs`，Swagger UI 位于 `/swagger-ui.html`。

## 约定

- **认证**：用 `POST /v1/authenticate/{username}/login` 登录，之后每个请求都带上 `Authorization: Bearer <accessToken>`。见[安全](./security.md#令牌)。
- **授权**：`/v1/namespaces/{namespace}/…` 下的请求需要授予该命名空间权限的角色；用户、角色、命名空间和审计接口需要 `admin` 角色。
- 路径中的**命名空间**使用完整名称，例如 `{dev}`（URL 编码为 `%7Bdev%7D`）。实例 ID 含有 `@` 和 `#`，同样需要 URL 编码。
- **错误**返回 `{"code": "0001", "msg": "<原因>"}`：请求无效为 400，没有有效令牌为 401，无权限为 403，未知路由为 404。

## 认证

| 方法 | 路径 | 请求体 | 返回 |
|---|---|---|---|
| POST | `/v1/authenticate/{username}/login` | `{"password": "…"}` | `{"accessToken", "refreshToken"}` |
| POST | `/v1/authenticate/{username}/refresh` | `{"accessToken", "refreshToken"}` | 新的令牌对 |

## 命名空间

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v1/namespaces` | 你能访问的命名空间（管理员为全部） |
| GET | `/v1/namespaces/current` | 服务器自身的命名空间 |
| PUT | `/v1/namespaces/{namespace}` | 添加命名空间 |
| DELETE | `/v1/namespaces/{namespace}` | 从列表中移除命名空间 |

## 配置

前缀：`/v1/namespaces/{namespace}/configs`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | *（前缀）* | 命名空间中的配置 ID |
| GET | `/{configId}` | 当前配置：`configId`、`data`、`hash`、`version`、`createTime` |
| PUT | `/{configId}` | 保存（请求体为原始内容，`text/plain`）；产生新版本 |
| DELETE | `/{configId}` | 删除 |
| GET | `/{configId}/versions` | 历史中的版本 |
| GET | `/{configId}/versions/{version}` | 某个版本的内容 |
| PUT | `/{configId}/to/{targetVersion}` | 回滚到某个版本 |
| GET | `/export` | 全部配置打包为 zip |
| POST | *（前缀）* `?policy=skip\|overwrite` | 导入 zip（multipart 字段 `importZip`）；返回 `{"total", "succeeded"}` |

## 服务与实例

前缀：`/v1/namespaces/{namespace}/services`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | *（前缀）* | 服务 ID |
| PUT | `/{serviceId}` | 添加一个没有实例的服务 |
| DELETE | `/{serviceId}` | 删除服务及其实例索引 |
| GET | `/{serviceId}/instances` | 存活实例 |
| PUT | `/{serviceId}/instances` | 注册实例（请求体见下） |
| DELETE | `/{serviceId}/instances/{instanceId}` | 注销实例 |
| PUT | `/{serviceId}/instances/{instanceId}/metadata` | 替换实例元数据（请求体为字符串映射） |
| GET | `/{serviceId}/lb` | 按加权随机选出一个实例 |
| GET | `/stats` | 每个服务的实例数 |

注册实例：

```json
{ "schema": "http", "host": "10.0.0.5", "port": 8080, "weight": 1, "isEphemeral": false, "metadata": { "zone": "a" } }
```

通过 API 注册的临时实例若无人续约，会在 TTL 后过期；无法续约的端点请注册为固定实例（`"isEphemeral": false`）。

## 统计

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v1/namespaces/{namespace}/stat` | 命名空间、配置、服务（总数与有实例的数量）和实例的数量 |
| GET | `/v1/namespaces/{namespace}/stat/topology` | 消费者及其依赖的服务 |

服务统计每 10 秒刷新一次，由通过 Redis 选出的一个服务器实例执行。

## 用户、角色与审计

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v1/users` | 用户及其角色与锁定状态 |
| POST | `/v1/users/{username}` | 创建（`{"password": "…"}`） |
| DELETE | `/v1/users/{username}` | 删除 |
| PATCH | `/v1/users/{username}/password` | 修改密码（`{"oldPassword", "newPassword"}`） |
| PATCH | `/v1/users/{username}/role` | 替换角色（请求体：`["role", …]`） |
| PUT | `/v1/users/{username}/lock` | 锁定 |
| DELETE | `/v1/users/{username}/unlock` | 解锁 |
| GET | `/v1/roles` | 角色列表 |
| PUT | `/v1/roles/{roleName}` | 创建或替换（`{"desc", "resourceActionBind": [{"namespace", "action": "r\|w\|rw"}]}`） |
| GET | `/v1/roles/{roleName}/bind` | 角色授权的命名空间 |
| DELETE | `/v1/roles/{roleName}` | 删除 |
| GET | `/v1/audit-log?offset&limit&query&from&to&successful` | 审计条目，最新在前；`from` / `to` 为毫秒时间戳 |
| GET | `/v1/audit-log/export?query&from&to&successful` | 匹配条目导出为 CSV |
