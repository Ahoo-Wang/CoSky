## What and why

Found while verifying Redis Cluster before the beta. On a cluster of three masters and three replicas (Docker, node timeout 5 s), `docker kill` of a master during writes left the namespaces in its slots unavailable for **53 seconds**. Redis had promoted the replica within about 5 seconds, but the server's Lettuce client went on sending those slots to the dead node, and every request on them hung until timing out. That is longer than the default lease TTL, for every namespace on that master.

Two settings in `store-redis`, for the cluster client only:

- **Topology refresh** (`ClusterTopologyRefreshOptions`, none before): every adaptive trigger (a node it cannot reconnect to after 2 attempts, MOVED/ASK, a slot no node holds, an unknown node), at most every 2 seconds, plus a periodic refresh every 5 seconds. The periodic one is what catches a node that hangs or a host that vanishes, since neither gives any sign.
- **A 10-second timeout** on the URI `RedisStore.open` builds for a cluster, unless the URI sets `timeout=` itself. Lettuce reads the topology with the seed URI's timeout, so with the default of 60 seconds every refresh also waited a minute for the hung node before it could follow the failover. It is the command timeout too: the scripts take milliseconds, and a request on a dead node now fails with `STORE_UNAVAILABLE` after 10 seconds instead of hanging for 60. Standalone and Sentinel are unchanged.

## How it was tested

- New `RedisStoreClusterFailoverTest`: a 6-node cluster in Testcontainers with a 2-second node timeout. It waits for the replicas to sync, freezes (`kill -STOP`) the master of a namespace, and expects the store to write to that namespace again within 30 seconds. With the change it did after about 13 seconds; without it, never in 30. `SHUTDOWN` would not have shown the defect: a node that goes away cleanly resets its connections, so the client finds out at once.
- `RedisClientOptionsTest` pins the refresh options.
- On the Docker cluster with a server image built from this branch, the same `docker kill` left the namespace unavailable for **21.4 seconds** instead of 53: the node timeout, the election, the next periodic refresh, and the wait for the dead node within it. Requests failed with 503 after 10 seconds instead of hanging.
- `CI=true ./gradlew :store-redis:check` and `ServerEndToEndTest` pass.

## Checklist

- [x] A test that fails without the change
- [x] No protocol, key or script change
