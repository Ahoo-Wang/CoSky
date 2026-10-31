# cosky-migrate

Moves a CoSky 6.x installation to 8.0 in one cutover (design D33). The two generations cannot see each other, so
applications move together: there is no bridge and no period where both run.

It imports Configs, and reports what cannot be imported. Instances, users and roles do not migrate.

## Build

```bash
./gradlew :migration:installDist
```

The tool is then at `migration/build/install/cosky-migrate/bin/cosky-migrate`, and needs JDK 21 or later.

## Cutover

1. Start the 8.0 server. It may use the same Redis as 6.x if that Redis meets design D31: their keys do not overlap.
2. Rehearse while 6.x still runs, and read the report:

   ```bash
   COSKY_TOKEN=<token> cosky-migrate --source redis://<6.x-redis> --target http://<8.0-server> --dry-run
   ```

   Rename the services it lists, and name any namespace 8.0 cannot accept with `--namespace <6.x>=<8.0>`. The default
   namespace of 6.x, `cosky-{default}`, goes to `default`. A namespace 6.x never listed has to be named this way too.
3. Rebuild the roles and subjects from the report on 8.0.
4. Stop writing Configs to 6.x, and import. `--history` also brings the versions 6.x still holds; 8.0 keeps its newest
   `cosky.config.history-size` of them.

   ```bash
   COSKY_TOKEN=<token> cosky-migrate --source redis://<6.x-redis> --target http://<8.0-server> --history
   ```

5. Move the applications to the 8.0 starter and stop 6.x. Applications register themselves. Fixed instances do not:
   create those from the report.

## Good to know

- Running it again continues an interrupted run and changes nothing else. A Config whose content on 8.0 matches no
  version left in 6.x was edited there, so it stays and is reported. To import it anyway, delete it on 8.0 first.
- A Config removed in 6.x has no current version and does not arrive. Revisions and timestamps start anew on 8.0.
- The format of a Config comes from the extension of its name; anything else is text.
- The report holds names, addresses and permissions, never the content of a Config, a password or a metadata value.
  The tool does not read password hashes at all.
- Exit code 0 means everything arrived, 1 that some Configs need attention, and 2 that it could not run or stopped early.
