# AGENTS.md

CoSky is being rewritten as **8.0** (no backward compatibility with 6.x): a lightweight control-plane server in front of
Redis. Clients speak only the protocol (gRPC data plane, REST management plane, one `.proto` set) and never touch
Redis.

- Design: `docs/architecture-redesign.md` — the spec. Code follows it; if it cannot, stop and raise it rather than
  deviating silently.
- Roadmap and progress: `docs/roadmap.md`.

## Build & Run Commands

```bash
# Build all modules (CI mode fails on any style violation instead of auto-correcting)
CI=true ./gradlew build

# Run tests of one module
./gradlew :api:test

# Lint the protos (part of check)
./gradlew :api:bufLint

# Generate the OpenAPI document (api/build/openapi/cosky.yaml, part of assemble)
./gradlew :api:generateOpenApi

# Check code style (Detekt), license headers and the coverage floor
./gradlew check
```

## Testing

- **Framework**: JUnit 5 with `me.ahoo.test:fluent-assert-core` for assertions; `kotlinx-coroutines-test` for suspend code
- **Redis**: integration tests use Testcontainers (no local Redis needed)
- **Coverage**: Jacoco aggregated in `code-coverage-report/`; `check` fails below 90% line / 70% branch coverage.
  Generated code (`me.ahoo.cosky.api.v1`) is excluded — never put hand-written code in that package
- **Bug fixes** come with a test that fails without the fix

## Project Structure

Directories have no `cosky-` prefix; published artifacts do (`cosky-${project.name}`).

| Module | Purpose | Status |
|--------|---------|--------|
| `api` | Protos (`cosky.v1`), generated messages and grpc-kotlin stubs, validation, error model, OpenAPI | ✅ |
| `store` | Store SPI and in-memory implementation | planned (W2a) |
| `conformance-test` | Store SPI and protocol conformance suites | planned (W2a) |
| `client` | Coroutine client: List/Watch, leases, local snapshots, load balancing | planned (W2b) |
| `spring-boot-starter` | ConfigData loading, refresh, Spring Cloud discovery and registry | planned (W2b) |
| `server` | gRPC + REST server, Watch Cache, lease reaper, auth, views | planned (W3a) |
| `store-redis` | Redis Store (the only module with Lua) | planned (W3b) |
| `bom` / `dependencies` | Bill of Materials / version platform | ✅ |
| `test` | Shared test utilities | ✅ |
| `buildSrc` | Build logic (OpenAPI generation) | ✅ |
| `dashboard/` | React 19 frontend (separate build — see dashboard/CLAUDE.md); still on the 6.x API until W4b | — |
| `wiki/` | VitePress documentation site; still describes 6.x until W4d | — |

## Code Style

- **Language**: Kotlin (JVM 17 toolchain)
- **Async**: Kotlin coroutines (`suspend` / `Flow` / `StateFlow`) everywhere. Reactor is allowed only in
  `store-redis`, `spring-boot-starter` and `server` `..adapter..` packages — Detekt `ForbiddenImport` enforces it
- **Style**: Detekt + ktlint formatting, 120 character lines; auto-corrects locally, fails CI on any violation
- **Protos**: buf STANDARD rules (enum value prefixes, `Service` suffix); only `RPC_RESPONSE_STANDARD_NAME` is excepted
- **Copyright header**: Apache 2.0 license header on all source files, enforced by detekt

## Git Workflow

- `main` carries 6.x until 8.0 GA; all 8.0 work lands on **`v8`** (see `docs/roadmap.md` §4)
- One roadmap task per branch, cut from the latest `v8`; one pull request per task into `v8`, squash-merged after review
  and a passing CI; review fixes go on the same branch
- Commits and pull request titles: Conventional Commits (`type(scope)!: summary`); the title drives labels and
  release notes
- Releases: Semantic Versioning; 8.0 starts at `8.0.0-alpha.1`

## Boundaries

- ✅ Always add Apache 2.0 license headers to new source files
- ✅ Use `fluent-assert-core` (`import me.ahoo.test.asserts.assert`) for test assertions
- ✅ Keep module dependencies acyclic; `api` depends on no other CoSky module
- ✅ Business failures are typed results or `CoSkyException` with an `ErrorReason`; exceptions are not control flow
- ⚠️ Ask before changing the protos, the Redis key layout (design §5.1) or the Lua scripts — they are the contract
- 🚫 Never let anything but `server` access the store; clients never talk to Redis
