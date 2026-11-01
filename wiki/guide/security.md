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

- The server reads it only while no role binding exists. After that another value does nothing, so removing it changes nothing either.
- Without it, a server with no role binding starts and logs a warning, and nobody can sign in.
- To replace a lost token, run one server on the same Redis with `--cosky.auth.enabled=false`, where nobody else can reach it. Create a token with `POST /v1/serviceaccounts/admin/tokens`, revoke the lost one, and stop that server.

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

- **Staying signed in.** The Dashboard renews the ID token before it expires, using the refresh token. A provider that gives none leaves people to sign in again when the token expires, which takes 5 minutes on a default Keycloak. The refresh token stays in the tab, next to the ID token, and ends with it.
- **Signing out** also ends the session at the provider, so that the next sign-in asks for a password, if the provider has an `end_session_endpoint`.
- **When the provider is away,** servers still start, and applications carry on with their tokens. People already signed in carry on until their ID token expires, and new sign-ins fail: the server answers `UNAVAILABLE` rather than calling the token invalid. Keep a ServiceAccount token to get in meanwhile.

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

A **Role** holds rules, and a **RoleBinding** gives a role to subjects in some namespaces. Three roles are built in, and a Role cannot take their names:

| Role | Allows |
|---|---|
| `admin` | Everything |
| `viewer` | Reading and watching everything |
| `client` | Reading and watching Configs, and reading, watching and writing Instances |

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
- `GET` includes lists, and only what the rule allows shows up in them.
- A lease belongs to whoever took it, so one account cannot attach its instances to another's lease.
- A change to a role reaches open Watch streams: one that lost permission is canceled.

`GET /v1/me` shows who the server thinks you are, and your effective rules.

With `cosky.auth.enabled: false` everyone may do everything. It is for development only.

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

With TLS on, port 8080 speaks only HTTPS, while the image's health check and the probes in `k8s/base` use HTTP. Give them a plain port of their own:

```yaml
management:
  server:
    port: 8081
    ssl:
      enabled: false
```

Then point the probes at it, and the container's check too: `docker run --health-cmd 'curl -f http://localhost:8081/actuator/health/liveness || exit 1'`.

## Audit

Each write and each failed sign-in is one JSON line in the log, with the time, subject, verb, kind, namespace, name, revision, outcome and remote address. Config contents and tokens never appear. The server keeps no audit data of its own: collect the lines with your log system, which also decides how long they stay.

## What is not protected

- Config contents are not encrypted, in Redis or in client [snapshots](./sdk.md#snapshots).
- Anyone who can reach Redis can read and change everything, past all of the above.
- Instance metadata is visible to anyone allowed to read the instance.
- `/actuator/health`, `/actuator/info` and `/actuator/prometheus` on port 8080 need no token, and the metrics name your namespaces. Keep them off public networks, or move them with `management.server.port`.
- gRPC health on port 9090 needs no token. gRPC reflection is off, and `spring.grpc.server.reflection.enabled` turns it on.
