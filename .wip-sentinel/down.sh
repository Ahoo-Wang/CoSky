#!/bin/bash
docker rm -f $(docker ps -aq --filter name=cosky-sredis- --filter name=cosky-sentinel- --filter name=cosky-sserver-) >/dev/null 2>&1
docker network rm cosky-sentinel >/dev/null 2>&1
echo down
