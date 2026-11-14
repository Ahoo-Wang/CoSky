#!/bin/bash
# A Redis Cluster of three masters and three replicas, and CoSky servers on it, all on one Docker network.
# Usage: up.sh <servers> [image]
set -e
SERVERS=${1:-3}; IMAGE=${2:-ahoowang/cosky:8.0.0-alpha.5}; NET=cosky-cluster
docker network inspect $NET >/dev/null 2>&1 || docker network create $NET >/dev/null
for i in 1 2 3 4 5 6; do
  docker run -d --name cosky-redis-$i --network $NET redis:7.4-alpine redis-server \
    --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 \
    --appendonly yes --maxmemory-policy noeviction >/dev/null
done
sleep 2
IPS=$(for i in 1 2 3 4 5 6; do echo -n "$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' cosky-redis-$i):6379 "; done)
docker exec cosky-redis-1 redis-cli --cluster create $IPS --cluster-replicas 1 --cluster-yes >/dev/null
until docker exec cosky-redis-1 redis-cli cluster info | grep -q "cluster_state:ok"; do sleep 1; done
FIRST=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' cosky-redis-1)
for s in $(seq 1 $SERVERS); do
  docker run -d --name cosky-server-$s --network $NET -p $((8180+s)):8080 -p $((9190+s)):9090 \
    -e COSKY_STORE_REDIS_URI=redis://$FIRST:6379 -e COSKY_STORE_REDIS_CLUSTER=true -e COSKY_AUTH_ENABLED=false \
    -e JAVA_OPTS="-Xmx1g -XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow" \
    $IMAGE >/dev/null
done
for s in $(seq 1 $SERVERS); do
  until curl -sf localhost:$((8180+s))/actuator/health/readiness >/dev/null; do sleep 1; done
done
docker exec cosky-redis-1 redis-cli cluster nodes | awk '{print $2, $3}' | sed 's/@.*myself,/ /;s/@[0-9]*//'
echo "servers ready: http 8181..$((8180+SERVERS)), grpc 9191..$((9190+SERVERS))"
