---
title: Getting Started
description: Add configuration and service discovery to a Spring Boot application, then run the dashboard.
---

# Getting Started

In about ten minutes you will have a Spring Boot application that loads its configuration from CoSky, registers itself, and discovers other services, plus the dashboard to manage both.

**You need:** Java 17+, Docker (for Redis and the dashboard), and a Spring Boot 4 application built with Gradle or Maven.

## 1. Start Redis and the dashboard

```bash
docker network create cosky
docker run -d --name redis --network cosky -p 6379:6379 redis:7
docker run -d --name cosky --network cosky -p 8080:8080 \
  -e SPRING_DATA_REDIS_URL=redis://redis:6379 \
  -e COSKY_SECURITY_KEY="$(openssl rand -base64 32)" \
  ahoowang/cosky:6.2.0
```

The server creates the super user `cosky` with a random password on first start:

```bash
docker logs cosky | grep "init super user"
```

Open <http://localhost:8080> and sign in as `cosky`.

## 2. Add the dependencies

::: code-group

```kotlin [Gradle (Kotlin DSL)]
dependencies {
    implementation(platform("me.ahoo.cosky:cosky-bom:6.2.0"))
    implementation(platform("me.ahoo.cosky:cosky-dependencies:6.2.0"))
    implementation("me.ahoo.cosky:cosky-spring-cloud-starter-config")
    implementation("me.ahoo.cosky:cosky-spring-cloud-starter-discovery")
    // Loads bootstrap.yaml, where the CoSky config source is configured.
    implementation("org.springframework.cloud:spring-cloud-starter-bootstrap")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
}
```

```xml [Maven]
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>me.ahoo.cosky</groupId>
            <artifactId>cosky-bom</artifactId>
            <version>6.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>me.ahoo.cosky</groupId>
            <artifactId>cosky-dependencies</artifactId>
            <version>6.2.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
<dependencies>
    <dependency>
        <groupId>me.ahoo.cosky</groupId>
        <artifactId>cosky-spring-cloud-starter-config</artifactId>
    </dependency>
    <dependency>
        <groupId>me.ahoo.cosky</groupId>
        <artifactId>cosky-spring-cloud-starter-discovery</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-bootstrap</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-loadbalancer</artifactId>
    </dependency>
</dependencies>
```

:::

`cosky-bom` manages the versions of the CoSky modules, `cosky-dependencies` those of Spring Boot, Spring Cloud and the other libraries CoSky is tested with. Without `spring-cloud-starter-bootstrap` (or `spring.cloud.bootstrap.enabled=true`), `bootstrap.yaml` is ignored and no configuration is loaded from CoSky.

## 3. Configure the application

`src/main/resources/bootstrap.yaml`:

```yaml
spring:
  application:
    name: order-service
  data:
    redis:
      url: redis://localhost:6379
  cloud:
    cosky:
      namespace: dev
```

- The configuration loaded is `order-service.yaml` in the `dev` namespace (the application name plus `.yaml`).
- The application registers as service `order-service` on its web server port.

## 4. Create the configuration

In the dashboard:

1. Open **Namespace** and add `{dev}`. CoSky wraps every namespace in a Redis Cluster hash tag, so `dev` in the application is `{dev}` in Redis; applications do not add their namespace to this list themselves.
2. Open **Config**, select `{dev}`, and create `order-service.yaml`:

```yaml
greeting: Hello from CoSky
```

Read it like any Spring property:

```kotlin
@RestController
@RefreshScope
class GreetingController(@Value("\${greeting}") private val greeting: String) {
    @GetMapping("/greeting")
    fun greeting() = greeting
}
```

## 5. Run and verify

Start the application, then:

- `curl localhost:<port>/greeting` returns `Hello from CoSky`.
- Edit the config in the dashboard: the next request returns the new value, without a restart.
- **Service** in the dashboard lists `order-service` with one instance.
- Other applications reach it through Spring Cloud LoadBalancer, e.g. `http://order-service/greeting` with a `@LoadBalanced` `WebClient` or OpenFeign.

## Next steps

- [Configuration](./configuration.md): formats, refresh, versions and rollback.
- [Service Discovery](./service-discovery.md): metadata, status, expiry and load balancing.
- [Deployment](./deployment.md): run the server in production.
- The [examples](https://github.com/Ahoo-Wang/CoSky/tree/main/examples) contain a provider and a Feign consumer.
