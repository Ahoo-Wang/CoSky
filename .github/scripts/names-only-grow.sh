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
# Fails when a property or a documented metric that the base in $1 has is gone (CONTRIBUTING.md, Releases and
# versioning). The properties are those of the configuration metadata the server and the starter ship, which
# ConfigurationMetadataTest holds to the code; the metrics are those of the deployment guide's table, by name and
# labels, which MetricsReferenceTest holds to the server. Before the freeze (frozen.sh) it only warns.
#
#   .github/scripts/names-only-grow.sh .base
set -euo pipefail

base="$1"
frozen="$("$(dirname "$0")/frozen.sh" "$base")"
level=warning
if [[ "$frozen" == true ]]; then
  level=error
fi

# The properties of the configuration metadata under $1.
properties() {
  for module in server spring-boot-starter; do
    metadata="$1/$module/src/main/resources/META-INF/spring-configuration-metadata.json"
    if [[ -f "$metadata" ]]; then
      jq -r '.properties[].name' "$metadata"
    fi
  done | sort -u
}

# The first column of the deployment guide's table of metrics under $1, as name{label,...}.
metrics() {
  grep -E '^\| `cosky_' "$1/wiki/guide/deployment.md" | cut -d'|' -f2 | grep -oE '`cosky_[^`]+`' | tr -d '`' |
    sort -u
}

gone=0
# Reports each line on stdin as a $1 the base has and this tree does not.
report() {
  while IFS= read -r name; do
    echo "::$level::The $1 $name is gone, or changed: from the release candidate on, properties and metrics only grow."
    gone=1
  done
}
report property < <(comm -23 <(properties "$base") <(properties .))
report metric < <(comm -23 <(metrics "$base") <(metrics .))

if [[ "$gone" == 1 && "$frozen" == true ]]; then
  exit 1
elif [[ "$gone" == 1 ]]; then
  echo "Fine before the release candidate; from it on, a property or metric that is gone fails."
fi
