---
title: Backup and Restore
description: What CoSky keeps in Redis, how to set Redis up to keep it, how to back it up and restore it, and what a restore means for applications.
---

# Backup and Restore

This page is for operators. The server keeps nothing on disk: everything that lasts is in Redis. Here is what is there, how to make Redis keep it, and how to get it back.

## What is in Redis

All of CoSky's keys begin with `cosky:`, and each namespace's keys share the hash tag `cosky:{<namespace>}:`, so that a namespace stays in one slot. The design document has [the layout](https://github.com/Ahoo-Wang/CoSky/blob/main/docs/architecture-redesign.md).

| What | Notes |
|---|---|
| Every object, with its revision, its times and who last changed it | Configs and Instances in their namespace. Namespaces, ServiceAccounts, Roles and RoleBindings in `_system`. |
| The last 20 revisions of each Config | Its history, for rollback |
| Each namespace's change log | Up to `cosky.watch.window` entries and `cosky.watch.window-bytes`. Clients resume from it. |
| Each namespace's revision and epoch | What a client's position means, as [Architecture](./architecture.md#when-redis-loses-writes) explains |
| Leases, with their expiry, TTL and owner | The instances attached to one go when it expires |
| The topology | Written again by every replica every 30 seconds |
| The reaper's lock | Held for seconds |

Tokens are kept as their SHA-256 only, so a backup holds none that could be used. It does hold Config contents as they are, unencrypted: protect backups as you protect Redis.

Three things are not in Redis: the [audit log](./security.md#audit), which is in the server's log; the bootstrap token, which is in your environment or Secret; and the clients' [snapshots](./sdk.md#snapshots), on their own disks.

## What Redis must do

- **`maxmemory-policy noeviction`.** The server refuses to start without it, as [Deployment](./deployment.md#redis) says.
- **AOF: `appendonly yes`.** Its default `appendfsync everysec` loses at most about the last second of writes when Redis crashes. Without AOF, a restart takes Redis back to its last RDB snapshot, which with the default save points can be an hour old, and loses everything written since.
- **A disk that outlives the container**, for Redis's data directory.
- **`min-replicas-to-write 1`**, with `min-replicas-max-lag 10`, on a primary with replicas. Replication is asynchronous, so a failover can lose the writes that had not reached the replica. These narrow that window. The cost is that the primary refuses writes while no replica is connected and caught up: CoSky then answers `STORE_UNAVAILABLE`, while reads and running applications go on.

Redis starts with `noeviction` by default. In Docker, a volume and one flag give the rest:

```bash
docker run -d --name redis --network cosky -v cosky-redis:/data redis:7.4 --appendonly yes
```

A managed Redis has these as settings of its own. Check that persistence is on and that the eviction policy is `noeviction`.

## When Redis loses data

### When a failover loses the last writes

A Config written just before a failover may be gone, and so may a token revocation or a change of a role. CoSky notices that the revision went back: `cosky_revision_regression_total` rises, and a replica logs an error that names the namespace and the revision it went back to. Every replica then loads the namespace again, every client starts over from what Redis has, and applications register again what is missing, as [Architecture](./architecture.md#when-redis-loses-writes) explains.

What was lost stays lost, and the Config's history lost it too. The audit log still has each acknowledged write, with its namespace, name and revision: the writes to that namespace above the revision it went back to are the ones to make again.

### When Redis loses everything

If Redis loses everything, the server sets it up again by itself within about 30 seconds, as on a first start: the system namespace, `default`, and the admin from the bootstrap token. It logs the error `The system namespace has no state: the store lost its data.`

So once you have revoked the bootstrap token, as [Security](./security.md#the-first-token) advises, set the variable or Secret to a new token nobody has used and keep that as a break-glass, or remove it: the revoked one would otherwise become the admin again.

The rest is gone: other namespaces, Configs, Roles, RoleBindings, and ServiceAccounts with their tokens. Restore a backup, or create them again and import a [zip export](#configs-as-a-zip-file) of your Configs. With auth on, applications keep serving what they know and stay unregistered until they have a new token. With auth off, they follow what is there once their namespace exists, and register themselves.

## Backing up

### Everything, as an RDB snapshot

An RDB snapshot is everything in Redis at one moment. Have Redis write one, check that it is done, and copy `dump.rdb` from its data directory:

```bash
redis-cli BGSAVE
redis-cli LASTSAVE   # changes once the snapshot is written
```

A Redis Cluster has one per master: back up every master. A managed Redis takes snapshots of its own.

### Configs as a zip file

The [export](./configuration.md#import-and-export) of a namespace holds each Config's name and content. It holds neither its format, labels nor history, nor anything but Configs. It moves between installations and versions, and is readable:

```bash
curl -o prod.zip -H "Authorization: Bearer $TOKEN" https://cosky.example.com/v1/namespaces/prod/configs:export
```

It holds the Configs the token may read, so use one that may read them all. Imported where it does not exist yet, a Config takes the format its extension says: `.yaml` or `.yml`, `.properties`, `.json`, and text for any other. One that exists keeps its format and labels, and takes the new content.

## Restoring

### From an RDB snapshot

Keep at least one server running while you restore. It remembers where each namespace was, and notices that the revision went back.

With AOF on, Redis loads its AOF at start and ignores `dump.rdb`, and with AOF on but no AOF yet, it starts empty. So:

1. Stop Redis.
2. Move its `appendonlydir` aside, and put the snapshot in the data directory as `dump.rdb`.
3. Start Redis with `appendonly no`. It loads the snapshot.
4. Run `redis-cli CONFIG SET appendonly yes`, which writes a new AOF from what Redis holds, and wait for `INFO persistence` to show `aof_rewrite_in_progress:0`.
5. Set `appendonly yes` again where Redis takes its configuration, for its next start.

This was tried on Redis 7.4, with the server running and an application connected. The server logged `Namespace default diverged`, and replaced the epoch. The application took the restored content of its Config at once, and registered its instance again within a renewal, as the instance was newer than the snapshot.

### What a restore means for applications

A restore is Redis losing every write since the snapshot, so it plays out [as above](#when-a-failover-loses-the-last-writes), on every namespace:

- **Configs** go back to what they were, and applications refresh with those values.
- **Instances** registered since are gone, and their processes register them again. Those removed since are back: a leased one goes once its lease expires, a fixed one stays until you remove it again.
- **Leases** in the snapshot have expired by then. Live processes renew theirs, as after any outage, while the reaper's grace and its [mass expiry protection](./architecture.md#leases) keep it from reaping them first.
- **Tokens** created since are gone, and the applications that use them are refused. **A token revoked since is valid again: revoke it again.** Roles and RoleBindings go back too.

If every server restarts along with Redis, none remembers where a namespace was. An application that comes back from a revision beyond the snapshot's is told that its position is gone, and starts over. But one whose revision new writes have already reached may resume on the wrong timeline, and stay there until its periodic fresh snapshot, every 30 minutes or so. That is why a server should keep running through a restore.

### From a zip file

Import it into a namespace that exists:

```bash
curl -X POST -H "Authorization: Bearer $TOKEN" https://cosky.example.com/v1/namespaces/prod/configs:import --data-binary @prod.zip
```

It overwrites Configs of the same name, and leaves the others as they are. Each import is an ordinary write, which applications follow.
