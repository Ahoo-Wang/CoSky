# [CoSky](https://github.com/Ahoo-Wang/CoSky): Configuration and Service Discovery on Redis

<p align="center">
     <img src="./docs/cosky-logo.svg" alt="CoSky Logo" width="200"/>
</p>

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub release](https://img.shields.io/github/release/Ahoo-Wang/CoSky.svg)](https://github.com/Ahoo-Wang/CoSky/releases)
[![Maven Central Version](https://img.shields.io/maven-central/v/me.ahoo.cosky/cosky-core)](https://central.sonatype.com/artifact/me.ahoo.cosky/cosky-core)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/9e31358946b645abb283e83b10e85e2d)](https://www.codacy.com/gh/Ahoo-Wang/CoSky/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=Ahoo-Wang/CoSky&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/Ahoo-Wang/CoSky/branch/v6/graph/badge.svg?token=N114DHHBUI)](https://codecov.io/gh/Ahoo-Wang/CoSky)
[![Integration Test Status](https://github.com/Ahoo-Wang/CoSky/actions/workflows/integration-test.yml/badge.svg)](https://github.com/Ahoo-Wang/CoSky)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Ahoo-Wang/CoSky)

> **[Documentation](https://cosky.ahoo.me/)** | [中文文档](https://cosky.ahoo.me/zh/) | [中文 README](README.zh-CN.md)

> [!IMPORTANT]
> This is the 6.x maintenance branch, and it takes bug fixes only. CoSky 8.0 is developed on [`main`](https://github.com/Ahoo-Wang/CoSky/tree/main).

CoSky provides **configuration management** and **service discovery** for Spring Cloud microservices, using only **Redis** as infrastructure. There is no registry cluster to operate: applications talk to the Redis you already run, and an optional REST API server adds a web dashboard, users and permissions.

- **Reads from memory.** Each application keeps a local cache that Redis Pub/Sub keeps current: reading a config or choosing an instance costs no network round trip ([benchmarks](https://cosky.ahoo.me/guide/performance)).
- **Atomic writes.** Every change is a single Lua script, announced to every subscriber.
- **Spring Cloud native.** A property source with live refresh, a `DiscoveryClient` and a service registry.

<p align="center">
     <img src="./docs/CoSky-Discovery.png" alt="CoSky service discovery"/>
</p>

## Features

| Area | What you get |
|---|---|
| Configuration | Live refresh, YAML / properties, versions with rollback, import and export (Nacos-compatible) |
| Service discovery | Ephemeral instances with automatic expiry, metadata and status, weighted load balancing, topology |
| Operations | REST API and dashboard, JWT authentication, namespace-scoped roles, audit log |
| Isolation | Namespaces separate environments or tenants in one Redis |

## Quick start

Add the starters (`spring-cloud-starter-bootstrap` makes Spring Cloud read `bootstrap.yaml`):

```kotlin
dependencies {
    implementation(platform("me.ahoo.cosky:cosky-bom:6.2.0"))
    implementation(platform("me.ahoo.cosky:cosky-dependencies:6.2.0"))
    implementation("me.ahoo.cosky:cosky-spring-cloud-starter-config")
    implementation("me.ahoo.cosky:cosky-spring-cloud-starter-discovery")
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
}
```

Point them at Redis in `bootstrap.yaml`:

```yaml
spring:
  application:
    name: order-service          # loads config order-service.yaml, registers as order-service
  data:
    redis:
      url: redis://localhost:6379
  cloud:
    cosky:
      namespace: dev
```

Run the dashboard to manage configs and services:

```bash
docker run -d --name cosky -p 8080:8080 \
  -e SPRING_DATA_REDIS_URL=redis://host.docker.internal:6379 \
  -e COSKY_SECURITY_KEY="$(openssl rand -base64 32)" \
  ahoowang/cosky:6.2.0
docker logs cosky | grep "init super user"    # password of the super user "cosky"
```

The [Getting Started](https://cosky.ahoo.me/guide/getting-started) guide walks through the whole setup; the [examples](examples) contain a provider and a Feign consumer.

## Documentation

| | |
|---|---|
| [Overview](https://cosky.ahoo.me/guide/) | What CoSky is, when it fits, how it compares |
| [Configuration](https://cosky.ahoo.me/guide/configuration) · [Service Discovery](https://cosky.ahoo.me/guide/service-discovery) · [SDK](https://cosky.ahoo.me/guide/sdk) | Using CoSky in applications |
| [Deployment](https://cosky.ahoo.me/guide/deployment) · [Security](https://cosky.ahoo.me/guide/security) · [Upgrading to 6.0](https://cosky.ahoo.me/guide/upgrade-6) | Running the REST API server |
| [Properties](https://cosky.ahoo.me/guide/properties) · [REST API](https://cosky.ahoo.me/guide/rest-api) | Reference |
| [Architecture](https://cosky.ahoo.me/guide/architecture) · [Performance](https://cosky.ahoo.me/guide/performance) | How it works |

## Contributing

Read the [contributing guide](CONTRIBUTING.md) for the development setup, conventions, review and release process, and [SECURITY.md](SECURITY.md) to report a vulnerability privately. Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md).

## License

CoSky is licensed under the [Apache License 2.0](LICENSE).
