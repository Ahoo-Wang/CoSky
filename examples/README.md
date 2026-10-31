# CoSky examples

A provider and a consumer on Spring Boot, using `spring-boot-starter`. The provider takes its greeting from a Config, and
registers itself. The consumer finds it through Spring Cloud LoadBalancer. `e2e` runs all of this as separate processes,
and is the test that these instructions stay true.

## Run them

Start a server, here without Redis or sign-in:

```bash
./gradlew :server:run --args='--dev'
```

Give the provider its Config:

```bash
curl -X PUT localhost:8080/v1/namespaces/default/configs/provider.yaml \
  -H 'Content-Type: application/json' \
  -d '{"config":{"format":"CONFIG_FORMAT_YAML","content":"example:\n  greeting: Bonjour"}}'
```

Start the provider and the consumer, each in a terminal of its own:

```bash
./gradlew :examples:provider:run
```

```bash
./gradlew :examples:consumer:run
```

Call the consumer, which calls the provider:

```bash
curl localhost:8082/call/World
```

Change the Config with the first `curl`, and the next call uses the new greeting without a restart. See who calls whom:

```bash
curl localhost:8080/v1/namespaces/default/views/topology
```

## Where to look

- `provider/src/main/resources/application.yaml`: importing a Config with `spring.config.import`.
- `provider/.../HelloController.kt`: `@RefreshScope` picks up a changed Config.
- `consumer/.../ConsumerApplication.kt`: `@LoadBalanced` finds instances by service name.
