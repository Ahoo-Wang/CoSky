#!/bin/bash
# A Redis Cluster of three masters and three replicas in one container, on ports 7100-7105 (7000 is AirPlay on macOS) of this host, for servers
# that run on the host itself: the nodes announce 127.0.0.1, and Docker maps each port to the same one.
# Usage: local.sh [image]   Remove with: docker rm -f cosky-local-cluster
set -e
IMAGE=${1:-redis:7.4-alpine}
START=""
for p in 7100 7101 7102 7103 7104 7105; do
  START="$START redis-server --port $p --cluster-enabled yes --cluster-config-file n$p.conf --cluster-node-timeout 5000 --cluster-announce-ip 127.0.0.1 --daemonize yes --logfile /tmp/$p.log &&"
done
docker run -d --name cosky-local-cluster -p 7100-7105:7100-7105 $IMAGE sh -c "$START sleep 1 && redis-cli --cluster create 127.0.0.1:7100 127.0.0.1:7101 127.0.0.1:7102 127.0.0.1:7103 127.0.0.1:7104 127.0.0.1:7105 --cluster-replicas 1 --cluster-yes && echo CLUSTER_READY && tail -f /dev/null" >/dev/null
until docker logs cosky-local-cluster 2>&1 | grep -q CLUSTER_READY; do sleep 1; done
until docker exec cosky-local-cluster redis-cli -p 7100 cluster info | grep -q "cluster_state:ok"; do sleep 1; done
until [ "$(for p in 7100 7101 7102; do docker exec cosky-local-cluster redis-cli -p $p info replication | grep -c 'state=online'; done | paste -sd+ - | bc)" = 3 ]; do sleep 1; done
docker exec cosky-local-cluster redis-cli -p 7100 cluster nodes | awk '{print $2, $3, $9}'
echo "seed: redis://127.0.0.1:7100"
