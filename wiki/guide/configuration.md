---
title: Configuration
description: Load configuration from CoSky into Spring, refresh it live, and manage versions, rollback, import and export.
---

# Configuration

This guide is for application developers using the config starter, and for whoever manages configs in the dashboard. Setup is covered in [Getting Started](./getting-started.md).

## Which config an application loads

At startup the starter loads one config, identified by a **namespace** and a **config id**:

| | Property | Default |
|---|---|---|
| Namespace | `spring.cloud.cosky.namespace` | `cosky-{default}` |
| Config id | `spring.cloud.cosky.config.config-id` | `${spring.application.name}.${file-extension}` |
| Default extension | `spring.cloud.cosky.config.file-extension` | `yaml` |

These properties belong in `bootstrap.yaml`, and the application needs `spring-cloud-starter-bootstrap` (see [Getting Started](./getting-started.md#_2-add-the-dependencies)).

- **Format** follows the extension of the config id: `.yaml` / `.yml`, `.properties` or `.xml`. A config id without an extension uses `file-extension`.
- **Multi-document YAML** (`---`) is supported; a later document overrides an earlier one.
- **Precedence**: properties from CoSky override `application.yaml` and other local sources, as with any Spring Cloud bootstrap property source.
- **Missing config**: the application starts with a warning and no CoSky properties.
- **Redis unreachable**: loading waits up to `spring.cloud.cosky.config.timeout` (2s).

Several services can share settings by pointing `config-id` at the same config.

## Live refresh

When a config changes, every application using it receives the change through Redis Pub/Sub, typically within milliseconds, and the starter publishes Spring Cloud's `RefreshEvent`. What updates depends on how a value is read:

| How the value is read | Updated on change |
|---|---|
| `@ConfigurationProperties` beans | ✅ rebound |
| `@Value` in a `@RefreshScope` bean | ✅ the bean is recreated on next use |
| `@Value` in a regular singleton | ❌ keeps the startup value |
| `Environment.getProperty(...)` | ✅ |

The refresh event is published only after the local cache holds the new value, so a refreshed bean never reads the previous version. If an application misses a message (for example during a Redis reconnection), its cache resyncs within 5 minutes.

## Versions and rollback

Every save that changes the content creates a new version; saving identical content does nothing. CoSky keeps the **latest 10 versions** of each config and can roll back to any of them; rolling back saves the old content as a new version, so it can be undone too. A removed config can also be rolled back.

In the dashboard, **Config → History** lists versions with a diff against the current content. Through the API:

```bash
GET  /v1/namespaces/{namespace}/configs/{configId}/versions             # list versions
GET  /v1/namespaces/{namespace}/configs/{configId}/versions/{version}   # content of a version
PUT  /v1/namespaces/{namespace}/configs/{configId}/to/{targetVersion}   # roll back
```

## Import and export

- **Export** (`GET /v1/namespaces/{namespace}/configs/export`) downloads every config of a namespace as a zip, one file per config.
- **Import** (`POST /v1/namespaces/{namespace}/configs`, multipart field `importZip`) creates a config per file. `policy=skip` (default) keeps existing configs, `policy=overwrite` replaces them.
- **From Nacos**: a Nacos export zip imports as is. The `DEFAULT_GROUP/` prefix is dropped and other `/` become `-`.
- **Limits**: 10 MiB per zip, 64 MiB once unzipped. Directories and hidden files are skipped.

## Turning it off

`spring.cloud.cosky.config.enabled=false` disables the config starter; `spring.cloud.cosky.enabled=false` disables CoSky entirely.

## See also

- [Configuration Properties](./properties.md) for every property.
- [Using the SDK](./sdk.md#configuration) to read and watch configs without Spring.
- [Architecture](./architecture.md#consistency-model) for how the cache stays consistent.
