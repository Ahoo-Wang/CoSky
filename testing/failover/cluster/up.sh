#!/bin/bash
# Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#      http://www.apache.org/licenses/LICENSE-2.0
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# A Redis Cluster of three masters and three replicas, and CoSky servers on it, all on one Docker network.
# Usage: up.sh [servers, 1] [image, ahoowang/cosky:<the version in gradle.properties>]
set -e
VERSION=$(sed -n 's/^version=//p' "$(cd "$(dirname "$0")/../../.." && pwd)/gradle.properties")
SERVERS=${1:-1}; IMAGE=${2:-ahoowang/cosky:$VERSION}; NET=cosky-cluster
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
if [ "$SERVERS" -gt 0 ]; then
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
fi
docker exec cosky-redis-1 redis-cli cluster nodes | awk '{print $2, $3}' | sed 's/@.*myself,/ /;s/@[0-9]*//'
echo "seed: redis://$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' cosky-redis-1):6379; servers: $SERVERS"
