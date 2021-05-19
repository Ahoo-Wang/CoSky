# Govern Service 基于 Redis 的服务治理平台（服务注册/发现 & 配置中心）

*Govern Service* 是一个轻量级、低成本的服务注册、服务发现、 配置服务 SDK，通过使用现有基础设施中的 Redis （相信你已经部署了Redis），不用给运维部署带来额外的成本与负担。
借助于 Redis 的高性能， *Govern Service* 提供了超高TPS&QPS (10W+/s [JMH 基准测试](#jmh-benchmark))。*Govern Service* 结合本地进程缓存策略 + *Redis PubSub*
，实现实时进程缓存刷新，兼具无与伦比的QPS性能、进程缓存与 Redis 的实时一致性。

## 安装

### Gradle

> Kotlin DSL

``` kotlin
    val governVersion = "0.9.19";
    implementation("me.ahoo.govern:spring-cloud-starter-govern-config:${governVersion}")
    implementation("me.ahoo.govern:spring-cloud-starter-govern-discovery:${governVersion}")
```

### Maven

```xml
<?xml version="1.0" encoding="UTF-8"?>

<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

  <modelVersion>4.0.0</modelVersion>
  <artifactId>demo</artifactId>
  <properties>
    <govern.version>0.9.19</govern.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>me.ahoo.govern</groupId>
      <artifactId>spring-cloud-starter-govern-config</artifactId>
      <version>${govern.version}</version>
    </dependency>
    <dependency>
      <groupId>me.ahoo.govern</groupId>
      <artifactId>spring-cloud-starter-govern-discovery</artifactId>
      <version>${govern.version}</version>
    </dependency>
  </dependencies>

</project>
```

### bootstrap.yaml (Spring-Cloud-Config)

```yaml
spring:
  application:
    name: ${service.name:govern-rest-api}
  cloud:
    govern:
      namespace: ${govern.namespace:govern-{system}}
      config:
        config-id: ${spring.application.name}.yaml
      redis:
        mode: ${govern.redis.mode:standalone}
        url: ${govern.redis.uri:redis://localhost:6379}
logging:
  file:
    name: logs/${spring.application.name}.log
```

## REST-API Server (``Optional``)

### 安装 REST-API Server

#### 方式一：下载可执行文件

> 下载 [rest-api-server](https://github.com/Ahoo-Wang/govern-service/releases/download/0.9.19/govern-rest-api-0.9.19.tar)

> 解压 *govern-rest-api-0.9.19.tar*

```shell
cd govern-rest-api-0.9.19
# 工作目录: govern-rest-api-0.9.19
bin/govern-rest-api --server.port=8080 --govern.redis.uri=redis://localhost:6379
```

#### 方式二：在 Docker 中运行

```shell
docker pull ahoowang/govern-service:0.9.19
docker run --name govern-service -d -p 8080:8080 --link redis -e GOVERN_REDIS_URI=redis://redis:6379  ahoowang/govern-service:0.9.19
```
---
> MacBook Pro (M1)
>
> 请使用 *ahoowang/govern-service:0.9.19-armv7*

```shell
docker pull ahoowang/govern-service:0.9.19-armv7
docker run --name govern-service -d -p 8080:8080 --link redis -e GOVERN_REDIS_URI=redis://redis:6379  ahoowang/govern-service:0.9.19-armv7
```

#### 方式三：在 Kubernetes 中运行

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: govern-service-rest-api
spec:
  replicas: 1
  selector:
    matchLabels:
      app: govern-service-rest-api
  template:
    metadata:
      labels:
        app: govern-service-rest-api
    spec:
      containers:
        - env:
            - name: GOVERN_REDIS_MODE
              value: standalone
            - name: GOVERN_REDIS_URI
              value: redis://redis-uri:6379
          image: ahoowang/govern-service:0.9.19
          name: govern-service
          resources:
            limits:
              cpu: "1"
              memory: 640Mi
            requests:
              cpu: 250m
              memory: 512Mi
          volumeMounts:
            - mountPath: /etc/localtime
              name: volume-localtime
      volumes:
        - hostPath:
            path: /etc/localtime
            type: ""
          name: volume-localtime

---
apiVersion: v1
kind: Service
metadata:
  name: govern-service-rest-api
  labels:
    app: govern-service-rest-api
spec:
  selector:
    app: govern-service-rest-api
  ports:
    - name: rest
      port: 80
      protocol: TCP
      targetPort: 8080
```


---

> [http://localhost:8080/dashboard](http://localhost:8080/dashboard)

### Dashboard

![dashboard-dashboard](./docs/dashboard-dashboard.png)

#### 命名空间管理

![dashboard-namespace](./docs/dashboard-namespace.png)

#### 配置管理

![dashboard-config](./docs/dashboard-config.png)
---
![dashboard-config-edit](./docs/dashboard-config-edit.png)
---
![dashboard-config-rollback](./docs/dashboard-config-rollback.png)
---
![dashboard-config-import](./docs/dashboard-config-import.png)

#### 服务管理

![dashboard-service](./docs/dashboard-service.png)
---
![dashboard-service-edit](./docs/dashboard-service-edit.png)

### REST-API

> http://localhost:8080/swagger-ui/index.html#/

##### Namespace

![rest-api-namespace](./docs/rest-api-namespace.png)

- /v1/namespaces
  - GET
- /v1/namespaces/{namespace}
  - PUT
  - GET
- /v1/namespaces/current
  - GET
- /v1/namespaces/current/{namespace}
  - PUT

##### Config

![rest-api-config](./docs/rest-api-config.png)

- /v1/namespaces/{namespace}/configs
  - GET
- /v1/namespaces/{namespace}/configs/{configId}
  - GET
  - PUT
    - DELETE
- /v1/namespaces/{namespace}/configs/{configId}/versions
  - GET
- /v1/namespaces/{namespace}/configs/{configId}/versions/{version}
  - GET
- /v1/namespaces/{namespace}/configs/{configId}/to/{targetVersion}
  - PUT

#### Service

![rest-api-service](./docs/rest-api-service.png)

- /v1/namespaces/{namespace}/services/
  - GET
- /v1/namespaces/{namespace}/services/{serviceId}/instances
  - GET
  - PUT
- /v1/namespaces/{namespace}/services/{serviceId}/instances/{instanceId}
  - DELETE
- /v1/namespaces/{namespace}/services/{serviceId}/instances/{instanceId}/metadata
  - PUT
- /v1/namespaces/{namespace}/services/{serviceId}/lb
  - GET

## JMH-Benchmark

- 基准测试运行环境：笔记本开发机 ( MacBook Pro (M1) )
- 所有基准测试都在开发笔记本上执行。
- Redis 部署环境也在该笔记本开发机上。

### ConfigService

``` shell
gradle config:jmh
```

```
# JMH version: 1.29
# VM version: JDK 11.0.11, OpenJDK 64-Bit Server VM, 11.0.11+9-LTS
# VM invoker: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java
# VM options: -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/Users/ahoo/govern-service/config/build/tmp/jmh -Duser.country=CN -Duser.language=zh -Duser.variant
# Blackhole mode: full + dont-inline hint
# Warmup: 1 iterations, 10 s each
# Measurement: 1 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 50 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time

Benchmark                                          Mode  Cnt          Score   Error  Units
ConsistencyRedisConfigServiceBenchmark.getConfig  thrpt       265321650.148          ops/s
RedisConfigServiceBenchmark.getConfig             thrpt          106991.476          ops/s
RedisConfigServiceBenchmark.setConfig             thrpt          103659.132          ops/s
```

### ServiceDiscovery

``` shell
gradle discovery:jmh
```

```
# JMH version: 1.29
# VM version: JDK 11.0.11, OpenJDK 64-Bit Server VM, 11.0.11+9-LTS
# VM invoker: /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin/java
# VM options: -Dfile.encoding=UTF-8 -Djava.io.tmpdir=/Users/ahoo/govern-service/discovery/build/tmp/jmh -Duser.country=CN -Duser.language=zh -Duser.variant
# Blackhole mode: full + dont-inline hint
# Warmup: 1 iterations, 10 s each
# Measurement: 1 iterations, 10 s each
# Timeout: 10 min per iteration
# Threads: 50 threads, will synchronize iterations
# Benchmark mode: Throughput, ops/time

Benchmark                                                Mode  Cnt          Score   Error  Units
ConsistencyRedisServiceDiscoveryBenchmark.getInstances  thrpt        76894658.867          ops/s
ConsistencyRedisServiceDiscoveryBenchmark.getServices   thrpt       466036317.472          ops/s
RedisServiceDiscoveryBenchmark.getInstances             thrpt          107778.244          ops/s
RedisServiceDiscoveryBenchmark.getServices              thrpt          106920.412          ops/s
RedisServiceRegistryBenchmark.deregister                thrpt          114094.513          ops/s
RedisServiceRegistryBenchmark.register                  thrpt          109085.694          ops/s
RedisServiceRegistryBenchmark.renew                     thrpt          127003.104          ops/s
```

## TODO

1. Grayscale Publishing
