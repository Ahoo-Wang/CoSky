# AGENTS.md

## Build & Run Commands

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Run specific module tests
./gradlew :cosky-config:test
./gradlew :cosky-discovery:test

# Run REST API server
./gradlew :cosky-rest-api:bootRun

# Run benchmarks (JMH)
./gradlew :cosky-config:jmh
./gradlew :cosky-discovery:jmh

# Publish to Maven Local
./gradlew publishToMavenLocal

# Check code style (Detekt)
./gradlew detekt
```

## Testing

- **Framework**: JUnit 5 with `me.ahoo.test:fluent-assert-core` for assertions
- **Redis**: Integration tests require a running Redis instance (use `AbstractReactiveRedisTest` base class)
- **Test scope**: Each module has its own `src/test/kotlin/` directory
- **Coverage**: Jacoco plugin enabled, reports in `code-coverage-report/`

## Project Structure

| Module | Purpose |
|--------|---------|
| `cosky-core` | Namespace management, Redis key utilities, PubSub event base |
| `cosky-config` | Configuration CRUD, versioning, rollback, consistency caching |
| `cosky-discovery` | Service registry, discovery, load balancing, topology |
| `cosky-spring-cloud-core` | Shared Spring Boot auto-configuration |
| `cosky-spring-cloud-starter-config` | Spring Cloud config loading & refresh |
| `cosky-spring-cloud-starter-discovery` | Spring Cloud service registration & discovery |
| `cosky-rest-api` | REST API server, dashboard, security, RBAC |
| `cosky-bom` | Bill of Materials for dependency management |
| `cosky-dependencies` | Version catalog |
| `cosky-test` | Shared test utilities |
| `dashboard/` | React 19 frontend (separate build — see dashboard/CLAUDE.md) |
| `examples/` | Sample service provider/consumer apps |
| `wiki/` | VitePress documentation site |

## Code Style

- **Language**: Kotlin (JVM 17 toolchain)
- **Style**: Detekt with auto-correct (`./gradlew detekt`)
- **Compiler flags**: `-Xjsr305=strict`, `-Xjvm-default=all-compatibility`
- **Reactive**: All core APIs use Project Reactor (`Mono`/`Flux`)
- **Redis**: All mutations via Lua scripts for atomicity; Spring `ReactiveStringRedisTemplate`
- **Copyright header**: Apache 2.0 license header on all source files

## Git Workflow

- Main branch: `main`
- CI: GitHub Actions (integration test, benchmark, codecov, Docker deploy)

## Boundaries

- ✅ Always add Apache 2.0 license headers to new source files
- ✅ Use Lua scripts for all Redis mutations (never multi-command sequences)
- ✅ Use `fluent-assert-core` (`import me.ahoo.test.asserts.assert`) for test assertions
- ✅ Keep module dependencies acyclic — `cosky-core` has no dependencies on other cosky modules
- ⚠️ Ask before modifying Lua scripts — they enforce critical invariants
- ⚠️ Ask before changing the Redis key schema in `ConfigKeyGenerator` or `DiscoveryKeyGenerator`
- 🚫 Never remove the consistency layer wrappers — they are the performance foundation
