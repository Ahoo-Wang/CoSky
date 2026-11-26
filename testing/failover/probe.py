#!/usr/bin/env python3
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

"""Write a Config every 100 ms through a server's REST API, and report each failed write and how long writes failed.

For a drill in an environment of your own, where measure.py cannot stop the master: start this, fail Redis over or
restart servers by your own means, and stop it with Ctrl-C for a summary. Times are this host's clock, to be read
beside the logs of Redis and of the servers.

Usage: COSKY_TOKEN=<token> probe.py <server URL, such as http://cosky.example:8080> [namespace, ga-trial] [config, probe]
The namespace must exist, and the token must be allowed VERB_PUT on that Config.
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime

if len(sys.argv) < 2:
    sys.exit(__doc__)
BASE = sys.argv[1].rstrip("/")
NAMESPACE = sys.argv[2] if len(sys.argv) > 2 else "ga-trial"
CONFIG = sys.argv[3] if len(sys.argv) > 3 else "probe"
URL = f"{BASE}/v1/namespaces/{NAMESPACE}/configs/{CONFIG}"
TOKEN = os.environ.get("COSKY_TOKEN")
SLOW = 1.0  # A write that succeeds after longer than this, in seconds, is reported as well.


def put(n):
    headers = {"Content-Type": "application/json"}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"
    body = json.dumps({"config": {"format": "CONFIG_FORMAT_TEXT", "content": str(n)}}).encode()
    request = urllib.request.Request(URL, data=body, method="PUT", headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return str(response.status)
    except urllib.error.HTTPError as e:
        try:
            reason = json.loads(e.read()).get("reason")
        except ValueError:
            reason = None
        return f"{e.code} {reason}" if reason else str(e.code)
    except urllib.error.URLError as e:
        return type(e.reason).__name__ if isinstance(e.reason, Exception) else str(e.reason)
    except Exception as e:  # noqa: BLE001 - a timeout or a reset is a failed write like any other
        return type(e).__name__


def clock():
    return datetime.now().strftime("%H:%M:%S.%f")[:-3]


first = put(0)
if first != "200":
    sys.exit(f"The first write failed: {first}. Check the URL, the token, and that namespace {NAMESPACE} exists.")
print(f"{clock()}  writing {URL} every 100 ms; Ctrl-C stops")

writes, failed, longest, latencies = 1, 0, 0.0, []
failing = None  # When the writes began to fail: this host's clock, time.monotonic(), and how many have failed since.
last = None  # The last failure reported: the same again is only counted.
try:
    while True:
        at, started = clock(), time.monotonic()
        status = put(writes)
        took = time.monotonic() - started
        writes += 1
        if status == "200":
            latencies.append(took)
            if failing:
                lasted = time.monotonic() - failing[1]
                longest = max(longest, lasted)
                print(f"{at}  writes succeed again, after {failing[2]} failed for {lasted:.1f}s since {failing[0]}")
                failing, last = None, None
            elif took > SLOW:
                print(f"{at}  succeeded after {took:.1f}s")
        else:
            failed += 1
            failing = (failing[0], failing[1], failing[2] + 1) if failing else (at, started, 1)
            if status != last:
                print(f"{at}  {status} after {took:.1f}s")
                last = status
        time.sleep(0.1)
except KeyboardInterrupt:
    pass

if failing:
    lasted = time.monotonic() - failing[1]
    longest = max(longest, lasted)
    print(f"Writes still failing, for {lasted:.1f}s since {failing[0]}.")
latencies.sort()


def millis(fraction):
    return f"{latencies[min(len(latencies) - 1, int(fraction * len(latencies)))] * 1000:.0f} ms" if latencies else "-"


print(f"{writes} writes, {failed} failed, the longest failure {longest:.1f}s; "
      f"succeeded in p50 {millis(0.5)}, p99 {millis(0.99)}, max {millis(1)}")
