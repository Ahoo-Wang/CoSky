# Contributing to CoSky

Thanks for helping improve CoSky. This guide describes how changes flow from an idea to a release.

## Reporting bugs and requesting features

- Search the [existing issues](https://github.com/Ahoo-Wang/CoSky/issues) first.
- Open a new issue with the **Bug report** or **Feature request** template. A bug report needs the CoSky, Redis and
  Java versions, the steps to reproduce, and the expected and actual behavior.
- Security vulnerabilities must not be reported in public issues: follow [SECURITY.md](SECURITY.md).

Issues are triaged with labels: a type (`bug`, `enhancement`, `question`, …) and, once understood, the affected area
(`core`, `config`, `discovery`, `rest-api`, `dashboard`, `documentation`). A pull request that resolves an issue
links it with `Fixes #<number>`, so the issue closes when the change is merged.

## Development setup

| Tool | Version | Used for |
|---|---|---|
| JDK | 21 and 25 | Gradle build (toolchain 21); the server's tests run on 25 |
| Docker | Any recent version | Integration tests (Testcontainers) |
| Node.js + pnpm | Node 22.12+, pnpm 10+ | `dashboard/` and `wiki/` only |

```bash
./gradlew check                 # compile, test, detekt, license headers, coverage floor
./gradlew :<module>:test        # one module
```

## Coding conventions

- Kotlin, following the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html), with a
  120 character line limit.
- [detekt](https://detekt.dev) (including ktlint formatting) enforces the conventions. Locally `./gradlew detekt`
  fixes formatting for you; in CI any violation fails the build. Rules turned off in
  [config/detekt/detekt.yml](config/detekt/detekt.yml) state why.
- Every source file starts with the Apache 2.0 license header ([template](config/detekt/license.template)),
  checked by detekt.
- Redis mutations go through Lua scripts so that they are atomic.

## Tests

- **Unit tests** cover a class in isolation, with [MockK](https://mockk.io) for collaborators.
- **Integration tests** run against a real Redis through [Testcontainers](https://testcontainers.com), so no local
  Redis is needed.
- Every bug fix comes with a test that fails without the fix.
- `./gradlew check` fails when aggregated coverage drops below 90% of lines or 70% of branches. On pull requests,
  Codecov additionally requires 80% coverage of the changed lines and no drop of the project coverage.

## Branches and commits

- `main` is always releasable. Work happens on short-lived branches created from `main`, e.g. `fix/renew-metadata`
  or `feat/config-search`, and comes back through a pull request.
- `v6` maintains 6.x and takes bug fixes only. Cut the branch from `v6` and open the pull request against `v6`; if the
  bug also exists on `main`, fix it there too. Features, refactoring and routine dependency upgrades go to `main` only.
- Commit messages and pull request titles follow [Conventional Commits](https://www.conventionalcommits.org):
  `type(scope)!: summary`, where `type` is one of `feat`, `fix`, `perf`, `docs`, `refactor`, `test`, `build`, `ci`,
  `chore`, and `!` marks a breaking change. The title determines the release notes category.
- Keep a pull request focused on one concern; split unrelated changes.
- Pull requests are squash-merged, keeping `main` linear with one commit per pull request.

## Code review

- `main` and `v6` are protected: every change goes through a pull request, and code owners
  ([CODEOWNERS](.github/CODEOWNERS)) are requested for review automatically.
- The pull request template lists what reviewers check: tests, documentation and compatibility.
- The build (`Integration Test`: tests, static analysis, license headers, coverage floor), `Redis Compatibility` and
  the image test (`Push Docker image`) must pass before merging; CodeQL and Codecov report on every pull request too.
- Review conversations must be resolved, by a change or an answer, before merging.
- History stays linear: no force pushes to `main` or `v6`, and pull requests are squash-merged.

## Releases and versioning

CoSky follows [Semantic Versioning](https://semver.org):

- **MAJOR** for incompatible API, configuration or behavior changes (e.g. 6.0.0), with an upgrade guide in the wiki.
- **MINOR** for backward-compatible features.
- **PATCH** for backward-compatible fixes.

From `8.0.0-rc.1` to `8.0.0`, these only grow: the gRPC API, REST, the client's and the starter's API and properties,
the server's properties, the documented metrics and the data in Redis. A field, call or property may be added; none is
removed, renumbered, renamed or given another meaning. Before the release candidate there is no compatibility promise:
an alpha or a beta may change any of them, and its release notes say how to upgrade.

In `cosky-api` that API is the generated types, `CoSkyException` and `ErrorCodes`. What `cosky-api` marks
`@InternalCoSkyApi` is shared by CoSky's own modules and may change in any release, as may the Kotlin API of
`cosky-server`, `cosky-store`, `cosky-store-redis`, `cosky-migration`, `cosky-test` and `cosky-conformance-test`.

Four CI jobs watch what only grows. During the betas they report, and from the release candidate they are required
checks:

- **Proto Compatibility** checks the protos against a pull request's base with `./gradlew :api:bufBreaking`, by the
  rules in `api/src/main/proto/buf.yaml`.
- **ABI Compatibility** runs `./gradlew checkKotlinAbi` on the Kotlin API of `cosky-api`, `cosky-client` and
  `cosky-spring-boot-starter`, which each module keeps in `api/<module>.api`. A pull request that changes that API runs
  `./gradlew updateKotlinAbi` and commits the result, so reviewers see the change. It also fails when a line of the
  base's dumps is gone (`.github/scripts/api-only-grows.sh`), which updating them cannot get past.
- **REST Compatibility** compares the OpenAPI documents generated from the base and from the pull request with
  `oasdiff breaking` (`.github/scripts/rest-only-grows.sh`): routes, parameters, headers, status codes and schemas.
- **Properties and Metrics Compatibility** fails when a property of the configuration metadata the server and the
  starter ship, or a metric of the table in `wiki/guide/deployment.md` with its labels, is gone
  (`.github/scripts/names-only-grow.sh`). `ConfigurationMetadataTest` holds the metadata to the code and
  `MetricsReferenceTest` holds that table to the server, so a pull request that adds one adds it there too.

All four decide by the base's version: they only warn while it is an alpha or a beta, or when the pull request raises
the major version, and fail from the release candidate on.

To release:

1. Open a pull request titled `Bump version to X.Y.Z` that updates `version` in `gradle.properties`,
   `dashboard/package.json` and the version references in the wiki. Optionally add
   `.github/release-notes/vX.Y.Z.md` with highlights and upgrade notes.
2. When it is merged, the [Release workflow](.github/workflows/release.yml) creates the GitHub release `vX.Y.Z` on
   the merge commit: the optional introduction followed by notes generated from the merged pull requests, grouped by
   their labels ([.github/release.yml](.github/release.yml)).
3. The workflow then publishes the artifacts to Maven Central and GitHub Packages, attaches the `cosky-migrate` zip to
   the release, and publishes the Docker images.

If the Release workflow stops partway, run it again: it creates the release if it is missing, and starts each
publishing workflow that has not run on the tag. A publishing workflow that failed is not started again, since part of
the release may already be out; the Release workflow fails and names the run whose failed jobs to re-run. Re-running
the failed jobs of the [Packages Deploy workflow](.github/workflows/package-deploy.yml) finishes the release: GitHub
Packages receives only the files it does not have yet, the `cosky-migrate` zip is attached again, and Maven Central
receives the whole release in a new staging repository.

A 6.x bug-fix release works the same way, with the pull request against `v6`.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
