#!/usr/bin/env python3
"""Stop the Sentinel master while writing to a namespace, and report how long the writes failed.

Usage: measure.py [kill | pause | failover]
kill is `docker kill`, pause freezes the master with `docker pause` (a hang), failover is SENTINEL FAILOVER.
"""
import json, subprocess, sys, time, urllib.error, urllib.request, uuid

STOP = sys.argv[1] if len(sys.argv) > 1 else "kill"
BASE = "http://localhost:8281"


def sh(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def ip_of(container):
    return sh("docker", "inspect", "-f", "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}", container)


def put(path, body, timeout=90):
    request = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="PUT",
                                     headers={"Content-Type": "application/json"})
    started = time.monotonic()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            status = response.status
    except urllib.error.HTTPError as e:
        status = e.code
    except Exception as e:  # noqa: BLE001
        status = type(e).__name__
    return status, time.monotonic() - started


master_ip = sh("docker", "exec", "cosky-sentinel-1", "redis-cli", "-p", "26379", "sentinel", "get-master-addr-by-name",
               "mymaster").splitlines()[0]
victim = {ip_of(c): c for c in ("cosky-sredis-1", "cosky-sredis-2")}[master_ip]
namespace = f"ns-{uuid.uuid4().hex[:8]}"
status, _ = put(f"/v1/namespaces/{namespace}", {"namespace": {}})
assert status == 200, status
print(f"namespace {namespace}; master {victim} ({master_ip}); stop: {STOP}")


def write(n):
    return put(f"/v1/namespaces/{namespace}/configs/probe", {"config": {"format": "CONFIG_FORMAT_TEXT", "content": str(n)}})


for n in range(20):
    status, took = write(n)
    assert status == 200, status
    time.sleep(0.1)
time.sleep(1)  # let the replica catch up

stopped = time.monotonic()
if STOP == "failover":
    sh("docker", "exec", "cosky-sentinel-1", "redis-cli", "-p", "26379", "sentinel", "failover", "mymaster")
else:
    sh("docker", STOP, victim)
failures, recovered, n = [], None, 20
LIMIT = 240
while time.monotonic() - stopped < LIMIT:
    n += 1
    status, took = write(n)
    at = time.monotonic() - stopped
    if status == 200:
        recovered = at
        break
    failures.append((round(at - took, 1), round(took, 1), status))
    time.sleep(0.1)
shown = failures if len(failures) <= 12 else failures[:6] + [("...",)] + failures[-5:]
for f in shown:
    print("  ..." if f == ("...",) else f"  +{f[0]:5.1f}s  {f[2]}  after {f[1]}s")
print(f"unavailable for {recovered:.1f}s" if recovered else f"not recovered within {LIMIT}s")
new_master = sh("docker", "exec", "cosky-sentinel-1", "redis-cli", "-p", "26379", "sentinel", "get-master-addr-by-name",
                "mymaster").splitlines()[0]
print(f"sentinel's master now {new_master}")
if STOP == "pause":
    sh("docker", "unpause", victim)
    print("unpaused the old master")
slow = []
for m in range(40):
    status, took = write(1000 + m)
    if status != 200 or took > 1:
        slow.append((m, status, round(took, 1)))
    time.sleep(0.25)
print(f"after recovery: {len(slow)} of 40 writes failed or slow {slow[:5]}")
