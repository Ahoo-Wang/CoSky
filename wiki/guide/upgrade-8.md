---
title: Moving from 6.x
description: Move a CoSky 6.x installation to 8.0 in one cutover, with cosky-migrate.
---

# Moving from 6.x

CoSky 8.0 is a new system, and nothing from 6.x carries over by itself. Applications on 6.x talk to Redis directly, and applications on 8.0 talk to the server, so **neither generation can see the other's instances**. There is no bridge: applications move together, in one cutover.

If some services must call each other across generations during a move, run the two installations side by side and switch traffic between whole environments.

## What changes

| | 6.x | 8.0 |
|---|---|---|
| Applications reach | Redis, directly | The server, over gRPC |
| Authentication of applications | None | A token on every call |
| Starters | `cosky-spring-cloud-starter-config`, `cosky-spring-cloud-starter-discovery` | `cosky-spring-boot-starter` |
| Loading configuration | `bootstrap.yaml` | `spring.config.import` |
| Settings | `spring.cloud.cosky.*`, `spring.data.redis.*` | `cosky.*` |
| Namespaces | Any name, often `{dev}` or `cosky-{default}` | DNS labels, such as `dev` or `default` |
| Service names | Anything | DNS-style names, lowercase |
| Config formats | Guessed from the extension | Declared with the Config |
| Staying alive | Each instance's own TTL | One lease per process |
| People | Users with passwords | OIDC, or ServiceAccount tokens |
| Permissions | Read or write per namespace | Roles down to kinds, verbs and name prefixes |
| Audit | A page in the Dashboard | JSON lines in the log |
| Redis | Any version | 7.0 or later, `noeviction` |
| JDK | 17 | 21 for clients, 25 for the server |

## What moves

| | How |
|---|---|
| Configs, with their history if you like | `cosky-migrate` |
| Instances that register themselves | They do so on 8.0 when they switch |
| Fixed instances | By hand, from the report |
| Users and roles | By hand, from the report |
| Audit log and topology | They do not. The audit log stays in 6.x's Redis, and topology rebuilds itself. |

Revisions and timestamps start anew. A Config removed in 6.x has no current version, and does not arrive.

## Building the tool

```bash
./gradlew :migration:installDist
```

The tool is then at `migration/build/install/cosky-migrate/bin/cosky-migrate`, and needs JDK 21 or later.

## The cutover

**1. Start 8.0.** It may share the Redis of 6.x if that Redis meets the [requirements](./requirements.md): their keys do not overlap.

**2. Rehearse while 6.x still runs.** This reads both sides, writes nothing, and writes a report:

```bash
export COSKY_TOKEN=<bootstrap token>
cosky-migrate --source redis://6x-redis:6379 --target http://cosky:8080 --dry-run
```

Read `cosky-migration-report.md`:

- **Service names to change.** `OrderService` cannot register on 8.0, so change `spring.application.name`.
- **Namespaces 8.0 cannot name.** `cosky-{default}` goes to `default` and `{dev}` to `dev`, while anything else illegal stops the run. Name it with `--namespace 'Old_Name=new-name'`, or leave it out with `--namespace 'Old_Name='`. A namespace 6.x never listed must be named this way too.
- **Configs 8.0 refuses**, such as a name holding `:` or content over 1 MiB.

**3. Rebuild people and permissions.** The report lists every 6.x role with the 8.0 rule each grant corresponds to, and every user with their roles. Create those roles and bindings, and a ServiceAccount with a token for each application. An application without a token cannot do anything on a server with auth on.

**4. Prepare the applications.** Replace the starters, move `bootstrap.yaml` into `application.yaml`, and set the token. On Spring Boot 4.1, also set `spring.cloud.compatibility-verifier.enabled: false` until Spring Cloud 2026.0:

```yaml
spring:
  application:
    name: order-service
  config:
    import: "cosky:order-service.yaml"
cosky:
  endpoints: cosky.internal:9090
  token: ${COSKY_TOKEN}
  namespace: prod
```

**5. Stop writing Configs to 6.x, and import.**

```bash
cosky-migrate --source redis://6x-redis:6379 --target http://cosky:8080 --history
```

Exit code `0` means everything arrived, `1` that some Configs need attention, and `2` that it could not run or stopped early. Running it again continues an interrupted run, and changes nothing else.

**6. Switch.** Deploy the applications on 8.0 and stop 6.x. Then create the fixed instances from the report: nothing renews them, so nothing registers them again.

## What the tool will not do

- **Overwrite an edit.** A Config whose content on 8.0 matches no version left in 6.x was edited there, so it stays and is reported. To import it anyway, delete it on 8.0 first.
- **Touch 6.x.** It only reads, and never reads a password hash.
- **Copy secrets into the report.** It holds names, addresses and permissions, never the content of a Config or a metadata value.

For a Redis password, use `COSKY_SOURCE_URI` rather than `--source`, where `ps` cannot see it. Run `cosky-migrate --help` for everything else.

## Going back

Until you stop 6.x, going back is deploying the applications on 6.x again. Nothing writes to its keys, but Configs edited on 8.0 after the switch stay on 8.0.
