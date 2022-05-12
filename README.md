# [CoSky](https://github.com/Ahoo-Wang/CoSky) High-performance, low-cost microservice governance platform (Service Discovery and Configuration Service)

[![License](https://img.shields.io/badge/license-Apache%202-4EB1BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![GitHub release](https://img.shields.io/github/release/Ahoo-Wang/CoSky.svg)](https://github.com/Ahoo-Wang/CoSky/releases)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/9e31358946b645abb283e83b10e85e2d)](https://www.codacy.com/gh/Ahoo-Wang/CoSky/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=Ahoo-Wang/CoSky&amp;utm_campaign=Badge_Grade)
[![codecov](https://codecov.io/gh/Ahoo-Wang/CoSky/branch/main/graph/badge.svg?token=N114DHHBUI)](https://codecov.io/gh/Ahoo-Wang/CoSky)

> [中文文档](https://github.com/Ahoo-Wang/CoSky/blob/main/README.zh-CN.md)

*[CoSky](https://github.com/Ahoo-Wang/CoSky)* is a lightweight, low-cost service registration, service discovery, and configuration service SDK. By using Redis in the existing infrastructure (I believe you have already deployed Redis), it doesn’t need to bring extra to the operation and maintenance deployment. Cost and burden. With the high performance of Redis, *CoSky* provides ultra-high TPS&QPS (100,000+/s [JMH Benchmark](#jmh-benchmark)). *CoSky* combines the process cache strategy + *Redis PubSub* to achieve real-time process cache refresh, with unparalleled QPS performance (70,000,000+/s [JMH Benchmark](#jmh-benchmark)) and real-time consistency
between process cache and Redis.

## Service Discovery

<p align="center">
     <img src="./docs/CoSky-Discovery.png" alt="CoSky-Discovery"/>
</p>

## Configuration

<p align="center">
     <img src="./docs/CoSky-Configuration.png" alt="CoSky-Configuration"/>
</p>

## Examples

[Service Consumer --RPC--> Service Provider Examples](https://github.com/Ahoo-Wang/CoSky/tree/main/examples)

## Installation

### Gradle

> Kotlin DSL

``` kotlin
    val coskyVersion = "lastVersion";
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
        <cosky.version>lastVersion</cosky.version>
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
  redis:
    url: redis://localhost:6379
  cloud:
    cosky:
      namespace: ${cosky.namespace:cosky-{system}}
      config:
        config-id: ${spring.application.name}.yaml
    service-registry:
      auto-registration:
        enabled: ${cosky.auto-registry:true}
logging:
  file:
    name: logs/${spring.application.name}.log
```

## REST-API Server (``Optional``)

### Installation REST-API Server

#### Option 1：Download the executable file

> Download [cosky-rest-api-server](https://github.com/Ahoo-Wang/cosky/releases/download/v2.0.0/cosky-rest-api-2.0.0.tar)

> tar *cosky-rest-api-lastVersion.tar*

```shell
cd cosky-rest-api-lastVersion
# Working directory: cosky-rest-api-lastVersion
bin/cosky-rest-api --server.port=8080 --spring.redis.uri=redis://localhost:6379
```

#### Option 2：Run On Docker

```shell
docker pull ahoowang/cosky-rest-api:lastVersion
docker run --name cosky-rest-api -d -p 8080:8080 --link redis -e SPRING_REDIS_URL=redis://redis:6379  ahoowang/cosky-rest-api:lastVersion
```

#### Option 3：Run On Kubernetes

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
            - name: SPRING_REDIS_URL
              value: redis://redis-uri:6379
          image: ahoowang/cosky-rest-api:lastVersion
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

<p align="center">
     <img src="./docs/dashboard-dashboard.png" alt="CoSky-Dashboard"/>
</p>

### Service dependent topology

<p align="center">
     <img src="./docs/dashboard-topology.png" alt="CoSky-Dashboard-topology"/>
</p>

### Role-based access control(RBAC)

- cosky: Reserved username, super user, with the highest authority. When the application is launched for the first time, the super user (cosky) password will be initialized and printed on the console. Don't worry if you forget your password, you can configure `enforce-init-super-user: true`, *CoSky* will help you reinitialize the password and print it on the console.

```log
---------------- ****** CoSky -  init super user:[cosky] password:[6TrmOux4Oj] ****** ----------------
```

- admin: Reserved roles, super administrator roles, have all permissions, a user can be bound to multiple roles, and a role can be bound to multiple resource operation permissions.
- Permission control granularity is namespace, read and write operations

#### Role Permissions

<p align="center">
     <img src="./docs/dashboard-role.png" alt="CoSky-Dashboard-role"/>
</p>

##### Add Role

<p align="center">
     <img src="./docs/dashboard-role-add.png" alt="CoSky-Dashboard-role-add"/>
</p>

#### User Management

<p align="center">
     <img src="./docs/dashboard-user.png" alt="CoSky-Dashboard-user"/>
</p>

##### Add User

<p align="center">
     <img src="./docs/dashboard-user-add.png" alt="CoSky-Dashboard-user-add"/>
</p>

#### Audit Log

<p align="center">
     <img src="./docs/dashboard-audit-log.png" alt="CoSky-Dashboard-audit-log"/>
</p>

#### Namespace

<p align="center">
     <img src="./docs/dashboard-namespace.png" alt="CoSky-Dashboard-namespace"/>
</p>

#### Config

<p align="center">
     <img src="./docs/dashboard-config.png" alt="CoSky-Dashboard-config"/>
</p>

##### Edit configuration

<p align="center">
     <img src="./docs/dashboard-config-edit.png" alt="CoSky-Dashboard-config-edit"/>
</p>

##### Rollback configuration

<p align="center">
     <img src="./docs/dashboard-config-rollback.png" alt="CoSky-Dashboard-config-rollback"/>
</p>

##### Import configuration from Nacos

<p align="center">
     <img src="./docs/dashboard-config-import.gif" alt="CoSky-Dashboard-config-import"/>
</p>

#### Service

<p align="center">
     <img src="./docs/dashboard-service.png" alt="CoSky-Dashboard-service"/>
</p>

##### Edit Service Instance

<p align="center">
     <img src="./docs/dashboard-service-edit.png" alt="CoSky-Dashboard-service-edit"/>
</p>

### REST-API

> http://localhost:8080/swagger-ui/index.html#/

#### Namespace

<p align="center">
     <img src="./docs/rest-api-namespace.png" alt="rest-api-namespace"/>
</p>

- /v1/namespaces
    - GET
- /v1/namespaces/{namespace}
    - PUT
    - GET
- /v1/namespaces/current
    - GET
- /v1/namespaces/current/{namespace}
    - PUT

#### Config

<p align="center">
     <img src="./docs/rest-api-config.png" alt="rest-api-config"/>
</p>

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

<p align="center">
     <img src="./docs/rest-api-service.png" alt="rest-api-service"/>
</p>

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

- The development notebook : MacBook Pro (M1)
- All benchmark tests are carried out on the development notebook.
- Deploying Redis on the development notebook.

### ConfigService

``` shell
gradle cosky-config:jmh
# or
java -jar cosky-config/build/libs/cosky-config-lastVersion-jmh.jar -bm thrpt -t 25 -wi 1 -rf json -f 1
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
java -jar cosky-discovery/build/libs/cosky-discovery-lastVersion-jmh.jar -bm thrpt -t 25 -wi 1 -rf json -f 1
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

## CoSky-Mirror (Real-time synchronization of service instance change status)

> CoSky-Mirror is like a mirror placed between Nacos and CoSky to build a unified service discovery platform.

<p align="center">
     <img src="./docs/CoSky-Mirror.png" alt="CoSky-Mirror"/>
</p>

<p align="center">
     <img src="./docs/CoSky-Mirror-Unified.png" alt="CoSky-Mirror-Unified"/>
</p>

## Comparison with others
|                                | CoSky            | 	Eureka       | 	Consul           | 	CoreDNS      | 	Zookeeper    | 	Nacos                       | 	Apollo       |
|--------------------------------|------------------|---------------|-------------------|---------------|---------------|------------------------------|---------------|
| CAP                            | CP+AP            | 	AP           | 	CP               | 	CP           | 	CP           | 	CP+AP                       | 	CP+AP        |
| Health Check                   | 	Client Beat     | 	Client Beat	 | TCP/HTTP/gRPC/Cmd | 	Keep Alive   | 	Keep Alive   | 	TCP/HTTP/Client Beat        | 	Client Beat  |
| Load Balancing Strategy        | 	Weight/Selector | 	Ribbon       | 	Fabio	           | 	RoundRobin	  | 	RoundRobin	  | 	Weight/metadata/RoundRobin	 | 	RoundRobin	  |
| Avalanche Protection           | 	N               | 	Y            | 	N	               | 	N	           | 	N	           | 	Y	                          | 	N	           |
| Auto Logoff Instance           | 	Support         | 	Support      | 	Not Support	     | 	Not Support	 | 	Support      | 	Support                     | 	Support	     |
| Access Protocol                | 	HTTP/Redis      | 	HTTP         | 	HTTP/DNS	        | 	DNS	         | 	TCP	         | 	HTTP/DNS	                   | 	HTTP	        |
| Listening Support              | 	Support         | 	Support      | 	Support	         | 	Not Support	 | 	Support	     | 	Support	                    | 	Support	     |
| Multi-data Center              | 	Support         | 	Support      | 	Support	         | 	Not Support	 | 	Not Support	 | 	Support	                    | 	Support	     |
| Cross Registry Synchronization | 	Support         | 	Not Support  | 	Support	         | 	Not Support	 | 	Not Support	 | 	Support	                    | 	Not Support	 |
| SpringCloud Integration        | 	Support         | 	Support      | 	Support	         | 	Not Support	 | 	Not Support	 | 	Support                     | 	Support	     |
| Dubbo Integration              | 	Support         | 	Not Support  | 	Not Support	     | 	Not Support	 | 	Support	     | 	Support	                    | 	Support	     |
| K8S Integration                | 	Support         | 	Not Support  | 	Support	         | 	Support	     | 	Not Support	 | 	Support	                    | 	Not Support	 |
| Persistence                    | 	Redis           | 		            | 		                | 		            |               | 	MySql                       | 	MySql        |
