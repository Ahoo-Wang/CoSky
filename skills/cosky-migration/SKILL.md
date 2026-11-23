---
name: cosky-migration
description: Move applications and data from CoSky 5.x/6.x to CoSky 8.0. In 5.x/6.x, applications talk to Redis directly through cosky-spring-cloud-starter-config and cosky-spring-cloud-starter-discovery, with bootstrap.yaml and spring.cloud.cosky.* settings. This skill covers rehearsing and importing Configs with cosky-migrate, mapping namespaces such as {dev} or cosky-{default}, fixing service names, replacing the starters and bootstrap.yaml, moving to Spring Boot 4.2, planning the cutover of services that call each other, and going back. Use it whenever a project depends on me.ahoo.cosky 5.x or 6.x artifacts, or someone wants to upgrade, migrate or move CoSky to 8, even if they only ask about one service or only about the Configs. Not for adopting CoSky for the first time (cosky-integration).
---

# Move from CoSky 5.x/6.x to 8.0

CoSky 8 is a new system: applications reach a server over gRPC with a token, instead of reading and writing Redis themselves. **The two generations cannot see each other's instances, and there is no bridge.** A service on 8.0 cannot call one still on 6.x, and the other way round. So the work has two halves, the data and the applications, and the hard part is the order in which the applications switch.

The steps below hold for 5.x as for 6.x: both keep their data in Redis the same way, and `cosky-migrate` reads both.

## Inventory first

Before changing anything, find out:

- **Each application's CoSky use:**
  - which starters it has;
  - what `bootstrap.yaml` and `spring.cloud.cosky.*` say;
  - whether it reads Configs by name in code through the old SDK;
  - whether it has a web server, since only those register by themselves on 8.0;
  - its `spring.application.name`.
- **Who calls whom.** Services that call each other have to switch together. Build the groups from the code (`@LoadBalanced` clients, Feign interfaces, service names in Configs) or from the 6.x Dashboard's topology while it still runs.
- **The Spring Boot version.** 8.0 needs Spring Boot 4.2.0 and Spring Cloud 2026.0 with JDK 21 or later. For an application on Boot 2.x or 3.x, that upgrade is usually most of the work. Treat it as its own step, and keep it apart from the CoSky change where you can.
- **The old Redis:**
  - its address and whether it is a Redis Cluster;
  - the namespaces in use (`{dev}`, `cosky-{default}`);
  - fixed instances, such as databases registered by hand;
  - users and roles.

## Settings: old to new

| 5.x/6.x | 8.0 |
|---|---|
| `cosky-spring-cloud-starter-config`, `cosky-spring-cloud-starter-discovery` | `cosky-spring-boot-starter`, versions from `me.ahoo.cosky:cosky-bom` |
| `bootstrap.yaml` | `application.yaml`; remove `bootstrap.yaml`, and `spring-cloud-starter-bootstrap` if nothing else needs it |
| `spring.cloud.cosky.namespace: "{dev}"`, or `cosky-{default}` by default | `cosky.namespace: dev`, or `default` |
| `spring.cloud.cosky.config.config-id: order-service.yaml`; unset, it was `<spring.application.name>.yaml` | `spring.config.import: "cosky:order-service.yaml"` |
| `spring.cloud.cosky.config.timeout` | `cosky.config.load-timeout` |
| `spring.cloud.cosky.discovery.registry.weight`, `.metadata` | `cosky.registry.weight`, `cosky.registry.metadata` |
| `.registry.ttl`, `.ephemeral`, `.renew.*` | Gone: each process holds one lease, renewed for it |
| `.registry.port` / `.host` for an application without a web server | It no longer registers by itself. Call the client's `instances.register(...)` where it must be called |
| `ServiceRegistry.setStatus(...)`, `initial-status` | A weight of `0` |
| `spring.data.redis.*` / `spring.redis.*` used only for CoSky | Remove; set `cosky.endpoints` (the server's gRPC address) instead |
| — | `cosky.token-file` or `cosky.token`: every call carries a token now |

Leave alone any Redis settings the application uses for itself. Code that read a Config by name through the old SDK moves to the `CoSkyClient` bean: `client.namespace("dev").configs.load("dictionary.json")` reads it once, and `.watch(...)` follows changes.

The new settings, token included, are what `cosky-integration` describes; follow it for each application.

## The data: cosky-migrate

Each [release](https://github.com/Ahoo-Wang/CoSky/releases) carries `cosky-migrate` as a zip; run `bin/cosky-migrate` with JDK 21 or later. It moves Configs, with their history if asked. Instances that register themselves do so on 8.0 when their application switches. Fixed instances, users and roles are listed in the report, to be made by hand.

1. **Start the 8.0 server.** It may share the old Redis if that is Redis 7.0 or later with `maxmemory-policy noeviction`: the keys do not overlap.
2. **Rehearse while the old system still runs.** It reads both sides and writes nothing:

   ```bash
   export COSKY_TOKEN=<an admin token of the 8.0 server>
   export COSKY_SOURCE_URI='redis://:<password>@old-redis:6379'   # keeps the password out of ps
   cosky-migrate --target http://cosky:8080 --dry-run
   ```

   Add `--cluster` for a Redis Cluster, with seed nodes separated by commas. Then read `cosky-migration-report.md` with the user:
   - **Service names to change.** `OrderService` cannot register on 8.0; change `spring.application.name` to something like `order-service`, and every caller that names it.
   - **Namespaces.** `cosky-{default}` maps to `default` and `{dev}` to `dev` by themselves. Name others with `--namespace 'Old_Name=new-name'`, or leave one out with `--namespace 'Old_Name='`.
   - **Configs 8.0 refuses,** such as a name holding `:` or content over 1 MiB.
   - **Configs without an extension** are taken as YAML, as 6.x assumed. `--default-format` says otherwise.
3. **Accounts.** Each 8.0 namespace comes with an account of its own name, bound to `client` there. Its applications share one token, so create one token per namespace, not one per service: the Dashboard's **Connect** on the namespace's row does it. Rebuild people's access from the report's users and roles, as OIDC groups or ServiceAccounts.
4. **Import, once Configs stop changing on the old side:**

   ```bash
   cosky-migrate --target http://cosky:8080 --history
   ```

   Exit `0`: everything arrived. `1`: some Configs need attention, listed in the report. `2`: it could not run or stopped early. Running it again continues an interrupted run and changes nothing else. It never overwrites a Config edited on 8.0, and never writes to the old Redis.

## The cutover

Order it so that no call crosses generations:

1. **Prepare every application** on a branch: Spring Boot 4.2, the new starter, the settings above, and the token from a Secret.
2. **Freeze Config edits on the old side, then import.** From the moment the first service switches, two copies of each Config exist. Services on 8.0 read the imported one, and services still on the old system read Redis. An edit to one never reaches the other.
3. **Switch a whole group of services that call each other at once,** or the whole environment if the groups are tangled. Each group's callers and callees go in the same deployment window.
4. **Recreate fixed instances** from the report once their callers are on 8.0: nothing registers them again.
5. **Check each group:**
   - its services show up as **Leased** in the 8.0 Dashboard;
   - calls by service name work;
   - a Config change reaches a `@RefreshScope` bean.
6. **Stop the old system** when nothing reads it any more.

**Going back:** until the old system is stopped, redeploying a group's previous build returns it to Redis, which nothing has written to. Configs edited on 8.0 after the switch stay on 8.0, so carry those edits back by hand if needed.

## What to hand back

- The inventory: the services, their call groups and the Boot upgrade each needs.
- The report's findings and the decision for each.
- The changed files for each application.
- The commands that were run, and those still to run.
- A cutover order with its rollback.

Say plainly which steps were rehearsed and which only planned.
