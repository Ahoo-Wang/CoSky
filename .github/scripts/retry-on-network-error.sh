#!/usr/bin/env bash
#
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
#

# Runs a command, a Gradle build, and runs it again when it failed on a transient network error: Maven Central
# answering 429 (Too Many Requests) or a 5xx, a timeout, a dropped connection.
#
#   .github/scripts/retry-on-network-error.sh ./gradlew assemble
#
# Gradle retries such a request itself, three times within about three seconds, then disables the repository for the
# rest of the build: a rate limit outlasts that. Each new attempt starts where the last one stopped, since what it
# downloaded stays in the Gradle cache. Any other failure, a compile error or a dependency that does not exist, ends it
# at once with the command's own exit status, and so does a transient one after the last attempt.
# RETRY_DELAYS sets the seconds to wait before each new attempt.
set -uo pipefail

read -r -a delays <<< "${RETRY_DELAYS:-60 120 240}"
transient='status code (408|429|5[0-9][0-9]) from server|Read timed out|[Cc]onnect timed out|Connection reset|Remote host terminated the handshake|NoHttpResponseException|UnknownHostException'

log="$(mktemp)"
trap 'rm -f "$log"' EXIT

attempt=1
while true; do
  "$@" 2>&1 | tee "$log"
  status="${PIPESTATUS[0]}"
  if [[ "$status" -eq 0 ]]; then
    exit 0
  fi
  if ((attempt > ${#delays[@]})) || ! grep -Eq "$transient" "$log"; then
    exit "$status"
  fi
  delay="${delays[attempt - 1]}"
  echo "::warning::Attempt $attempt of '$*' failed on a transient network error. Trying again in ${delay}s."
  sleep "$delay"
  attempt=$((attempt + 1))
done
