#!/bin/bash
# Redis with one replica and three Sentinels, and a CoSky server on them, all on one Docker network.
# Usage: up.sh [image]
set -e
IMAGE=${1:-ahoowang/cosky:8.0.0-beta.1}; NET=cosky-sentinel
ip() { docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$1"; }
docker network inspect $NET >/dev/null 2>&1 || docker network create $NET >/dev/null
docker run -d --name cosky-sredis-1 --network $NET redis:7.4-alpine redis-server --appendonly yes --maxmemory-policy noeviction >/dev/null
M=$(ip cosky-sredis-1)
docker run -d --name cosky-sredis-2 --network $NET redis:7.4-alpine redis-server --appendonly yes --maxmemory-policy noeviction --replicaof $M 6379 >/dev/null
for i in 1 2 3; do
  docker run -d --name cosky-sentinel-$i --network $NET redis:7.4-alpine sh -c "printf 'port 26379\nsentinel monitor mymaster $M 6379 2\nsentinel down-after-milliseconds mymaster 5000\nsentinel failover-timeout mymaster 10000\nsentinel parallel-syncs mymaster 1\n' > /tmp/s.conf && exec redis-sentinel /tmp/s.conf" >/dev/null
done
until docker exec cosky-sredis-1 redis-cli info replication | grep -q 'slave0:.*state=online'; do sleep 1; done
until [ "$(docker exec cosky-sentinel-1 redis-cli -p 26379 sentinel master mymaster | awk 'f{print;exit} /^num-other-sentinels$/{f=1}')" = 2 ]; do sleep 1; done
docker run -d --name cosky-sserver-1 --network $NET -p 8281:8080 \
  -e COSKY_STORE_REDIS_URI='redis-sentinel://cosky-sentinel-1:26379,cosky-sentinel-2:26379,cosky-sentinel-3:26379#mymaster' \
  -e COSKY_AUTH_ENABLED=false \
  -e JAVA_OPTS="-Xmx1g -XX:+UseCompactObjectHeaders --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow" \
  $IMAGE >/dev/null
for i in $(seq 1 90); do curl -sf localhost:8281/actuator/health/readiness >/dev/null && break; sleep 1; done
curl -s localhost:8281/actuator/health/readiness; echo
echo "master: $M ($(docker exec cosky-sentinel-1 redis-cli -p 26379 sentinel get-master-addr-by-name mymaster | tr '\n' ' '))"
