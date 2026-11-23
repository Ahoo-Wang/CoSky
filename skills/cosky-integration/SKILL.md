---
name: cosky-integration
description: Connect a Spring Boot (or plain Kotlin/Java) application to a CoSky 8 server — starter and BOM on Spring Boot 4.2 and Spring Cloud 2026.0, the cosky.* settings in application.yaml, importing Configs with spring.config.import and live refresh, registering and finding services through Spring Cloud LoadBalancer, and giving the application its token (the namespace's shared account, cosky.token-file, a Kubernetes Secret). Use it whenever someone adds CoSky to a service, wires cosky.endpoints / cosky.namespace / cosky.token, asks how an application should authenticate to CoSky, or an application fails to start against CoSky with UNAUTHENTICATED, PERMISSION_DENIED or a missing namespace. Not for moving a 5.x/6.x application or its data (cosky-migration), and not for running the CoSky server itself.
---

# Connect an application to CoSky 8

CoSky 8 is a server: applications reach it over gRPC (port 9090 by default) with a token on every call, and never touch its Redis. Make the smallest change that fits the target application, and keep its build tool, dependency-management style, web stack and configuration layout.

## Inspect first

Find out, from the project and not from memory:

- **Old CoSky.** `cosky-spring-cloud-starter-config`, `cosky-spring-cloud-starter-discovery`, `bootstrap.yaml` or `spring.cloud.cosky.*` mean a 5.x/6.x application: that is a migration, so use `cosky-migration`.
- **Versions.** The starter is built and tested on Spring Boot **4.2.0** and Spring Cloud **2026.0**, with JDK 21 or later. An application on Spring Boot 3.x has to move to 4.2 first; say so rather than mixing versions, since an older Boot manages an older gRPC and fails at runtime. Take the CoSky version from the project's BOM, version catalog or lockfile, or from the latest [release](https://github.com/Ahoo-Wang/CoSky/releases). Never copy one from this skill.
- **Web stack.** Spring MVC or WebFlux, and whether the application has a web server at all: only an application with one registers by itself.
- **Where it runs and which namespace.** The server's gRPC address as the application reaches it, and the namespace. Only `default` exists on a fresh server; any other must be created first.
- **How the token arrives.** Kubernetes Secret, environment, or a file on disk.

## Dependencies

```kotlin
dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.2.0"))
    implementation(platform("org.springframework.cloud:spring-cloud-dependencies:2026.0.0"))
    implementation(platform("me.ahoo.cosky:cosky-bom:<version>"))
    implementation("me.ahoo.cosky:cosky-spring-boot-starter")
    implementation("org.springframework.cloud:spring-cloud-context")              // live refresh of Configs
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer") // calling services by name
}
```

In Maven, import the same three BOMs in `<dependencyManagement>` and add the same artifacts without versions. Add `spring-cloud-context` only if Configs should refresh live, and the load balancer only if the application calls other services. A project already on Spring Boot's Gradle plugin or parent keeps that and adds the two other BOMs. In Kotlin, a bean in `@RefreshScope` must be open: the `kotlin("plugin.spring")` plugin does that.

## application.yaml

```yaml
spring:
  application:
    name: order-service              # the service name it registers as
  config:
    import: "optional:cosky:order-service.yaml"
cosky:
  endpoints: cosky.internal:9090     # gRPC, not the REST port 8080; several are tried in turn
  namespace: prod
  token-file: /etc/cosky/token       # or token: ${COSKY_TOKEN}
```

- **Keep `cosky.*` in the file that imports.** An import connects with what is known of `cosky.*` when its file is read. Settings in `application-prod.yaml` do not reach an import in `application.yaml`, so put them in the same file or in the environment (`COSKY_ENDPOINTS`, `COSKY_NAMESPACE`, …).
- **`optional:` or not.** Without it, the start fails when the Config cannot be loaded, which is right for a Config the service cannot run without. With it, the application starts without the Config and picks it up once it exists.
- **Several Configs:** `"optional:cosky:order-service.yaml,cosky:shared/datasource.yaml"`. YAML and JSON Configs become properties; a TEXT Config becomes one property named after it.
- **Service names** are DNS-style: lowercase letters, digits, `-` and `.`, up to 63 characters. `OrderService` is refused.

Every starter setting, with defaults, is in [Configuration Properties](https://cosky.ahoo.me/guide/properties#spring-boot-starter).

## The token

With auth on, which is the default, an application without a token, or with one its roles do not allow, stops at start and says which. Pick the lightest option that meets the need:

| Need | Use |
|---|---|
| The applications of a namespace, the usual case | **The namespace's account.** Every namespace comes with a ServiceAccount of its own name, bound to `client` there: reads Configs, reads and writes Instances, in that namespace only. All its applications share one token. |
| Telling applications apart, revoking one alone, or keeping a service from registering other names | A ServiceAccount named like the service, bound to the built-in role `service`: reads like `client`, and writes only the Instances of its own service. |
| Less still, such as only its own Configs | A role of your own. See [Security](https://cosky.ahoo.me/guide/security#a-token-for-an-application). |

An admin creates the token once, and it is shown once. Either open the Dashboard's **Namespaces** page and click **Connect** on the namespace's row, then **Create token**; or run, with an admin token in `COSKY_TOKEN`:

```bash
curl -X POST http://<server>:8080/v1/serviceaccounts/<namespace>/tokens \
  -H "Authorization: Bearer $COSKY_TOKEN" -H 'Content-Type: application/json' -d '{}'
```

An account holds at most two tokens, which is what makes rotation possible: add the new one, switch, then revoke the old one.

**Prefer `cosky.token-file` with a mounted Secret.** The file is read again whenever it changes, so a rotated token goes out from the next call on, without a restart:

```bash
kubectl create secret generic cosky-<namespace> --from-literal=token='<the token>'
```

```yaml
# in the Deployment's pod template
containers:
  - name: order-service
    volumeMounts:
      - name: cosky-token
        mountPath: /etc/cosky
        readOnly: true
volumes:
  - name: cosky-token
    secret:
      secretName: cosky-<namespace>
```

Mount the Secret as a directory. A `subPath` mount never sees the Secret change. `cosky.token: ${COSKY_TOKEN}` from a `secretKeyRef` works too, but then rotating means a restart. Set one of `token` and `token-file`, never both: the starter refuses that.

Never put a token in Git, in a Config, or in a message. Hand it over as a Secret, or tell the user the command that creates one.

## Configs and refresh

With `spring-cloud-context` on the classpath, a change to an imported Config refreshes `@RefreshScope` beans and `@ConfigurationProperties` beans without a restart. This also covers a Config that did not exist at start. `cosky.config.refresh.enabled: false` turns it off.

To read a Config by name in code rather than as properties, inject the `CoSkyClient` bean the starter provides: `client.namespace("prod").configs.load("dictionary.json")` reads it once, and `.watch(...)` follows changes.

Config content is not encrypted on the server or in the snapshots clients keep under `~/.cosky`. Use `cosky.snapshot.include-config: false` where that matters, and keep real secrets elsewhere.

## Registering and finding services

- An application with a web server registers once the server is ready, at the pod's address and port, and removes itself on shutdown. A separate management port is not registered.
- An application **without** a web server does not register by itself. It can still read Configs and find services; when it must be called, register it with the client's `instances.register(...)`.
- Call other services by name through a `@LoadBalanced` `WebClient.Builder` or `RestClient.Builder`, or OpenFeign: `http://payment-service/...`.
- `cosky.registry.weight: 0` keeps an instance registered but out of rotation.
- `spring.cloud.service-registry.auto-registration.enabled: false` stops registering.

Without Spring, `me.ahoo.cosky:cosky-client` does the same from Kotlin or Java: `CoSkyClient(CoSkyClientOptions(listOf("host:9090")) { tokenFile = Path.of("/etc/cosky/token") })`, then `client.namespace("prod")`. See [Using the SDK](https://cosky.ahoo.me/guide/sdk).

## Check it works

Do not call it done until these hold, or until you have told the user which you could not check:

1. The application starts and logs no CoSky error.
2. The token belongs to the account you meant: `curl -H "Authorization: Bearer <token>" http://<server>:8080/v1/me` names it.
3. The service shows up under **Service** in the Dashboard, marked **Leased**.
4. Changing an imported Config reaches a `@RefreshScope` bean without a restart.

| Symptom | Usual cause |
|---|---|
| `UNAUTHENTICATED` at start | No token, the `token-file` path wrong or empty, or the token revoked |
| `PERMISSION_DENIED` at start | The token belongs to another namespace's account; `cosky.namespace` differs from the namespace the account is for; or a `service`-role account registers under another name |
| Namespace not found | The namespace was never created. Create it: `PUT /v1/namespaces/<name>` with `{"namespace":{}}` |
| A call by service name fails with 503, on a Mac | The JDK took the macOS system proxy, and Spring Boot 4.2's `WebClient` follows it. Start with `-Dhttp.nonProxyHosts='*'`, or hosts the client can read such as `10.*\|*.internal` |
| `MacOSDnsServerAddressStreamProvider` error on Apple silicon | Add `runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-aarch_64")` |
| Instances of 5.x/6.x services are invisible | They live in Redis, not in the 8.0 server. Use `cosky-migration`. |

## What to hand back

The changed build file and configuration, how the token reaches the application (which account, which Secret, who creates it), and what you verified and what you could not.
