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

# Run REST API server (requires a JWT secret)
COSKY_SECURITY_KEY=$(openssl rand -base64 32) ./gradlew :cosky-rest-api:run

# Run benchmarks (JMH)
./gradlew :cosky-config:jmh
./gradlew :cosky-discovery:jmh

# Publish to Maven Local
./gradlew publishToMavenLocal

# Check code style (Detekt), license headers and the coverage floor
./gradlew check
```

## Testing

- **Framework**: JUnit 5 with `me.ahoo.test:fluent-assert-core` for assertions
- **Redis**: Integration tests require a running Redis instance (use `AbstractReactiveRedisTest` base class)
- **Test scope**: Each module has its own `src/test/kotlin/` directory
- **Coverage**: Jacoco aggregated in `code-coverage-report/`; `check` fails below 90% line / 70% branch coverage
- **REST API**: `RestApiIntegrationTest` drives the HTTP API end to end (JWT, RBAC, audit log)
- **Bug fixes** come with a test that fails without the fix

## Project Structure

| Module | Purpose |
|--------|---------|
| `cosky-core` | Namespace model and validation, Redis key utilities, PubSub event base, `ReactiveWatchCache` (the consistency cache primitive) |
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
- **Style**: Detekt + ktlint formatting, 120 character lines; auto-corrects locally, fails CI on any violation
- **Compiler flags**: `-Xjsr305=strict`, `-Xjvm-default=all-compatibility`
- **Reactive**: All core APIs use Project Reactor (`Mono`/`Flux`)
- **Redis**: All mutations via Lua scripts for atomicity; Spring `ReactiveStringRedisTemplate`
- **Copyright header**: Apache 2.0 license header on all source files, enforced by detekt

## Git Workflow

- This branch, `v6`, maintains 6.x and takes bug fixes only; development is on `main`
- Short-lived branches, squash-merged pull requests into `v6` (see CONTRIBUTING.md)
- Commits and pull request titles: Conventional Commits (`type(scope)!: summary`); the title drives labels and
  release notes
- CI: GitHub Actions (build & tests, REST API smoke test, CodeQL, codecov, benchmark, labeler, Docker deploy)
- Releases: Semantic Versioning; a `Bump version to X.Y.Z` pull request, then a GitHub release

## Boundaries

- ✅ Always add Apache 2.0 license headers to new source files
- ✅ Use Lua scripts for all Redis mutations (never multi-command sequences)
- ✅ Use `fluent-assert-core` (`import me.ahoo.test.asserts.assert`) for test assertions
- ✅ When behavior, a property or an endpoint changes, update the wiki page that owns it (see `wiki/AGENTS.md`), in English and Chinese
- ✅ Keep module dependencies acyclic — `cosky-core` has no dependencies on other cosky modules
- ⚠️ Ask before modifying Lua scripts — they enforce critical invariants
- ⚠️ Ask before changing the Redis key schema in `ConfigKeyGenerator` or `DiscoveryKeyGenerator`
- 🚫 Never remove the consistency layer wrappers — they are the performance foundation
