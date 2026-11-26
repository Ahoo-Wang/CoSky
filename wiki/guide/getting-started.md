---
title: Getting Started
description: Run a CoSky server, serve a Config to a Spring Boot application, and find it from another one.
---

# Getting Started

::: warning 8.0 is a release candidate
From `8.0.0-rc.1` to `8.0.0`, its API, wire format, configuration and Redis data only grow. It becomes `8.0.0` once it has run in a real environment for two weeks without a serious problem, so try it in one of yours before production. `ahoowang/cosky:latest` is still 6.x until `8.0.0`, when it moves to 8.0: a 6.x deployment should name its version, such as `6.2.0`. The documentation of 6.x is in the [`v6` branch](https://github.com/Ahoo-Wang/CoSky/tree/v6/wiki/guide).
:::

This takes a server in development mode and a Spring Boot 4 application. [Requirements](./requirements.md) lists the versions.

## 1. Start a server

```bash
docker run -p 8080:8080 -p 9090:9090 ahoowang/cosky:8.0.0-rc.3 --dev
```

`--dev` needs no Redis and no sign-in. Nothing survives a restart, so never run it for real: [Deployment](./deployment.md) shows how. Port 9090 serves applications over gRPC. Port 8080 serves the REST API and the Dashboard, at <http://localhost:8080>.

## 2. Add the starter

```kotlin
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.2.0"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2026.0.0"))
    implementation(platform("me.ahoo.cosky:cosky-bom:8.0.0-rc.3"))
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

```bash
curl localhost:8081/hello/World
```

It answers `Bonjour, World`. Write the Config again with another greeting, and the next call answers with that one.

## 5. Find it from another application

A second application calls the provider by its service name. It takes the same dependencies, and `spring-cloud-starter-loadbalancer`:

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
```

```yaml
# application.yaml
server:
  port: 8082
spring:
  application:
    name: consumer
cosky:
  endpoints: localhost:9090
  namespace: default
```

`@LoadBalanced` makes a `WebClient` take the host of a URL as a service name, and pick one of the instances CoSky knows:

```kotlin
@SpringBootApplication
class ConsumerApplication {
    @Bean
    @LoadBalanced
    fun webClientBuilder(): WebClient.Builder = WebClient.builder()
}

@RestController
class CallController(builder: WebClient.Builder) {
    private val provider = builder.baseUrl("http://provider").build()

    @GetMapping("/call/{name}", produces = [MediaType.TEXT_PLAIN_VALUE])
    suspend fun call(@PathVariable name: String): String =
        provider.get().uri("/hello/{name}", name).retrieve().awaitBody()
}
```

```bash
curl localhost:8082/call/World
```

It answers `Bonjour, World`, from the provider.

On a Mac with Apple silicon, Netty may log an error that it cannot load `MacOSDnsServerAddressStreamProvider`, as Reactor Netty brings the Intel build of that library only. The other one quiets it:

```kotlin
runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-aarch_64")
```

## 6. Look around

Open the [Dashboard](./dashboard.md) at <http://localhost:8080>. Instances show up under **Service**, with **Leased** next to them, and the topology on the first page shows who calls whom. The server records the topology every 30 seconds ([`cosky.view.topology-flush-interval`](./properties.md)), so the consumer shows up there, and here, up to half a minute after it starts:

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
