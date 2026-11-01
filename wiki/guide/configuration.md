---
title: Configuration
description: Serve Spring properties from CoSky, refresh them live, and keep their revisions.
---

# Configuration

A **Config** is a named document in a namespace: a name, a format and its content, up to 1 MiB.

| Field | Rules |
|---|---|
| Name | Letters, digits, `.`, `_`, `-` and `/`, up to 253 characters. `shared/datasource.yaml` is one name. |
| Format | `YAML`, `JSON`, `PROPERTIES` or `TEXT`, declared with the Config. Only importing a zip file guesses it, from the file extension. |
| Content | UTF-8. The server keeps its SHA-256, which you can compare and cannot set. |

## Loading into Spring

```yaml
spring:
  config:
    import: "optional:cosky:order-service.yaml,cosky:shared/datasource.yaml"
cosky:
  endpoints: cosky.internal:9090
  token: ${COSKY_TOKEN}
  namespace: prod
```

- Only `default` exists at first. Create any other namespace before using it: `curl -X PUT localhost:8080/v1/namespaces/prod -H "Authorization: Bearer $COSKY_TOKEN" -H 'Content-Type: application/json' -d '{"namespace":{}}'`.
- `cosky:` without `optional:` fails the start when the Config cannot be loaded, including when no server can be reached and no snapshot holds it. When the server refuses, the start fails at once, and the log says why.
- YAML and JSON are parsed as YAML, and properties as properties. A TEXT Config becomes one property named after the Config.
- `cosky.config.load-timeout` (5 seconds) limits how long an import waits for the server.
- An import connects with what is known of `cosky.*` when its file is read, so keep these settings in the same file or in the environment. Spring reads `application.yaml` before `application-prod.yaml`, whose settings do not reach an import in the former.

## Live refresh

With `spring-cloud-context` on the classpath, a change to an imported Config publishes a Spring Cloud `RefreshEvent`, so beans in `@RefreshScope` and `@ConfigurationProperties` beans see it. This holds for a Config that did not exist at start as well. Turn it off with `cosky.config.refresh.enabled: false`.

The application compares against the revision it loaded, so a change made between loading and watching still counts.

## Writing

Use the [Dashboard](./dashboard.md) or the [REST API](./rest-api.md). A write replaces the whole Config:

```bash
curl -X PUT localhost:8080/v1/namespaces/prod/configs/shared%2Fdatasource.yaml \
  -H "Authorization: Bearer $COSKY_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'If-Match: "42"' \
  -d '{"config":{"format":"CONFIG_FORMAT_YAML","content":"url: jdbc:mysql://db/orders"}}'
```

| Header | Means |
|---|---|
| `If-Match: "42"` | Only if it is still at revision 42. Otherwise `409`, and nothing changes. |
| `If-None-Match: *` | Only if it does not exist. |
| Neither | Write regardless. |

The Dashboard always sends the first two, so nobody overwrites a change they did not see. Writing the same content changes nothing: there is no new revision and no event.

A `/` in a name is encoded as `%2F` in the path, because a name is one path segment.

## History and rollback

Each Config keeps its last 20 revisions, newest first:

```bash
curl localhost:8080/v1/namespaces/prod/configs/app.yaml/history
```

Rolling back writes the content of an earlier revision as a new one, so the history only grows:

```bash
curl -X POST localhost:8080/v1/namespaces/prod/configs/app.yaml:rollback \
  -H 'Content-Type: application/json' -d '{"toRevision":"41"}'
```

A deleted Config keeps its history until it is created again or its namespace is deleted, and a rollback brings it back.

## Import and export

A zip file moves many Configs at once. Each entry path is a Config name, and its extension decides the format.

```bash
curl -o configs.zip localhost:8080/v1/namespaces/prod/configs:export
```

```bash
curl -X POST localhost:8080/v1/namespaces/staging/configs:import --data-binary @configs.zip
```

An import overwrites Configs of the same name, and holds at most 10,000 entries.

## Keeping secrets

Config content is not encrypted, on the server or in the [snapshots](./sdk.md#snapshots) clients keep on disk. Turn snapshots of Configs off with `cosky.snapshot.include-config: false`, and keep real secrets in a system made for them.
