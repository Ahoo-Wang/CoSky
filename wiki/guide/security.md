---
title: Security
description: Users, roles, the security policy, tokens and the audit log of the CoSky REST API server.
---

# Security

This page is for administrators of the REST API server. Security covers the server's API and dashboard; applications using the starters access Redis directly, so protect Redis itself (see [Deployment](./deployment.md#preparing-redis)).

## Model

| Concept | Meaning |
|---|---|
| **Super user** `cosky` | Created on first start; can do everything and can be neither locked nor removed. |
| **User** | Signs in with a password (stored as a BCrypt hash, at most 72 bytes) and holds roles. |
| **Role** | Grants `r` (read), `w` (write) or `rw` (both) on namespaces. The built-in `admin` role grants everything, including user and role management. |
| **Policy** | Rules evaluated before roles: public endpoints, what any signed-in user may do, and the `admin` / super user grants. |

A request is allowed when the policy allows it; otherwise when one of the user's roles grants the action on the namespace in the request path (`GET`, `HEAD`, `OPTIONS`, `TRACE` need `r` or `rw`; `POST`, `PUT`, `PATCH`, `DELETE` need `w` or `rw`).

Signed-in users without roles can only list the namespaces their roles grant (`GET /v1/namespaces`, so an empty list) and read the server's own namespace name.

## Common tasks

All of these are available in the dashboard (**User**, **Role**) and through the [REST API](./rest-api.md#users-roles-and-audit).

**Give a team read access to a namespace**

1. Create a role, e.g. `payments-read`, with `{prod}` → `r`.
2. Create the users and bind them to the role.

A user bound to several roles gets the union of their grants. A removed role grants nothing but stays bound to its users until they are rebound.

**Make someone an administrator**: bind the `admin` role.

**Lock a user out**: lock the user; sign-in and token refresh are rejected until the user is unlocked.

**Recover the super user**: start the server once with `COSKY_SUPER_INIT=true` (`cosky.security.enforce-init-super-user`), read the new password from the log, then remove the variable; while it is set, the password is reset at every start.

## Tokens

Signing in returns a JWT access token (valid 15 minutes) and a refresh token (valid 3 hours), signed with `cosky.security.key`. Clients send `Authorization: Bearer <access token>` and exchange the refresh token for a new pair before it expires.

| Setting | Default |
|---|---|
| `cosky.security.key` (`COSKY_SECURITY_KEY`) | none: the server refuses to start without it |
| `cosec.jwt.token-validity.access` | `15m` |
| `cosec.jwt.token-validity.refresh` | `3H` |

Changing the key invalidates every issued token, which signs everybody out. Use the same key on every server instance.

## Failed sign-ins

After 10 failed attempts a user is locked out, and the lock lifts 15 minutes after the last attempt. The user list shows who is locked; an administrator can unlock earlier.

## Customizing the policy

The bundled policy allows without sign-in: `OPTIONS` requests, the dashboard's static files, Swagger UI, `/actuator/health`, and sign-in / token refresh. It allows any signed-in user to call `GET /v1/namespaces` and `GET /v1/namespaces/current`, and grants everything to the `admin` role and to `cosky`.

To change it, save a [CoSec policy](https://github.com/Ahoo-Wang/CoSec) as the config `cosky-policy.json` in the `cosky-{system}` namespace, starting from the [bundled one](https://github.com/Ahoo-Wang/CoSky/blob/main/cosky-rest-api/src/main/resources/cosky-policy.json). Every server instance applies it as soon as it is saved; a policy that does not parse is rejected with an error in the log and the previous one stays in force.

## Audit log

The server records each request it handles: operator, IP, path, method, status, error message and time. By default only write operations are recorded.

| Setting | Default | Values |
|---|---|---|
| `cosky.security.audit-log.action` | `write` | `read`, `write`, `rw` |
| `cosky.security.audit-log.max-size` | `100000` | newest entries kept |

The dashboard's **Audit Log** filters by text, time range and outcome, and exports CSV.

## Turning security off

`cosky.security.enabled=false` disables authentication and authorization altogether. Only do this when the server is reachable exclusively by trusted clients.
