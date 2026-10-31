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
| JDK | 17+ | Gradle build (toolchain 17) |
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
- `main` carries 6.x until 8.0 GA. 8.0 work is cut from `v8` and comes back through a pull request into `v8`
  (see [docs/roadmap.md](docs/roadmap.md) §4).
- Commit messages and pull request titles follow [Conventional Commits](https://www.conventionalcommits.org):
  `type(scope)!: summary`, where `type` is one of `feat`, `fix`, `perf`, `docs`, `refactor`, `test`, `build`, `ci`,
  `chore`, and `!` marks a breaking change. The title determines the release notes category.
- Keep a pull request focused on one concern; split unrelated changes.
- Pull requests are squash-merged, keeping `main` linear with one commit per pull request.

## Code review

- `main` is protected: every change goes through a pull request, and code owners
  ([CODEOWNERS](.github/CODEOWNERS)) are requested for review automatically.
- The pull request template lists what reviewers check: tests, documentation and compatibility.
- The build (`Integration Test`: tests, static analysis, license headers, coverage floor) must pass before merging;
  CodeQL and Codecov report on every pull request too.
- Review conversations must be resolved, by a change or an answer, before merging.
- History stays linear: no force pushes to `main`, and pull requests are squash-merged.

## Releases and versioning

CoSky follows [Semantic Versioning](https://semver.org):

- **MAJOR** for incompatible API, configuration or behavior changes (e.g. 6.0.0), with an upgrade guide in the wiki.
- **MINOR** for backward-compatible features.
- **PATCH** for backward-compatible fixes.

To release:

1. Open a pull request titled `Bump version to X.Y.Z` that updates `version` in `gradle.properties`,
   `dashboard/package.json` and the version references in the wiki. Optionally add
   `.github/release-notes/vX.Y.Z.md` with highlights and upgrade notes.
2. When it is merged, the [Release workflow](.github/workflows/release.yml) creates the GitHub release `vX.Y.Z` on
   the merge commit: the optional introduction followed by notes generated from the merged pull requests, grouped by
   their labels ([.github/release.yml](.github/release.yml)).
3. The workflow then publishes the artifacts to Maven Central and GitHub Packages, and the Docker images.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
