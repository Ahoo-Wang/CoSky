#!/bin/bash
docker rm -f $(docker ps -aq --filter name=cosky-redis- --filter name=cosky-server-) >/dev/null 2>&1
docker network rm cosky-cluster >/dev/null 2>&1
echo down
