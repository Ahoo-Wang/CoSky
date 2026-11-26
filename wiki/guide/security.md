---
title: Security
description: Sign in with tokens or OIDC, say who may do what with roles, encrypt the connections, and read the audit log.
---

# Security

Every call goes through the server and is checked there, including what applications do. Nothing but the server touches Redis, so protect Redis and its credentials as you would the data itself.

## Who is calling

| Subject | Signs in with | For |
|---|---|---|
| `SERVICE_ACCOUNT` | A token, `cosky_<id>_<secret>` | Programs, and people without OIDC |
| `USER` and `GROUP` | An OIDC ID token | People |

A token goes in `Authorization: Bearer`, over gRPC and HTTP alike. The server keeps only its SHA-256, and shows the plaintext once, when creating it.

An account holds at most two tokens, which allows rotating without a gap: create the second, move the programs over, revoke the first. Revoking takes effect at once, including on open streams.

### The first token

On its first start, a server without any role binding turns `COSKY_BOOTSTRAP_TOKEN` into the `admin` ServiceAccount, with the `admin` role in every namespace. Use it to create real accounts, then revoke it.

```bash
export COSKY_BOOTSTRAP_TOKEN="cosky_$(openssl rand -hex 8)_$(openssl rand -hex 16)"
```

- **Strong enough.** The secret, after the second `_`, takes at least 22 letters and digits, about 128 bits; the command above gives 32 hex digits. With auth on, the server checks the token at every start, used or not, and refuses to start with a shorter one or one of another shape, without printing it.
- **Read only while no role binding exists:** on the first start, and again when Redis loses everything, as [Backup and Restore](./backup.md#when-redis-loses-everything) explains. Otherwise another value does nothing.
- **After revoking it,** set the variable or Secret to a new token nobody has used, kept as a break-glass, or remove it. Left as it is, a Redis that loses its data makes the revoked token the admin again. Removed, nobody can sign in after such a loss until you set one and restart.
- Without it, a server with no role binding starts and logs a warning, and nobody can sign in.

### A lost admin token

A new bootstrap token does not help once anyone is bound, since the server no longer reads it. So keep a second way in, set up beforehand: an OIDC group bound to `admin`, or a second token of an admin account, kept offline. With it, create a token with `POST /v1/serviceaccounts/admin/tokens` and revoke the lost one with `DELETE /v1/serviceaccounts/admin/tokens/<id>`.

With no admin left, the last resort is a server with authentication off, on which every caller is admin:

1. Start one more server on the same Redis with `--cosky.auth.enabled=false`, where nobody else can reach it: outside the Service or load balancer, its ports bound to `localhost`. Leave the other replicas as they are.
2. It logs an error saying that authentication is off. Create a token with `POST /v1/serviceaccounts/admin/tokens`, and revoke the lost one.
3. Stop it at once.

### OIDC

```yaml
cosky:
  auth:
    oidc:
      issuer: https://idp.example.com/realms/main
      client-id: cosky-dashboard
```

The server checks signature, issuer, audience and expiry against the provider's keys. `sub` becomes the `USER` name, and the `groups` claim (see `groups-claim`) the `GROUP` names. The token's audience must be `audience`, or the client when that is not set.

The Dashboard signs in through the authorization code flow with PKCE, so register `cosky-dashboard` as a public client, with the Dashboard's address as its redirect URI and as the address to return to after signing out. The provider must allow that origin to call its token endpoint, and browsers and the server must reach the issuer at the same address. `GET /v1/auth/config` gives the page these public values before anyone has signed in, and holds nothing else.

`scopes` defaults to `openid` and `profile`. Add a scope only if your provider has it, because it refuses a request for one it does not know. Some providers ask for one to send groups, and some for `offline_access` to give a refresh token.

- **Staying signed in.** The Dashboard renews the ID token before it expires, using the refresh token. A provider that gives none leaves people to sign in again when the token expires, which takes 5 minutes on a default Keycloak. The refresh token stays in the tab, next to the ID token, and ends with it. A live view ends when the ID token it was opened with expires, and the Dashboard opens it again with the renewed one: someone removed from a group at the provider stops seeing what the group may within one token's lifetime.
- **Signing out** also ends the session at the provider, so that the next sign-in asks for a password, if the provider has an `end_session_endpoint`.
- **When the provider is away,** servers still start, and applications carry on with their tokens. People already signed in carry on until their ID token expires, and new sign-ins fail: the server answers `UNAVAILABLE` (`IDENTITY_PROVIDER_UNAVAILABLE`) rather than calling the token invalid. Keep a ServiceAccount token to get in meanwhile.

### Keycloak

This was tried with Keycloak 26. Other providers follow the same standard, but none has been tried.

1. Create the client `cosky-dashboard`, with client authentication off and the standard flow on. Set its valid redirect URIs to `https://cosky.example.com/*` and its web origins to `https://cosky.example.com`.
2. In the client's dedicated scope, add a **Group Membership** mapper with the token claim name `groups`, **Full group path** off, and **Add to ID token** on. With the full path on, a group's name is `/devs`.
3. Bind a role to the group. A person's `sub` is an opaque id, so groups are easier. Someone with no role yet sees their `sub` and groups in the Dashboard, to pass on to you.

```bash
curl -X PUT localhost:8080/v1/rolebindings/devs-view \
  -H "Authorization: Bearer $COSKY_TOKEN" -H 'Content-Type: application/json' \
  -d '{"roleBinding":{"role":"viewer","subjects":[{"type":"TYPE_GROUP","name":"devs"}],"namespaces":["*"]}}'
```

## Who may do what

A **Role** holds rules, and a **RoleBinding** gives a role to subjects in some namespaces. Four roles are built in, and a Role cannot take their names:

| Role | Allows |
|---|---|
| `admin` | Everything |
| `viewer` | Reading and watching everything |
| `client` | Reading and watching Configs, and reading, watching and writing Instances |
| `service` | As `client`, but writing only the Instances of the service named like the subject |

### A token for an application

Every namespace comes with a ServiceAccount of its own name, bound to `client` there. The applications of a namespace share its token and need nothing else. Create the token once, in the Dashboard under **Security** with **Create token** on the account named like the namespace, or with an admin's token in `COSKY_TOKEN`:

```bash
curl -X POST localhost:8080/v1/serviceaccounts/prod/tokens \
  -H "Authorization: Bearer $COSKY_TOKEN" -H 'Content-Type: application/json' -d '{}'
```

It answers the token, once. Give it to the applications as `cosky.token` (`COSKY_TOKEN`). An application without a token, or with one its roles do not allow, stops at start and says which.

- **For this namespace only, and this one only.** Deleted and created again, a namespace is new, and so is its account, without tokens: the old token reaches nothing.
- **Yours stays yours.** A ServiceAccount or RoleBinding of that name you made yourself is left alone, and that namespace then has no account of its own.
- **Off:** `cosky.auth.namespace-accounts: false`. The accounts made already stay until you delete them.

To tell applications apart, to revoke one alone or to see in the audit log who did what, give an application a ServiceAccount named like its service, bound to `service`: it registers that service and no other, and reads like `client`.

```bash
curl -X PUT localhost:8080/v1/serviceaccounts/order \
  -H "Authorization: Bearer $COSKY_TOKEN" -H 'Content-Type: application/json' \
  -d '{"serviceAccount":{"description":"The order service"}}'
curl -X PUT localhost:8080/v1/rolebindings/order-service \
  -H "Authorization: Bearer $COSKY_TOKEN" -H 'Content-Type: application/json' \
  -d '{"roleBinding":{"role":"service","subjects":[{"type":"TYPE_SERVICE_ACCOUNT","name":"order"}],"namespaces":["prod"]}}'
curl -X POST localhost:8080/v1/serviceaccounts/order/tokens \
  -H "Authorization: Bearer $COSKY_TOKEN" -H 'Content-Type: application/json' -d '{}'
```

For less still, such as reading its own Configs only, write a role of your own.

Your own roles look like this:

```json
{
  "role": {
    "description": "Runs the order service",
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

- A call is allowed when any rule of any bound role matches it. A rule applies only where its own namespaces and the binding's overlap.
- `names` left empty matches everything. Otherwise it holds exact values, or prefixes ending in `*`, matched against the Config name, the Instance's **service**, or the object name.
- Global kinds (Namespace, ServiceAccount, Role, RoleBinding) live in the namespace `_system`.
- Nobody gives more than they hold. Writing a Role takes holding each of its rules in every namespace the rule names, `*` only by holding it in `*`. Writing a RoleBinding takes holding what its role grants in the binding's namespaces: for `service`, the Instances of each subject's own service, or of any service for a group, and for a role that does not exist yet, everything there. Creating a token for a ServiceAccount takes holding everything that account may do. Otherwise the server answers `PERMISSION_DENIED`, saying what you do not hold. An admin holds everything, so this never stops one.
- Writing Roles, RoleBindings and ServiceAccounts still lets someone take access away from others, and give anyone what they hold themselves: grant it to those who manage access.
- `GET` includes lists, and only what the rule allows shows up in them.
- A lease belongs to whoever took it, so one account cannot attach its instances to another's lease.
- A change to a role reaches open Watch streams: one that lost permission is canceled.

`GET /v1/me` shows who the server thinks you are, and your effective rules.

With `cosky.auth.enabled: false` everyone may do everything. It is for development only: on Redis, the server logs an error at every start, and reports `cosky_auth_enabled 0` (see [Deployment](./deployment.md#observability)).

## TLS

Without TLS, tokens cross the network in the clear, so turn it on in production. The server uses Spring's SSL bundles:

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

And clients:

```yaml
cosky:
  tls:
    enabled: true
    trusted-certificates: /etc/cosky/ca.pem   # for a private CA; otherwise the JVM's trust store decides
```

Mutual TLS is not supported yet: a client proves who it is with its token.

With TLS on, port 8080 speaks only HTTPS, and so does [Actuator's own port](./deployment.md#actuator-on-its-own-port), which takes the same settings, while the image's health check and the probes in `k8s/base` use HTTP. Keep Actuator's port plain:

```yaml
management:
  server:
    port: 8081
    ssl:
      enabled: false
```

`k8s/base` already probes port 8081: add `MANAGEMENT_SERVER_SSL_ENABLED=false` to its environment. The image's health check follows `MANAGEMENT_SERVER_PORT`, so set the port that way, or give the container a check of its own: `docker run --health-cmd 'curl -f http://localhost:8081/actuator/health/liveness || exit 1'`.

## Audit

Each write and each failed sign-in is one JSON line in the log, with the time, subject, verb, kind, namespace, name, revision, outcome and remote address. Config contents and tokens never appear. The server keeps no audit data of its own: collect the lines with your log system, which also decides how long they stay.

## What is not protected

- Config contents are not encrypted, in Redis or in client [snapshots](./sdk.md#snapshots).
- Anyone who can reach Redis can read and change everything, past all of the above. Give the server a Redis user of its own, over TLS where others share the network: see [Deployment](./deployment.md#credentials-and-tls).
- Instance metadata is visible to anyone allowed to read the instance.
- The REST API's document, `/v3/api-docs`, and Swagger UI, `/swagger-ui.html`, need no token; calls made from it do. `springdoc.api-docs.enabled=false` turns both off.
- `/actuator/health`, `/actuator/info` and `/actuator/prometheus` need no token, and the metrics name your namespaces. They are on port 8080 unless moved: give them [a port of their own](./deployment.md#actuator-on-its-own-port) that only the probes and Prometheus reach, as `k8s/base` does.
- gRPC health on port 9090 needs no token. gRPC reflection is off, and `spring.grpc.server.reflection.enabled` turns it on.
