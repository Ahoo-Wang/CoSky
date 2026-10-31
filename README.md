# [CoSky](https://github.com/Ahoo-Wang/CoSky): Configuration and Service Discovery

<p align="center">
     <img src="./docs/cosky-logo.svg" alt="CoSky Logo" width="200"/>
</p>

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub release](https://img.shields.io/github/release/Ahoo-Wang/CoSky.svg)](https://github.com/Ahoo-Wang/CoSky/releases)
[![Maven Central Version](https://img.shields.io/maven-central/v/me.ahoo.cosky/cosky-client)](https://central.sonatype.com/artifact/me.ahoo.cosky/cosky-core)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/9e31358946b645abb283e83b10e85e2d)](https://www.codacy.com/gh/Ahoo-Wang/CoSky/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=Ahoo-Wang/CoSky&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/Ahoo-Wang/CoSky/branch/main/graph/badge.svg?token=N114DHHBUI)](https://codecov.io/gh/Ahoo-Wang/CoSky)
[![Integration Test Status](https://github.com/Ahoo-Wang/CoSky/actions/workflows/integration-test.yml/badge.svg)](https://github.com/Ahoo-Wang/CoSky)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/Ahoo-Wang/CoSky)

> **[Documentation](https://cosky.ahoo.me/)** | [中文文档](https://cosky.ahoo.me/zh/) | [中文 README](README.zh-CN.md)

CoSky provides **configuration management** and **service discovery** for microservices. A small server sits in front of **Redis**, and applications talk only to that server.

- **Reads from memory.** Each application keeps a local view that a Watch stream keeps current, so reading a Config or choosing an instance costs no network round trip.
- **Keeps working through failures.** Clients serve what they know when the server is away, and restart from a snapshot on disk. A mass expiry after a partition is held back rather than obeyed.
- **Access control on every call.** ServiceAccount tokens and OIDC, for applications as well as people, with roles down to a name prefix.
- **Nothing new to operate.** One image holds the server and the Dashboard, and needs only the Redis you already run.

## Features

| Area | What you get |
|---|---|
| Configuration | `spring.config.import` with live refresh; YAML, JSON, properties and text; revisions with rollback; zip import and export |
| Service discovery | Spring Cloud `DiscoveryClient`, `ServiceRegistry` and LoadBalancer; instances under a lease; metadata and weights; fixed instances |
| Resilience | Local views keep serving through an outage; snapshots survive a restart; mass expiry protection; several server replicas |
| Security | Tokens and OIDC on every call; roles scoped to namespaces and name prefixes; TLS; a structured audit log |
| Operations | Kustomize manifests; Prometheus metrics; OpenTelemetry traces; topology of who watches whom |

## Quick start

Start a server. `--dev` needs no Redis and no sign-in, and keeps nothing:

```bash
docker run -p 8080:8080 -p 9090:9090 ahoowang/cosky --dev
```

Add the starter to a Spring Boot 4 application:

```kotlin
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.1.2"))
    implementation(platform("me.ahoo.cosky:cosky-bom:8.0.0"))
    implementation("me.ahoo.cosky:cosky-spring-boot-starter")
    implementation("org.springframework.cloud:spring-cloud-context")
}
```

Point it at the server:

```yaml
spring:
  application:
    name: order-service          # registers as order-service
  config:
    import: "optional:cosky:order-service.yaml"
  cloud:
    compatibility-verifier:
      enabled: false             # until Spring Cloud 2026.0, which declares Spring Boot 4.1
cosky:
  endpoints: localhost:9090
```

Open the Dashboard at <http://localhost:8080>. The [Getting Started](https://cosky.ahoo.me/guide/getting-started) guide walks through the whole setup, and the [examples](examples) hold a provider and a consumer.

## Moving from 6.x

8.0 is a new system: applications on 6.x and 8.0 cannot see each other, so they move together. [Moving from 6.x](https://cosky.ahoo.me/guide/upgrade-8) explains the cutover, and [`cosky-migrate`](migration) imports the Configs and reports what to rebuild by hand. The `main` branch carries 6.x until 8.0 is released.

## Documentation

| | |
|---|---|
| [Overview](https://cosky.ahoo.me/guide/) | What CoSky is, and when it fits |
| [Configuration](https://cosky.ahoo.me/guide/configuration) · [Service Discovery](https://cosky.ahoo.me/guide/service-discovery) · [SDK](https://cosky.ahoo.me/guide/sdk) | Using it |
| [Deployment](https://cosky.ahoo.me/guide/deployment) · [Security](https://cosky.ahoo.me/guide/security) · [Dashboard](https://cosky.ahoo.me/guide/dashboard) | Running it |
| [Properties](https://cosky.ahoo.me/guide/properties) · [REST API](https://cosky.ahoo.me/guide/rest-api) · [Requirements](https://cosky.ahoo.me/guide/requirements) | Reference |
| [Architecture](https://cosky.ahoo.me/guide/architecture) · [Performance](https://cosky.ahoo.me/guide/performance) | How it works |

## Contributing

Read the [contributing guide](CONTRIBUTING.md) for the development setup, conventions, review and release process, and [SECURITY.md](SECURITY.md) to report a vulnerability privately. Everyone taking part follows the [code of conduct](CODE_OF_CONDUCT.md).

## License

CoSky is licensed under the [Apache License 2.0](LICENSE).
