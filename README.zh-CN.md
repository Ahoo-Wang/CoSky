# [CoSky](https://github.com/Ahoo-Wang/CoSky) 高性能、低成本微服务治理平台（服务注册/发现 & 配置中心）

> [English Document](https://github.com/Ahoo-Wang/CoSky/blob/main/README.md)

*[CoSky](https://github.com/Ahoo-Wang/CoSky)* 是一个轻量级、低成本的服务注册、服务发现、 配置服务 SDK，通过使用现有基础设施中的 Redis （相信你已经部署了Redis），不用给运维部署带来额外的成本与负担。 借助于 Redis 的高性能， *CoSky* 提供了超高TPS&QPS (10W+/s [JMH 基准测试](#jmh-benchmark))。*CoSky* 结合本地进程缓存策略 + *Redis PubSub*，实现实时进程缓存刷新，兼具无与伦比的QPS性能 (7000W+/s [JMH 基准测试](#jmh-benchmark))、进程缓存与 Redis 的实时一致性。

### 服务注册与发现

![CoSky-Discovery](./docs/CoSky-Discovery.png)

### 配置中心

![CoSky-Configuration](./docs/CoSky-Configuration.png)

### CoSky-Mirror （实时同步服务实例变更状态）

> CoSky-Mirror 就像一个镜子放在 Nacos、CoSky 中间，构建一个统一的服务发现平台。

![CoSky-Mirror](./docs/CoSky-Mirror.png)

![CoSky-Mirror-Unified](./docs/CoSky-Mirror-Unified.png)

## Examples

[Service Consumer --RPC--> Service Provider Examples](https://github.com/Ahoo-Wang/CoSky/tree/main/examples)

## 安装

### Gradle

> Kotlin DSL

``` kotlin
    val coskyVersion = "1.3.2";
    implementation("me.ahoo.cosky:spring-cloud-starter-cosky-config:${coskyVersion}")
    implementation("me.ahoo.cosky:spring-cloud-starter-cosky-discovery:${coskyVersion}")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer:3.0.3")
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
    <cosky.version>1.3.2</cosky.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>me.ahoo.cosky</groupId>
      <artifactId>spring-cloud-starter-cosky-config</artifactId>
      <version>${cosky.version}</version>
    </dependency>
    <dependency>
      <groupId>me.ahoo.cosky</groupId>
      <artifactId>spring-cloud-starter-cosky-discovery</artifactId>
      <version>${cosky.version}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-starter-loadbalancer</artifactId>
      <version>3.0.3</version>
    </dependency>
  </dependencies>

</project>
```

### bootstrap.yaml (Spring-Cloud-Config)

```yaml
spring:
  application:
    name: ${service.name:cosky-rest-api}
  cloud:
    cosky:
      namespace: ${cosky.namespace:cosky-{system}}
      config:
        config-id: ${spring.application.name}.yaml
      redis:
        mode: ${cosky.redis.mode:standalone}
        url: ${cosky.redis.uri:redis://localhost:6379}
logging:
  file:
    name: logs/${spring.application.name}.log
```

## REST-API Server (``Optional``)

### 安装 REST-API Server

#### 方式一：下载可执行文件

> 下载 [rest-api-server](https://github.com/Ahoo-Wang/cosky/releases/download/1.3.2/cosky-rest-api-1.3.2.tar)

> 解压 *cosky-rest-api-1.3.2.tar*

```shell
cd cosky-rest-api-1.3.2
# 工作目录: cosky-rest-api-1.3.2
bin/cosky-rest-api --server.port=8080 --cosky.redis.uri=redis://localhost:6379
```

#### 方式二：在 Docker 中运行

```shell
docker pull ahoowang/cosky-rest-api:1.3.2
docker run --name cosky-rest-api -d -p 8080:8080 --link redis -e COSKY_REDIS_URI=redis://redis:6379  ahoowang/cosky-rest-api:1.3.2
```

#### 方式三：在 Kubernetes 中运行

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cosky-rest-api
  labels:
    app: cosky-rest-api
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cosky-rest-api
  template:
    metadata:
      labels:
        app: cosky-rest-api
    spec:
      containers:
        - env:
            - name: COSKY_REDIS_MODE
              value: standalone
            - name: COSKY_REDIS_URI
              value: redis://redis-uri:6379
          image: ahoowang/cosky-rest-api:1.3.2
          name: cosky-rest-api
          ports:
            - containerPort: 8080
              protocol: TCP
          resources:
            limits:
              cpu: "1"
              memory: 1280Mi
            requests:
              cpu: 250m
              memory: 1024Mi
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
  name: cosky-rest-api
  labels:
    app: cosky-rest-api
spec:
  selector:
    app: cosky-rest-api
  ports:
    - name: rest
      port: 80
      protocol: TCP
      targetPort: 8080
```

### Dashboard

> [http://localhost:8080/dashboard](http://localhost:8080/dashboard)

![dashboard-dashboard](./docs/dashboard-dashboard.png)

### 服务依赖拓扑

![dashboard-topology](./docs/dashboard-topology.png)

### 基于角色的访问控制(RBAC)

- cosky: 保留用户名，超级用户，拥有最高权限。应用首次启动时会初始化超级用户(*cosky*)的密码，并打印在控制台。忘记密码也不用担心，可以通过配置 `enforce-init-super-user: true`，*CoSky* 会帮助你重新初始化密码并打印在控制台。

```log
---------------- ****** CoSky -  init super user:[cosky] password:[6TrmOux4Oj] ****** ----------------
```

- admin: 保留角色，超级管理员角色，拥有所有权限，一个用户可以绑定多个角色，一个角色可以绑定多个资源操作权限。
- 权限控制粒度为命名空间，读写操作

#### 角色权限

![dashboard-role](./docs/dashboard-role.png)

##### 添加角色

![dashboard-role-add](./docs/dashboard-role-add.png)

#### 用户管理

![dashboard-user](./docs/dashboard-user.png)

##### 添加用户

![dashboard-user-add](./docs/dashboard-user-add.png)

#### 审计日志

![dashboard-audit-log](./docs/dashboard-audit-log.png)


#### 命名空间管理

![dashboard-namespace](./docs/dashboard-namespace.png)

#### 配置管理

![dashboard-config](./docs/dashboard-config.png)

##### 编辑配置

![dashboard-config-edit](./docs/dashboard-config-edit.png)

##### 回滚配置

![dashboard-config-rollback](./docs/dashboard-config-rollback.png)

##### 从 Nacos 导入配置

![dashboard-config-import](./docs/dashboard-config-import.gif)

#### 服务管理

![dashboard-service](./docs/dashboard-service.png)

##### 编辑服务实例信息

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
gradle cosky-config:jmh
# or
java -jar cosky-config/build/libs/cosky-config-1.3.2-jmh.jar -bm thrpt -t 25 -wi 1 -rf json -f 1
```

```
Benchmark                                          Mode  Cnt          Score   Error  Units
ConsistencyRedisConfigServiceBenchmark.getConfig  thrpt       256733987.827          ops/s
RedisConfigServiceBenchmark.getConfig             thrpt          241787.679          ops/s
RedisConfigServiceBenchmark.setConfig             thrpt          140461.112          ops/s
```

### ServiceDiscovery

``` shell
gradle cosky-discovery:jmh
# or
java -jar cosky-discovery/build/libs/cosky-discovery-1.3.2-jmh.jar -bm thrpt -t 25 -wi 1 -rf json -f 1
```

```
Benchmark                                                Mode  Cnt          Score   Error  Units
ConsistencyRedisServiceDiscoveryBenchmark.getInstances  thrpt        76621729.048          ops/s
ConsistencyRedisServiceDiscoveryBenchmark.getServices   thrpt       455760632.346          ops/s
RedisServiceDiscoveryBenchmark.getInstances             thrpt          226909.985          ops/s
RedisServiceDiscoveryBenchmark.getServices              thrpt          304979.150          ops/s
RedisServiceRegistryBenchmark.deregister                thrpt          255305.648          ops/s
RedisServiceRegistryBenchmark.register                  thrpt          110664.160          ops/s
RedisServiceRegistryBenchmark.renew                     thrpt          210960.325          ops/s
```
