---
title: 安全
description: 用 token 或 OIDC 登录，用角色说明谁能做什么，加密连接，并查看审计日志。
---

# 安全

每次调用都经过 server 并在那里检查，包括应用的调用。除了 server，没有任何东西接触 Redis，所以请像保护数据本身一样保护 Redis 和它的凭据。

## 谁在调用

| 主体 | 登录方式 | 用于 |
|---|---|---|
| `SERVICE_ACCOUNT` | token，`cosky_<id>_<secret>` | 程序，以及没有 OIDC 时的人员 |
| `USER` 和 `GROUP` | OIDC ID Token | 人员 |

token 放在 `Authorization: Bearer` 中，gRPC 和 HTTP 相同。server 只保存它的 SHA-256，明文只在创建时显示一次。

一个账号最多持有两个 token，这样轮换时不会中断：创建第二个，把程序迁过去，再吊销第一个。吊销立即生效，已打开的流也不例外。

### 第一个 token

首次启动时，如果没有任何角色绑定，server 会把 `COSKY_BOOTSTRAP_TOKEN` 变成 `admin` ServiceAccount，并在所有 namespace 上授予 `admin` 角色。用它创建真正的账号，然后吊销它。

```bash
export COSKY_BOOTSTRAP_TOKEN="cosky_$(openssl rand -hex 8)_$(openssl rand -hex 16)"
```

- 只有在不存在任何角色绑定时，server 才会读取它。之后换一个值不起作用，删除它也没有影响。
- 没有设置时，没有角色绑定的 server 会正常启动并记录一条警告，但谁也无法登录。
- 要替换丢失的 token，在同一个 Redis 上运行一个 `--cosky.auth.enabled=false` 的 server，并确保别人访问不到它。用 `POST /v1/serviceaccounts/admin/tokens` 创建 token，吊销丢失的那个，然后停掉这个 server。

### OIDC

```yaml
cosky:
  auth:
    oidc:
      issuer: https://idp.example.com/realms/main
      client-id: cosky-dashboard
```

server 用提供方的密钥检查签名、签发者、受众和有效期。`sub` 成为 `USER` 名称，`groups` 声明（见 `groups-claim`）成为 `GROUP` 名称。token 的受众必须是 `audience`，没有设置时是 client。

Dashboard 通过带 PKCE 的授权码流程登录，所以要把 `cosky-dashboard` 注册为公开客户端，并把 Dashboard 的地址设为重定向 URI 和退出登录后的返回地址。提供方必须允许这个来源调用它的 token 端点，浏览器和 server 也必须能通过同一个地址访问签发者。`GET /v1/auth/config` 在登录前把这些公开值提供给页面，不包含其他内容。

`scopes` 默认是 `openid` 和 `profile`。只有提供方确实有某个 scope 时才添加，因为它会拒绝不认识的 scope。有些提供方要求某个 scope 才返回分组，有些要求 `offline_access` 才发放 refresh token。

- **保持登录。** Dashboard 在 ID token 到期前用 refresh token 续期。如果提供方不发放 refresh token，token 到期后就要重新登录，默认配置的 Keycloak 上是 5 分钟。refresh token 和 ID token 一样保存在标签页中，随标签页结束。
- **退出登录**时，如果提供方提供 `end_session_endpoint`，也会结束提供方的会话，下次登录会要求输入密码。
- **提供方不可用时**，server 仍能启动，应用继续使用自己的 token 工作。已登录的用户在 ID token 到期前不受影响，新的登录会失败：server 返回 `UNAVAILABLE`，而不是认为 token 无效。请保留一个 ServiceAccount token，以便在此期间登录。

### Keycloak

以下配置在 Keycloak 26 上实测过。其他提供方遵循同样的标准，但都没有实测过。

1. 创建客户端 `cosky-dashboard`，关闭 client authentication，开启 standard flow。把 valid redirect URIs 设为 `https://cosky.example.com/*`，web origins 设为 `https://cosky.example.com`。
2. 在客户端专属的 scope 中添加 **Group Membership** mapper：token claim name 设为 `groups`，关闭 **Full group path**，开启 **Add to ID token**。如果开启 full path，分组名称会是 `/devs`。
3. 把角色绑定到分组。用户的 `sub` 是一串不透明的 id，所以绑定分组更方便。还没有角色的用户会在 Dashboard 中看到自己的 `sub` 和所属的分组，可以转告管理员。

```bash
curl -X PUT localhost:8080/v1/rolebindings/devs-view \
  -H "Authorization: Bearer $COSKY_TOKEN" -H 'Content-Type: application/json' \
  -d '{"roleBinding":{"role":"viewer","subjects":[{"type":"TYPE_GROUP","name":"devs"}],"namespaces":["*"]}}'
```

## 谁能做什么

**Role** 持有规则，**RoleBinding** 在某些 namespace 中把角色授予主体。有三个内置角色，Role 不能使用它们的名称：

| 角色 | 允许 |
|---|---|
| `admin` | 一切 |
| `viewer` | 读取和 watch 一切 |
| `client` | 读取和 watch Config，读取、watch 和写入 Instance |

自定义角色如下：

```json
{
  "role": {
    "description": "运行订单服务",
    "rules": [
      {"kinds": ["KIND_INSTANCE"], "namespaces": ["*"], "verbs": ["VERB_PUT", "VERB_DELETE"], "names": ["order"]},
      {"kinds": ["KIND_INSTANCE"], "namespaces": ["*"], "verbs": ["VERB_GET", "VERB_WATCH"]},
      {"kinds": ["KIND_CONFIG"], "namespaces": ["*"], "verbs": ["VERB_GET", "VERB_WATCH"], "names": ["order-service.yaml", "shared/*"]}
    ]
  }
}
```

```json
{
  "roleBinding": {
    "role": "order-service",
    "subjects": [{"type": "TYPE_SERVICE_ACCOUNT", "name": "order"}],
    "namespaces": ["prod"]
  }
}
```

- 任一绑定角色的任一规则匹配，调用就被允许。规则只在它自己的 namespace 与绑定的 namespace 重叠的范围内生效。
- `names` 留空匹配所有对象；否则是精确值，或以 `*` 结尾的前缀，匹配 Config 名称、Instance 的**服务名**或对象名。
- 全局类型（Namespace、ServiceAccount、Role、RoleBinding）位于 namespace `_system`。
- `GET` 包括列表，列表中只出现规则允许的对象。
- 租约属于创建它的主体，所以一个账号不能把实例挂到别人的租约上。
- 角色的变化会影响已打开的 Watch 流：失去权限的流会被取消。

`GET /v1/me` 显示 server 认为你是谁，以及你的有效规则。

`cosky.auth.enabled: false` 时任何人都可以做任何事，只用于开发。

## TLS

没有 TLS 时，token 明文经过网络，所以生产环境请开启。server 使用 Spring 的 SSL bundle：

```yaml
spring:
  ssl:
    bundle:
      jks:
        cosky:
          keystore:
            location: file:/etc/cosky/server.p12
            password: ${KEYSTORE_PASSWORD}
            type: PKCS12
  grpc:
    server:
      ssl:
        bundle: cosky
server:
  ssl:
    bundle: cosky
```

客户端：

```yaml
cosky:
  tls:
    enabled: true
    trusted-certificates: /etc/cosky/ca.pem   # 用于私有 CA；否则由 JVM 的信任库决定
```

目前还不支持双向 TLS：客户端用 token 证明身份。

开启 TLS 后，8080 端口只接受 HTTPS，而镜像的健康检查和 `k8s/base` 中的探针使用 HTTP。给它们一个单独的明文端口：

```yaml
management:
  server:
    port: 8081
    ssl:
      enabled: false
```

然后把探针指向这个端口，容器的健康检查也一样：`docker run --health-cmd 'curl -f http://localhost:8081/actuator/health/liveness || exit 1'`。

## 审计

每次写入和每次登录失败都是日志中的一行 JSON，包含时间、主体、动作、类型、namespace、名称、revision、结果和远端地址。Config 内容和 token 不会出现。server 自己不保存审计数据：请用日志系统收集这些行，保留多久也由它决定。

## 没有保护的内容

- Config 内容没有加密，无论是在 Redis 还是在客户端[快照](./sdk.md#快照)里。
- 能访问 Redis 的人可以读取和修改一切，绕过以上所有措施。
- 实例元数据对任何有权读取该实例的人可见。
- 8080 端口上的 `/actuator/health`、`/actuator/info` 和 `/actuator/prometheus` 不需要 token，指标中包含 namespace 名称。不要把它们暴露到公网，或者用 `management.server.port` 换一个端口。
- 9090 端口上的 gRPC 健康检查不需要 token。gRPC 反射默认关闭，可用 `spring.grpc.server.reflection.enabled` 打开。
