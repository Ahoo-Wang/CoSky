# Failover measurements

How long a namespace stays unavailable while Redis fails over, measured end to end: a CoSky server in Docker writes a
Config every 100 ms over REST while the master that holds the namespace is stopped. These scripts produced the
[Redis Cluster](../../wiki/guide/performance.md#a-redis-cluster-failover) and
[Redis Sentinel](../../wiki/guide/performance.md#a-redis-sentinel-failover) results on the Performance page.

CI covers the same failovers at the store's level (`RedisStoreClusterFailoverTest`,
`RedisStoreSentinelFailoverTest`), asserting that writes come back within bounds. These scripts measure how long, through
the server, which a test does not report.

## Needs

Docker, `curl`, and Python 3 with nothing but its standard library. The server image defaults to
`ahoowang/cosky:<version>`, the version in `gradle.properties`; pass another, such as one built from
`server/Dockerfile`, to measure that instead. Each server takes 1 GB of heap.

## Redis Cluster

Three masters and three replicas, with a node timeout of 5 seconds. The servers listen on 8181, 8182, … for HTTP.

```bash
testing/failover/cluster/up.sh            # one server; up.sh 3 for three, up.sh 1 <image> for another image
testing/failover/cluster/measure.py 8181 kill
testing/failover/cluster/down.sh
```

`measure.py` creates a namespace whose slot is on a master other than the seed node, then stops that master: `kill` is
`docker kill`, `pause` freezes it with `docker pause`, as a hang. It prints each failed write and how long the
namespace was unavailable. Start a fresh cluster for each run: the stopped master stays stopped.

`local.sh` starts a cluster in one container instead, on ports 7100 to 7105 of this host, for servers that run on the
host itself, such as from an IDE. Remove it with `docker rm -f cosky-local-cluster`.

## Redis Sentinel

A master with one replica and three Sentinels, which take a master that does not answer for 5 seconds as down. The
server listens on 8281.

```bash
testing/failover/sentinel/up.sh
testing/failover/sentinel/measure.py kill   # or pause, or failover for SENTINEL FAILOVER
testing/failover/sentinel/down.sh
```

As for the cluster, start a fresh one for each run.

## In an environment of your own

`measure.py` stops a Docker container, so it reaches only the Redis that `up.sh` starts. Where Redis runs elsewhere,
`probe.py` does the writing alone: it writes a Config every 100 ms through a server's REST API and prints each kind of
failure once, when writes succeed again, and how long they failed. Fail Redis over, or restart servers, by your own
means while it runs, and stop it with Ctrl-C for a summary. Its times are this host's clock, to be read beside the logs
of Redis and of the servers.

```bash
COSKY_TOKEN=<token> testing/failover/probe.py http://cosky.example:8080   # namespace ga-trial, Config probe
```

The namespace must exist, and the token must be allowed `VERB_PUT` on the Config. The
[GA trial runbook](../../docs/ga-trial.md) (in Chinese) sets one up and lists the drills.
