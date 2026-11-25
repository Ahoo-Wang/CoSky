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
# Fails when the protos lost something the base in $1 has, by the rules in api/src/main/proto/buf.yaml. Before the
# freeze (frozen.sh) it only warns, as the Kotlin API and REST checks do: a beta may still remove a field.
#
#   .github/scripts/proto-only-grows.sh .base
set -euo pipefail

base="$1"
check=(
  "$(dirname "$0")/retry-on-network-error.sh" ./gradlew :api:bufBreaking
  -PbufAgainst="$(cd "$base" && pwd)/api/src/main/proto" -PbufErrorFormat=github-actions
)
if [[ "$("$(dirname "$0")/frozen.sh" "$base")" == true ]]; then
  "${check[@]}"
else
  "${check[@]}" 2>&1 | sed -E 's/^::error /::warning /' || true
  echo "Fine before the release candidate; from it on, a breaking change fails."
fi
