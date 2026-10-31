---
title: 安全
description: CoSky REST API 服务器的用户、角色、安全策略、令牌与审计日志。
---

# 安全

本页面向 REST API 服务器的管理员。安全机制保护的是服务器的 API 和控制台；使用 starter 的应用直接访问 Redis，因此请同时保护好 Redis 本身（见[部署](./deployment.md#准备-redis)）。

## 模型

| 概念 | 含义 |
|---|---|
| **超级用户** `cosky` | 首次启动时创建；拥有全部权限，不能被锁定或删除。 |
| **用户** | 用密码登录（以 BCrypt 哈希存储，最长 72 字节），持有若干角色。 |
| **角色** | 授予对命名空间的 `r`（读）、`w`（写）或 `rw`（读写）权限。内置的 `admin` 角色授予全部权限，包括管理用户和角色。 |
| **策略** | 先于角色评估的规则：公开端点、任何已登录用户能做的事，以及 `admin` / 超级用户的授权。 |

请求先看策略是否放行；不放行时，再看用户的某个角色是否对请求路径中的命名空间授予了相应操作（`GET`、`HEAD`、`OPTIONS`、`TRACE` 需要 `r` 或 `rw`；`POST`、`PUT`、`PATCH`、`DELETE` 需要 `w` 或 `rw`）。

没有角色的已登录用户只能列出其角色授予的命名空间（`GET /v1/namespaces`，所以是空列表），以及读取服务器自身的命名空间名称。

## 常见操作

以下操作都可以在控制台（**User**、**Role**）中完成，也可以通过 [REST API](./rest-api.md) 完成。

**让一个团队只读访问某个命名空间**

1. 创建角色，例如 `payments-read`，授予 `{prod}` → `r`。
2. 创建用户并绑定该角色。

绑定多个角色的用户获得这些角色授权的并集。被删除的角色不再授予任何权限，但在重新绑定之前仍挂在用户身上。

**设为管理员**：绑定 `admin` 角色。

**锁定用户**：锁定后，该用户的登录和令牌刷新都会被拒绝，直到解锁。

**找回超级用户**：设置 `COSKY_SUPER_INIT=true`（`cosky.security.enforce-init-super-user`）启动服务器一次，从日志读取新密码，然后删除该变量；变量保留期间，每次启动都会重置密码。

## 令牌

登录返回 JWT 访问令牌（有效 15 分钟）和刷新令牌（有效 3 小时），用 `cosky.security.key` 签名。客户端发送 `Authorization: Bearer <访问令牌>`，并在刷新令牌过期前用它换取新的令牌对。

| 设置 | 默认值 |
|---|---|
| `cosky.security.key`（`COSKY_SECURITY_KEY`） | 无：没有它服务器拒绝启动 |
| `cosec.jwt.token-validity.access` | `15m` |
| `cosec.jwt.token-validity.refresh` | `3H` |

更换密钥会让所有已签发的令牌失效，所有人都会被登出。每个服务器实例必须使用相同的密钥。

## 登录失败

连续 10 次登录失败后用户被锁定，最后一次尝试 15 分钟后自动解除。用户列表会显示谁被锁定；管理员可以提前解锁。

## 自定义策略

内置策略允许未登录访问：`OPTIONS` 请求、控制台静态文件、Swagger UI、`/actuator/health`，以及登录和令牌刷新。允许任何已登录用户调用 `GET /v1/namespaces` 和 `GET /v1/namespaces/current`，并把全部权限授予 `admin` 角色和 `cosky`。

要修改它，把一个 [CoSec 策略](https://github.com/Ahoo-Wang/CoSec)保存为 `cosky-{system}` 命名空间中的配置 `cosky-policy.json`，可以从[内置策略](https://github.com/Ahoo-Wang/CoSky/blob/v6/cosky-rest-api/src/main/resources/cosky-policy.json)开始改。保存后每个服务器实例立即生效；无法解析的策略会被拒绝并在日志中报错，原有策略继续生效。

## 审计日志

服务器记录它处理的每个请求：操作人、IP、路径、方法、状态码、错误信息和时间。默认只记录写操作。

| 设置 | 默认值 | 取值 |
|---|---|---|
| `cosky.security.audit-log.action` | `write` | `read`、`write`、`rw` |
| `cosky.security.audit-log.max-size` | `100000` | 保留的最新条目数 |

控制台的 **Audit Log** 可以按文本、时间范围和结果筛选，并导出 CSV。

## 关闭安全

`cosky.security.enabled=false` 会完全关闭认证与授权。只有在服务器仅能被可信客户端访问时才这样做。
