---
title: 快速开始
description: 运行一个 CoSky server，把 Config 提供给 Spring Boot 应用，并从另一个应用找到它。
---

# 快速开始

你需要一个开发模式的 server 和一个 Spring Boot 4 应用。版本要求见[需求](./requirements.md)。

## 1. 启动 server

```bash
docker run -p 8080:8080 -p 9090:9090 ahoowang/cosky --dev
```

`--dev` 不需要 Redis，也不需要登录。重启后什么都不会保留，所以不要用于生产环境，正式部署见[部署](./deployment.md)。9090 端口通过 gRPC 服务应用；8080 端口提供 REST API 和 Dashboard，地址是 <http://localhost:8080>。

## 2. 添加 starter

```kotlin
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.1.2"))
    implementation(platform("me.ahoo.cosky:cosky-bom:8.0.0"))
    implementation("me.ahoo.cosky:cosky-spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.cloud:spring-cloud-context") // 用于实时刷新
}
```

## 3. 配置应用

```yaml
# application.yaml
spring:
  application:
    name: provider
  config:
    import: "optional:cosky:provider.yaml"
  cloud:
    # Spring Cloud 2025.1 声明的是 Spring Boot 4.0。升级到 Spring Cloud 2026.0 后即可去掉。
    compatibility-verifier:
      enabled: false
cosky:
  endpoints: localhost:9090
  namespace: default
```

Web 服务器就绪后，应用会注册为服务 `provider`，并导入 Config `provider.yaml`。有了 `optional:`，这个 Config 还不存在时应用也能启动。

## 4. 创建 Config

```bash
curl -X PUT localhost:8080/v1/namespaces/default/configs/provider.yaml \
  -H 'Content-Type: application/json' \
  -d '{"config":{"format":"CONFIG_FORMAT_YAML","content":"example:\n  greeting: Bonjour"}}'
```

`@RefreshScope` 中的 bean 不用重启就能看到新值：

```kotlin
@RestController
@RefreshScope
class HelloController(@Value("\${example.greeting:Hello}") private val greeting: String) {
    @GetMapping("/hello/{name}", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun hello(@PathVariable name: String): String = "$greeting, $name"
}
```

## 5. 从另一个应用找到它

在第二个应用中加入 `spring-cloud-starter-loadbalancer`，使用相同的配置和自己的名称，然后按服务名调用 provider：

```kotlin
@Bean
@LoadBalanced
fun webClientBuilder(): WebClient.Builder = WebClient.builder()

// ...
builder.baseUrl("http://provider").build().get().uri("/hello/World").retrieve().awaitBody<String>()
```

## 6. 看一看

打开 <http://localhost:8080> 的 [Dashboard](./dashboard.md)。实例显示在 **Service** 下，旁边标着 **Leased**；首页的拓扑图显示谁在调用谁。

```bash
curl localhost:8080/v1/namespaces/default/views/topology
```

## 运行示例

仓库中有这个完整的示例，`examples/README.md` 有详细步骤：

```bash
./gradlew :server:run --args='--dev'
```

```bash
./gradlew :examples:provider:run
```

```bash
./gradlew :examples:consumer:run
```

## 下一步

- [配置管理](./configuration.md)：格式、revision、回滚和导入。
- [服务发现](./service-discovery.md)：租约、权重和固定实例。
- [部署](./deployment.md)和[安全](./security.md)：接入真实流量之前必读。
