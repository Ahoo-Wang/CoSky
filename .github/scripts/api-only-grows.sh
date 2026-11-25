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

# Fails when a Kotlin API dump (*/api/*.api) lost a line the base in $1 has: a declaration removed, or changed, which is
# a removed line and an added one. checkKotlinAbi only checks that the dumps match the code, which a pull request meets
# by updating them; this checks that what is published stays. Before the freeze (frozen.sh) it only warns.
#
#   .github/scripts/api-only-grows.sh .base
set -euo pipefail

base="$1"
frozen="$("$(dirname "$0")/frozen.sh" "$base")"
level=warning
if [[ "$frozen" == true ]]; then
  level=error
fi

removed=0
while IFS= read -r dump; do
  relative="${dump#"$base"/}"
  if [[ ! -f "$relative" ]]; then
    echo "::$level file=$relative::The whole Kotlin API of $relative is gone."
    removed=$((removed + 1))
    continue
  fi
  while IFS= read -r line; do
    echo "::$level file=$relative::No longer in the Kotlin API: ${line}"
    removed=$((removed + 1))
  done < <(comm -23 <(grep -v '^[[:space:]]*$' "$dump" | sort -u) <(grep -v '^[[:space:]]*$' "$relative" | sort -u))
done < <(find "$base" -path "$base/.git" -prune -o -path '*/api/*.api' -print | sort)

if ((removed == 0)); then
  echo "The Kotlin API only grows."
elif [[ "$frozen" == true ]]; then
  echo "$removed lines of the Kotlin API are gone, which the freeze does not allow (CONTRIBUTING.md)."
  exit 1
else
  echo "$removed lines of the Kotlin API are gone. Fine before the release candidate; from it on, this fails."
fi
