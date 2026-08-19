---
title: Security & RBAC
---

# 安全与 RBAC

CoSky 实现了全面的三层安全模型 -- **认证**、**授权**和**审计** -- 基于 [CoSec](https://github.com/Ahoo-Wang/CoSec) 安全框架构建。用户凭据使用 SHA-256 哈希存储在 Redis 中。授权结合了策略引擎（CoSec 策略 JSON）和命名空间范围的 RBAC。操作会被审计（默认仅写操作，可配置）并持久化到 Redis List 中以供查询。

## 一览

| 层级 | 组件 | 职责 | 关键文件 | 源码 |
|-------|-----------|---------------|----------|--------|
| 认证 | `UserPasswordAuthentication` | 通过用户名/密码登录 | `UserPasswordAuthentication.kt` | [security/.../UserPasswordAuthentication.kt:11](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/UserPasswordAuthentication.kt#L11) |
| 认证 | `RefreshTokenAuthentication` | 刷新 JWT 令牌对 | `RefreshTokenAuthentication.kt` | [security/.../RefreshTokenAuthentication.kt:14](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/RefreshTokenAuthentication.kt#L14) |
| 授权 | `CoSkyAuthorization` | 双层授权（策略 + RBAC） | `CoSkyAuthorization.kt` | [security/.../CoSkyAuthorization.kt:19](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/CoSkyAuthorization.kt#L19) |
| RBAC | `RbacService` | Redis 中的角色/资源 CRUD | `RbacService.kt` | [security/.../RbacService.kt:30](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/RbacService.kt#L30) |
| 审计 | `AuditLogHandlerInterceptor` | 用于审计日志的 WebFilter | `AuditLogHandlerInterceptor.kt` | [security/.../AuditLogHandlerInterceptor.kt:31](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/audit/AuditLogHandlerInterceptor.kt#L31) |
| 用户管理 | `UserService` | 用户 CRUD、登录锁定、角色绑定 | `UserService.kt` | [security/.../UserService.kt:36](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserService.kt#L36) |

## 三层安全架构

```mermaid
flowchart LR
    subgraph "第 1 层：认证"
        UserPwd["UserPasswordAuthentication<br>SHA-256 凭据"]
        RefreshTok["RefreshTokenAuthentication<br>JWT 刷新"]
        AuthCtrl["AuthenticateController<br>/v1/authenticate"]
    end

    subgraph "第 2 层：授权"
        Policy["CoSkyPolicy<br>cosky-policy.json"]
        Authz["CoSkyAuthorization<br>策略 + RBAC"]
        NSApp["NamespaceRequestAttributesAppender<br>从路径提取命名空间"]
    end

    subgraph "第 3 层：审计"
        AuditFilter["AuditLogHandlerInterceptor<br>WebFilter"]
        AuditSvc["AuditLogService<br>持久化到 Redis List"]
    end

    AuthCtrl --> UserPwd
    AuthCtrl --> RefreshTok
    Authz --> Policy
    Authz --> NSApp
    AuditFilter --> AuditSvc
```

<!-- Sources: cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/UserPasswordAuthentication.kt:11, cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/RefreshTokenAuthentication.kt:14, cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/CoSkyAuthorization.kt:19, cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/audit/AuditLogHandlerInterceptor.kt:31 -->

## 认证

CoSky 支持两种认证方式：

1. **UserPasswordAuthentication** -- 使用 SHA-256 哈希凭据（存储在 Redis 中）验证用户名/密码。成功时，`UserService.login()` 方法返回包含用户角色绑定的 `SimplePrincipal`。
2. **RefreshTokenAuthentication** -- 接受访问/刷新令牌对，并通过 CoSec 的 `TokenVerifier` 验证刷新令牌。验证通过后会调用 `UserService.ensureUnlocked()`，因此已被锁定的账户无法再刷新令牌。成功时返回新的令牌对。

### AuthenticateController 端点

| 方法 | 路径 | 描述 | 源码 |
|--------|------|-------------|--------|
| POST | `/v1/authenticate/{username}/login` | 使用密码登录 | [AuthenticateController.kt:37](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/AuthenticateController.kt#L37) |
| POST | `/v1/authenticate/{username}/refresh` | 刷新令牌对 | [AuthenticateController.kt:47](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/AuthenticateController.kt#L47) |

### 登录锁定机制

`UserService.login()` 实现了渐进式账户锁定以防止暴力破解攻击。每个锁定状态变更（`login-attempt`、`login-success`、`lock`、`remove`）都以单次原子性 `user_state.lua` 脚本调用执行，因此单个计数更新不会被并发登录破坏。注意，一次完整登录包含两次独立的脚本调用（先 attempt，密码校验通过后再 success），而 `unlock()` 会直接删除锁定键，因此端到端的登录序列并非一个原子整体。

- **最大失败次数**：10 次（`MAX_LOGIN_ERROR_TIMES`）
- **锁定追踪**：Redis 键（`cosky-{system}:login_lock:{username}`）在每次登录尝试时递增，在成功登录时删除。当计数未超过阈值时，脚本会在每次尝试时将键的 TTL 刷新为 15 分钟（`LOGIN_LOCK_EXPIRE`），因此冻结实际会在最后一次计数尝试的 15 分钟后解除。
- **冻结提示**：当计数超过 10 次后，登录将被拒绝直到键过期。错误消息中的冻结时长按渐进退避公式计算：`lockoutDuration = baseLockout * max(tryCount / maxErrorTimes, 1)`，上限为 3 天（`MAX_LOGIN_LOCK_EXPIRE`）。
- **手动锁定**：管理员可通过 `PUT /v1/users/{username}/lock` 立即锁定用户。脚本会向锁定键写入 `manual` 标记（无 TTL），账户将保持锁定直到被显式解锁。root 用户（`cosky`）不可被锁定。
- **刷新令牌被拒绝**：被锁定的用户（手动标记或超过失败次数阈值）在刷新令牌时同样会被拒绝 -- 参见 [RefreshTokenAuthentication](#认证)。
- **解锁**：管理员可通过 `DELETE /v1/users/{username}/unlock` 解锁用户，该操作会删除锁定键。
- **可见性**：`GET /v1/users` 会为每个用户返回 `locked` 属性，当账户被手动锁定或超过失败次数阈值时为 `true`。

源码: [UserService.kt:138-180](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserService.kt#L138), [user_state.lua](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/resources/user_state.lua)

### 登录流程

```mermaid
sequenceDiagram
    autonumber
    participant Client as 客户端
    participant AuthenticateController
    participant TokenCompositeAuth
    participant UserPasswordAuth
    participant UserService
    participant Redis

    Client->>AuthenticateController: POST /v1/authenticate/{username}/login
    AuthenticateController->>TokenCompositeAuth: authenticateAsToken(UserPasswordCredentials)
    TokenCompositeAuth->>UserPasswordAuth: authenticate(credentials)
    UserPasswordAuth->>UserService: login(username, pwd)
    UserService->>Redis: EVAL user_state.lua (login-attempt)<br>INCR cosky-{system}:login_lock:{username}
    Redis-->>UserService: tryCount（手动锁定时为 -1）
    alt 手动锁定（tryCount = -1）
        UserService-->>UserPasswordAuth: SecurityException（被管理员锁定）
    else tryCount > 10
        UserService-->>UserPasswordAuth: SecurityException（账户已冻结）
    else 在限制内
        UserService->>Redis: HGET cosky-{system}:user_idx（哈希密码）
        Redis-->>UserService: storedHash
        alt SHA-256(pwd) != storedHash
            UserService-->>UserPasswordAuth: SecurityException（密码错误）
        else 密码匹配
            UserService->>Redis: EVAL user_state.lua (login-success)<br>DEL 锁定键
            UserService->>Redis: SMEMBERS cosky-{system}:user_role_bind:{username}
            Redis-->>UserService: roleSet
            UserService-->>UserPasswordAuth: SimplePrincipal(id, roles)
        end
    end
    TokenCompositeAuth->>TokenCompositeAuth: 生成 JWT 访问 + 刷新令牌
    TokenCompositeAuth-->>AuthenticateController: CompositeToken
    AuthenticateController-->>Client: 200 CompositeToken
```

<!-- Sources: cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/AuthenticateController.kt:37, cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/UserPasswordAuthentication.kt:11, cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserService.kt:139, cosky-rest-api/src/main/resources/user_state.lua:31 -->

## 授权

CoSky 使用 `CoSkyAuthorization` 实现的**双层授权模型**：

1. **第 1 层 -- 基于策略**：CoSec 策略引擎根据 `cosky-policy.json` 评估请求。如果策略明确允许该请求，则立即通过。如果明确拒绝，则被拒绝。
2. **第 2 层 -- RBAC**：如果策略结果为隐式（既不允许也不拒绝），系统落入命名空间范围的 RBAC。它通过 `NamespaceRequestAttributesAppender` 从请求路径中提取命名空间，然后检查用户的角色是否授予了在该命名空间上所需的操作权限。

超级用户（`cosky`）绕过所有授权检查。

源码: [CoSkyAuthorization.kt:24-60](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/CoSkyAuthorization.kt#L24)

### 授权决策流程

```mermaid
flowchart TD
    Request["传入请求"] --> IsRoot{"是否为超级用户<br>(cosky)?"}
    IsRoot -- 是 --> Allow["允许"]
    IsRoot -- 否 --> Policy{"CoSec 策略<br>验证"}
    Policy -- "明确允许" --> Allow
    Policy -- "明确拒绝" --> ExplicitDeny["明确拒绝"]
    Policy -- "隐式（无匹配）" --> ExtractNS["从请求路径<br>提取命名空间"]
    ExtractNS --> HasNS{"找到命名空间?"}
    HasNS -- 否 --> ImplicitDeny["隐式拒绝"]
    HasNS -- 是 --> MapAction["将 HTTP 方法映射为操作<br>GET -> READ<br>PUT/POST/DELETE -> WRITE"]
    MapAction --> CheckRoles["检查用户角色<br>与 ResourceAction"]
    CheckRoles --> Match{"角色授予<br>权限?"}
    Match -- 是 --> Allow
    Match -- 否 --> ImplicitDeny
```

<!-- Sources: cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/CoSkyAuthorization.kt:24, cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/NamespaceRequestAttributesAppender.kt:11 -->

### CoSkyPolicy 和 InitialPolicyLoader

`CoSkyPolicy` 从 CoSky 自身的配置服务加载安全策略。策略存储在 `cosky-{system}` 命名空间中的配置项中。它订阅配置变更事件，并在更新时自动刷新策略缓存。

如果配置服务中不存在策略，`InitialPolicyLoader` 会从 classpath 加载内置的 `cosky-policy.json` 作为回退。

源码: [CoSkyPolicy.kt:17](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/CoSkyPolicy.kt#L17), [InitialPolicyLoader.kt:7](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/InitialPolicyLoader.kt#L7)

## RBAC 模型

CoSky 实现了命名空间范围的基于角色的访问控制模型：

- **角色** -- 拥有名称、描述以及命名空间到 `ResourceAction` 绑定的映射。
- **ResourceAction** -- 将 `namespace` 与 `Action` 枚举值配对。
- **Action** -- 可以是 `READ`（`r`）、`WRITE`（`w`）或 `READ_WRITE`（`rw`）。HTTP 方法映射为：`GET/OPTIONS/TRACE/HEAD` 映射为 `READ`；`POST/PUT/DELETE/PATCH` 映射为 `WRITE`。

内置的 `admin` 角色没有资源-操作绑定，作为系统保留角色拥有最高权限级别（由策略授予完全访问权限）。

```mermaid
classDiagram
    class Role {
        +String roleName
        +String desc
        +Map~String, ResourceAction~ resourceActionBind
        +check(ResourceAction): Boolean
    }
    class ResourceAction {
        +String namespace
        +Action action
        +check(ResourceAction): Boolean
    }
    class Action {
        <<enumeration>>
        READ = r
        WRITE = w
        READ_WRITE = rw
        +check(Action): Boolean
        +asAction(String): Action
        +httpMethodAsAction(String): Action
    }
    class RbacService {
        +saveRole(roleName, SaveRoleRequest): Mono~Void~
        +removeRole(roleName): Mono~Boolean~
        +allRole: Mono~Set~RoleDto~~
        +getRole(roleName): Mono~Role~
        +getResourceBind(roleName): Flux~ResourceAction~
        +getRoleNamespaces(roles): Flux~String~
    }
    class RoleController {
        +allRole(): Mono~Set~RoleDto~~
        +getResourceBind(roleName): Mono~List~
        +saveRole(roleName, SaveRoleRequest): Mono~Void~
        +removeRole(roleName): Mono~Boolean~
    }

    Role --> ResourceAction : "1 对 n"
    ResourceAction --> Action
    RoleController --> RbacService
    RbacService --> Role : 加载,保存
```

<!-- Sources: cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/Role.kt:20, cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/ResourceAction.kt:22, cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/Action.kt:22, cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/RbacService.kt:30 -->

### 超级用户和管理员角色

- **超级用户**：`cosky` 用户（root）绕过所有授权。`SecurityCommand` 在每次应用启动时都会调用 `UserService.initRoot()`；只要 root 用户不存在，就会为其生成随机 10 字符密码（打印到标准输出）。将 `cosky.security.enforce-init-super-user` 设为 `true` 会先删除已有的 root 用户，从而在每次重启时强制重置密码。
- **管理员角色**：`admin` 角色是系统保留角色，由策略引擎的 `admin` 语句授予完全访问权限。它会自动包含在角色列表中。

源码: [UserService.kt:38-52](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserService.kt#L38), [Role.kt:31-34](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/Role.kt#L31), [SecurityCommand.kt:25](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/SecurityCommand.kt#L25)

## 审计日志

### AuditLogHandlerInterceptor

`AuditLogHandlerInterceptor` 是一个响应式 `WebFilter`，拦截所有 HTTP 请求。在响应写入后，它创建包含以下内容的 `AuditLog` 条目：

- **operator** -- 用户名（来自安全上下文，或从登录路径提取）
- **ip** -- 远程地址
- **resource** -- 请求 URI
- **action** -- HTTP 方法名
- **status** -- HTTP 响应状态码
- **msg** -- 错误消息（如有）
- **opTime** -- 毫秒级时间戳

该过滤器可通过 `SecurityProperties.auditLog.action` 配置。默认仅审计 `WRITE` 操作。设置为 `READ_WRITE`（`rw`）可审计所有操作，设置为 `READ`（`r`）可仅审计读操作。

源码: [AuditLogHandlerInterceptor.kt:31-76](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/audit/AuditLogHandlerInterceptor.kt#L31)

### AuditLogService

`AuditLogService` 将审计日志条目以 JSON 字符串的形式持久化到 Redis List（`cosky-{system}:audit:log`）中。新条目推入头部（`leftPush`）。查询支持通过 `range` 进行偏移/限制分页，并可通过 `GET /v1/audit-log/export` 导出为 CSV。

源码: [AuditLogService.kt:28-99](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/audit/AuditLogService.kt#L28)

## 用户管理

### UserService

`UserService` 完全在 Redis 中管理用户：

- **用户索引**：Redis Hash（`cosky-{system}:user_idx`），将用户名映射到 SHA-256 密码哈希。
- **角色绑定**：Redis Set（`cosky-{system}:user_role_bind:{username}`），存储分配给每个用户的角色名称。
- **密码哈希**：使用 Guava 的 `Hashing.sha256()` 进行 UTF-8 编码。
- **登录锁定**：参见上方[登录锁定机制](#登录锁定机制)。每个锁定状态变更都由 `user_state.lua` 脚本原子执行。
- **用户列表**：`query()` 返回每个用户的角色绑定及 `locked` 属性（手动锁定或超过失败次数阈值时为 `true`）。
- **删除保护**：root 用户（`cosky`）既不可被删除，也不可被锁定。
- **Root 初始化**：`initRoot(enforce)` 在 root 用户不存在时创建 `cosky` 超级用户并生成随机密码；`enforce = true` 会先删除已有 root，强制重新初始化。

源码: [UserService.kt:36-288](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserService.kt#L36)

### SecurityCommand

`SecurityCommand` 是一个在应用启动时运行的 `CommandLineRunner`。它无条件调用 `UserService.initRoot()`，并透传 `cosky.security.enforce-init-super-user` 标志（默认 `false`）。只要 root 用户不存在就会被创建；该标志仅控制是否先删除已有 root 并重新初始化。

源码: [SecurityCommand.kt:25-34](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/SecurityCommand.kt#L25)

### UserController 端点

| 方法 | 路径 | 描述 | 源码 |
|--------|------|-------------|--------|
| GET | `/v1/users` | 列出所有用户及其角色绑定与锁定状态 | [UserController.kt:44](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserController.kt#L44) |
| POST | `/v1/users/{username}` | 创建新用户 | [UserController.kt:54](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserController.kt#L54) |
| DELETE | `/v1/users/{username}` | 移除用户 | [UserController.kt:64](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserController.kt#L64) |
| PATCH | `/v1/users/{username}/password` | 修改密码 | [UserController.kt:49](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserController.kt#L49) |
| PATCH | `/v1/users/{username}/role` | 绑定角色到用户 | [UserController.kt:59](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserController.kt#L59) |
| PUT | `/v1/users/{username}/lock` | 手动锁定用户（root 不可被锁定） | [UserController.kt:69](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserController.kt#L69) |
| DELETE | `/v1/users/{username}/unlock` | 解锁被锁定的用户 | [UserController.kt:74](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserController.kt#L74) |

## 默认安全策略

内置的 `cosky-policy.json` 定义了基准安全策略。其语句按顺序评估：

```json
{
  "statements": [
    { "name": "options",       "action": { "all": { "method": "OPTIONS" } } },
    { "name": "swaggerUI",     "action": { "path": { "method": "GET", "pattern": ["/swagger-ui/**", ...] } } },
    { "name": "dashboard",     "action": { "path": { "method": "GET", "pattern": ["/", "/index.html", ...] } } },
    { "name": "actuatorHealth","action": ["/actuator/health", "/actuator/health/*"] },
    { "name": "authenticate",  "action": ["/v1/authenticate/{username}/login", "/v1/authenticate/{username}/refresh"] },
    { "name": "namespace",     "action": { "path": { "method": "GET", "pattern": "/v1/namespaces/**" } },
                              "condition": { "authenticated": {} } },
    { "name": "admin",         "action": "*", "condition": { "inRole": { "value": "admin" } } },
    { "name": "root",          "action": "*", "condition": { "eq": { "part": "context.principal.id", "value": "cosky" } } }
  ]
}
```

关键策略规则：

- **未认证访问**：OPTIONS 请求、Swagger UI、静态 Dashboard 资源、Actuator 健康检查和认证端点无需登录即可访问。
- **命名空间读取**：任何已认证用户都可以读取命名空间数据（GET `/v1/namespaces/**`）。
- **管理员角色**：`admin` 角色成员拥有对所有 API 的不受限制访问（`action: "*"`）。
- **Root 用户**：`cosky` 用户无论角色绑定如何，都拥有不受限制的访问权限。

源码: [cosky-rest-api/src/main/resources/cosky-policy.json](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/resources/cosky-policy.json)

## 相关页面

- [REST API Server](/guide/rest-api) -- API 端点和服务器架构
- [Dashboard](/guide/dashboard) -- CoSky 管理 UI

## 参考

- [SecurityProperties.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/SecurityProperties.kt)
- [AuthenticateController.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/AuthenticateController.kt)
- [UserPasswordAuthentication.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/UserPasswordAuthentication.kt)
- [RefreshTokenAuthentication.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authentication/RefreshTokenAuthentication.kt)
- [CoSkyAuthorization.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/CoSkyAuthorization.kt)
- [CoSkyPolicy.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/CoSkyPolicy.kt)
- [InitialPolicyLoader.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/InitialPolicyLoader.kt)
- [NamespaceRequestAttributesAppender.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/authorization/NamespaceRequestAttributesAppender.kt)
- [RbacService.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/RbacService.kt)
- [Role.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/Role.kt)
- [Action.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/Action.kt)
- [ResourceAction.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/rbac/ResourceAction.kt)
- [UserService.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/user/UserService.kt)
- [SecurityCommand.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/SecurityCommand.kt)
- [AuditLogService.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/audit/AuditLogService.kt)
- [AuditLogHandlerInterceptor.kt](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/kotlin/me/ahoo/cosky/rest/security/audit/AuditLogHandlerInterceptor.kt)
- [cosky-policy.json](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/resources/cosky-policy.json)
