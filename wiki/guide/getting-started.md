---
title: Getting Started
description: Run a CoSky server, serve a Config to a Spring Boot application, and find it from another one.
---

# Getting Started

::: warning 8.0 is in alpha
`8.0.0-alpha.2` is a preview with no compatibility promise: its API, wire format and Redis keys may still change. `ahoowang/cosky:latest` is still 6.x, whose documentation is in the [`v6` branch](https://github.com/Ahoo-Wang/CoSky/tree/v6/wiki/guide).
:::

This takes a server in development mode and a Spring Boot 4 application. [Requirements](./requirements.md) lists the versions.

## 1. Start a server

```bash
docker run -p 8080:8080 -p 9090:9090 ahoowang/cosky:8.0.0-alpha.2 --dev
```

`--dev` needs no Redis and no sign-in. Nothing survives a restart, so never run it for real: [Deployment](./deployment.md) shows how. Port 9090 serves applications over gRPC. Port 8080 serves the REST API and the Dashboard, at <http://localhost:8080>.

## 2. Add the starter

```kotlin
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.1.2"))
    implementation(platform("me.ahoo.cosky:cosky-bom:8.0.0-alpha.2"))
    implementation("me.ahoo.cosky:cosky-spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-context") // for live refresh
}
```

In Kotlin, add the `kotlin("plugin.spring")` Gradle plugin as well: the bean in `@RefreshScope` below has to be open.

## 3. Configure the application

```yaml
# application.yaml
server:
  port: 8081 # 8080 is the CoSky server
spring:
  application:
    name: provider
  config:
    import: "optional:cosky:provider.yaml"
  cloud:
    # Spring Cloud 2025.1 declares Spring Boot 4.0. Remove this once on Spring Cloud 2026.0.
    compatibility-verifier:
      enabled: false
cosky:
  endpoints: localhost:9090
  namespace: default
```

The application registers itself as the service `provider` once its web server is up, and imports the Config `provider.yaml`. `optional:` lets it start while that Config does not exist yet.

## 4. Create a Config

```bash
curl -X PUT localhost:8080/v1/namespaces/default/configs/provider.yaml \
  -H 'Content-Type: application/json' \
  -d '{"config":{"format":"CONFIG_FORMAT_YAML","content":"example:\n  greeting: Bonjour"}}'
```

A bean in `@RefreshScope` sees the new value without a restart:

```kotlin
@RestController
@RefreshScope
class HelloController(@Value("\${example.greeting:Hello}") private val greeting: String) {
    @GetMapping("/hello/{name}", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun hello(@PathVariable name: String): String = "$greeting, $name"
}
```

## 5. Find it from another application

Add `spring-cloud-starter-loadbalancer` to a second application with the same settings and its own name and port, and call the provider by its service name:

```kotlin
@Bean
@LoadBalanced
fun webClientBuilder(): WebClient.Builder = WebClient.builder()

// ...
builder.baseUrl("http://provider").build().get().uri("/hello/World").retrieve().awaitBody<String>()
```

## 6. Look around

Open the [Dashboard](./dashboard.md) at <http://localhost:8080>. Instances show up under **Service**, with **Leased** next to them, and the topology on the first page shows who calls whom.

```bash
curl localhost:8080/v1/namespaces/default/views/topology
```

## See it run

The repository holds this as a working example. `examples/README.md` walks through it:

```bash
./gradlew :server:run --args='--dev'
```

```bash
./gradlew :examples:provider:run
```

```bash
./gradlew :examples:consumer:run
```

## Next steps

- [Configuration](./configuration.md): formats, revisions, rollback and import.
- [Service Discovery](./service-discovery.md): leases, weights and fixed instances.
- [Deployment](./deployment.md) and [Security](./security.md): before real traffic.
