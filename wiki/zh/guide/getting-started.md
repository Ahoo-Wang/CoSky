---
title: 快速开始
description: 为 Spring Boot 应用接入配置管理与服务发现，并运行控制台。
---

# 快速开始

大约十分钟后，你将得到一个从 CoSky 加载配置、注册自身并能发现其他服务的 Spring Boot 应用，以及用来管理它们的控制台。

**准备：** Java 17+、Docker（运行 Redis 和控制台），以及一个用 Gradle 或 Maven 构建的 Spring Boot 4 应用。

## 1. 启动 Redis 与控制台

```bash
docker network create cosky
docker run -d --name redis --network cosky -p 6379:6379 redis:7
docker run -d --name cosky --network cosky -p 8080:8080 \
  -e SPRING_DATA_REDIS_URL=redis://redis:6379 \
  -e COSKY_SECURITY_KEY="$(openssl rand -base64 32)" \
  ahoowang/cosky:6.2.0
```

服务器首次启动时会创建超级用户 `cosky`，并生成随机密码：

```bash
docker logs cosky | grep "init super user"
```

打开 <http://localhost:8080>，以 `cosky` 登录。

## 2. 添加依赖

::: code-group

```kotlin [Gradle (Kotlin DSL)]
dependencies {
    implementation(platform("me.ahoo.cosky:cosky-bom:6.2.0"))
    implementation(platform("me.ahoo.cosky:cosky-dependencies:6.2.0"))
    implementation("me.ahoo.cosky:cosky-spring-cloud-starter-config")
    implementation("me.ahoo.cosky:cosky-spring-cloud-starter-discovery")
    // 加载 bootstrap.yaml，CoSky 配置源在其中配置。
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

`cosky-bom` 管理 CoSky 各模块的版本，`cosky-dependencies` 管理 Spring Boot、Spring Cloud 以及 CoSky 测试所用其他库的版本。没有 `spring-cloud-starter-bootstrap`（或 `spring.cloud.bootstrap.enabled=true`）时，`bootstrap.yaml` 会被忽略，也就不会从 CoSky 加载任何配置。

## 3. 配置应用

`src/main/resources/bootstrap.yaml`：

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

- 加载的配置是 `dev` 命名空间中的 `order-service.yaml`（应用名加 `.yaml`）。
- 应用以服务 `order-service` 注册，端口为其 Web 服务器端口。

## 4. 创建配置

在控制台中：

1. 打开 **Namespace**，添加 `{dev}`。CoSky 会把每个命名空间包进 Redis Cluster hash tag，所以应用里的 `dev` 在 Redis 中是 `{dev}`；应用不会自己把命名空间加入这个列表。
2. 打开 **Config**，选择 `{dev}`，创建 `order-service.yaml`：

```yaml
greeting: Hello from CoSky
```

像读取任何 Spring 属性一样读取它：

```kotlin
@RestController
@RefreshScope
class GreetingController(@Value("\${greeting}") private val greeting: String) {
    @GetMapping("/greeting")
    fun greeting() = greeting
}
```

## 5. 运行并验证

启动应用，然后：

- `curl localhost:<端口>/greeting` 返回 `Hello from CoSky`。
- 在控制台中修改这个配置：下一次请求返回新值，无需重启。
- 控制台的 **Service** 页面列出 `order-service` 及其一个实例。
- 其他应用通过 Spring Cloud LoadBalancer 访问它，例如用 `@LoadBalanced` 的 `WebClient` 或 OpenFeign 请求 `http://order-service/greeting`。

## 下一步

- [配置管理](./configuration.md)：格式、刷新、版本与回滚。
- [服务发现](./service-discovery.md)：元数据、状态、过期与负载均衡。
- [部署](./deployment.md)：在生产环境运行服务器。
- [示例](https://github.com/Ahoo-Wang/CoSky/tree/main/examples)包含一个服务提供者和一个 Feign 消费者。
