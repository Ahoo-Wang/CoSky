# [CoSky](https://github.com/Ahoo-Wang/CoSky)：配置管理与服务发现

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

> **[文档](https://cosky.ahoo.me/zh/)** | [English Documentation](https://cosky.ahoo.me/) | [English README](README.md)

CoSky 为微服务提供**配置管理**和**服务发现**。一个小型 server 位于 **Redis** 前面，应用只和这个 server 通信。

- **从内存读取。** 每个应用都保存一份由 Watch 流保持最新的本地视图，读配置、选实例都不需要网络往返。
- **故障时继续工作。** server 不可用时，客户端继续提供已知的数据，重启后从磁盘快照恢复。分区之后的大面积到期会被拦住，而不是照单执行。
- **每次调用都鉴权。** 应用和人员都使用 ServiceAccount token 或 OIDC，角色可细到名称前缀。
- **没有新东西要运维。** 一个镜像同时包含 server 和 Dashboard，只依赖你已有的 Redis。

## 功能

| 方面 | 提供什么 |
|---|---|
| 配置 | `spring.config.import` 与实时刷新；YAML、JSON、properties 和文本；revision 与回滚；zip 导入导出 |
| 服务发现 | Spring Cloud `DiscoveryClient`、`ServiceRegistry` 和 LoadBalancer；挂在租约上的实例；元数据和权重；固定实例 |
| 容错 | 故障期间本地视图继续服务；快照在重启后保留；大面积到期保护；多个 server 副本 |
| 安全 | 每次调用都使用 token 或 OIDC；角色可限定到 namespace 和名称前缀；TLS；结构化审计日志 |
| 运维 | Kustomize 清单；Prometheus 指标；OpenTelemetry 链路追踪；调用拓扑 |

## 快速开始

启动 server。`--dev` 不需要 Redis 和登录，也不保留任何数据：

```bash
docker run -p 8080:8080 -p 9090:9090 ahoowang/cosky --dev
```

在 Spring Boot 4 应用中添加 starter：

```kotlin
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2025.1.2"))
    implementation(platform("me.ahoo.cosky:cosky-bom:8.0.0"))
    implementation("me.ahoo.cosky:cosky-spring-boot-starter")
    implementation("org.springframework.cloud:spring-cloud-context")
}
```

连接 server：

```yaml
spring:
  application:
    name: order-service          # 注册为 order-service
  config:
    import: "optional:cosky:order-service.yaml"
  cloud:
    compatibility-verifier:
      enabled: false             # 直到 Spring Cloud 2026.0，它才声明 Spring Boot 4.1
cosky:
  endpoints: localhost:9090
```

打开 <http://localhost:8080> 的 Dashboard。[快速开始](https://cosky.ahoo.me/zh/guide/getting-started)有完整步骤，[示例](examples)中有一个 provider 和一个 consumer。

## 从 6.x 迁移

8.0 是一套新系统：6.x 和 8.0 的应用互相看不到对方，所以要一起迁移。[从 6.x 迁移](https://cosky.ahoo.me/zh/guide/upgrade-8)说明了切换步骤，[`cosky-migrate`](migration) 负责导入 Config，并报告需要手工重建的内容。在 8.0 发布之前，`main` 分支仍是 6.x。

## 文档

| | |
|---|---|
| [概述](https://cosky.ahoo.me/zh/guide/) | CoSky 是什么，适合什么场景 |
| [配置管理](https://cosky.ahoo.me/zh/guide/configuration) · [服务发现](https://cosky.ahoo.me/zh/guide/service-discovery) · [SDK](https://cosky.ahoo.me/zh/guide/sdk) | 使用 |
| [部署](https://cosky.ahoo.me/zh/guide/deployment) · [安全](https://cosky.ahoo.me/zh/guide/security) · [控制台](https://cosky.ahoo.me/zh/guide/dashboard) | 运维 |
| [配置属性](https://cosky.ahoo.me/zh/guide/properties) · [REST API](https://cosky.ahoo.me/zh/guide/rest-api) · [需求](https://cosky.ahoo.me/zh/guide/requirements) | 参考 |
| [架构](https://cosky.ahoo.me/zh/guide/architecture) · [性能](https://cosky.ahoo.me/zh/guide/performance) | 原理 |

## 参与贡献

开发环境、编码规范、评审与发版流程见[贡献指南](CONTRIBUTING.md)；请按 [SECURITY.md](SECURITY.md) 私下报告安全漏洞。所有参与者都应遵守[行为准则](CODE_OF_CONDUCT.md)。

## 许可证

CoSky 基于 [Apache License 2.0](LICENSE) 开源。
