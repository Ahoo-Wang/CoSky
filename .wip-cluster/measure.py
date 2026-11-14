#!/usr/bin/env python3
"""Kill the master of a namespace's slot while writing to it, and report how long the writes failed.

Usage: measure.py [server port, 8181] [stop: kill | pause]
Needs the cluster from up.sh. `kill` is `docker kill`; `pause` freezes the node with `docker pause`, a hang.
"""
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8181
STOP = sys.argv[2] if len(sys.argv) > 2 else "kill"
BASE = f"http://localhost:{PORT}"
SEED = "cosky-redis-1"


def sh(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def cli(*args):
    return sh("docker", "exec", SEED, "redis-cli", *args)


def ip_of(container):
    return sh("docker", "inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", container)


def master_of(slot):
    for line in cli("cluster", "nodes").splitlines():
        fields = line.split()
        if "master" not in fields[2] or "fail" in fields[2]:
            continue
        for part in fields[8:]:
            lo, _, hi = part.partition("-")
            if lo.isdigit() and int(lo) <= slot <= int(hi or lo):
                return fields[1].split("@")[0].split(":")[0]
    raise RuntimeError(f"no master for slot {slot}")


def put(path, body, timeout=30):
    request = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="PUT",
                                     headers={"Content-Type": "application/json"})
    started = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
    except urllib.error.HTTPError as e:
        status = e.code
    except Exception as e:  # noqa: BLE001 - a timeout or a reset is a failed write like any other
        status = type(e).__name__
    return status, time.monotonic() - started


containers = {ip_of(f"cosky-redis-{i}"): f"cosky-redis-{i}" for i in range(1, 7)}
seed_ip = ip_of(SEED)
while True:
    namespace = f"ns-{uuid.uuid4().hex[:8]}"
    victim_ip = master_of(int(cli("cluster", "keyslot", f"cosky:{{{namespace}}}:rev")))
    if victim_ip != seed_ip:
        break
victim = containers[victim_ip]
status, _ = put(f"/v1/namespaces/{namespace}", {"namespace": {}})
assert status == 200, status
print(f"namespace {namespace} on {victim} ({victim_ip}); stop: {STOP}")


def write(n):
    return put(f"/v1/namespaces/{namespace}/configs/probe", {"config": {"format": "CONFIG_FORMAT_TEXT",
                                                                         "content": str(n)}})


for n in range(20):
    status, took = write(n)
    assert status == 200, status
    time.sleep(0.1)

stopped = time.monotonic()
sh("docker", STOP, victim)
failures, recovered, n = [], None, 20
while time.monotonic() - stopped < 180:
    n += 1
    status, took = write(n)
    at = time.monotonic() - stopped
    if status == 200:
        recovered = at
        break
    failures.append((round(at - took, 1), round(took, 1), status))
    time.sleep(0.1)

for start, took, status in failures:
    print(f"  +{start:5.1f}s  {status}  after {took}s")
print(f"unavailable for {recovered:.1f}s" if recovered else "not recovered within 180s")
for n in range(20):
    status, took = write(n)
    if status != 200 or took > 1:
        print(f"  after recovery: {status} in {took:.1f}s")
if STOP == "pause":
    sh("docker", "unpause", victim)
