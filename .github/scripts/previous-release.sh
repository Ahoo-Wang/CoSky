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
# Prints the tag of the release that the notes of version $1 start from, given the tags of the existing releases on
# stdin, newest first, as `gh release list` gives them; nothing when there is none.
#
# - A pre-release starts from the newest release of its major version, pre-releases included, so that its notes say
#   what changed since the one before. gh release view would not do: it only knows the latest stable release.
# - A stable release starts from the stable release just below it, by version, so that its notes cover the whole cycle
#   and not only what came after its last release candidate. The first of a major version starts from the last of the
#   one before: 8.0.0 from 6.2.0, as 8.0.0-alpha.1 did, so that its notes hold those of every 8.0 pre-release.
# - A pre-release with no release of its major version yet starts from the stable release below it too.
#
#   gh release list --exclude-drafts --limit 100 --json tagName -q '.[].tagName' | previous-release.sh 8.0.0
set -euo pipefail

version="$1"
major="${version%%.*}"
core="${version%%-*}"
tags="$(cat)"

# The newest release of the major version, other than this one.
newest_of_major() {
  awk -v prefix="v${major}." -v self="v${version}" 'index($0, prefix) == 1 && $0 != self { print; exit }' <<< "$tags"
}

# The highest stable release below the version's major.minor.patch.
stable_below() {
  awk -F. -v core="$core" '
    BEGIN { split(core, c, ".") }
    /^v[0-9]+\.[0-9]+\.[0-9]+$/ {
      sub(/^v/, "", $1)
      if ($1 + 0 < c[1] + 0 || ($1 + 0 == c[1] + 0 && ($2 + 0 < c[2] + 0 || ($2 + 0 == c[2] + 0 && $3 + 0 < c[3] + 0)))) {
        print $1 "." $2 "." $3
      }
    }' <<< "$tags" | sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1 | sed 's/^./v&/'
}

previous=""
if [[ "$version" == *-* ]]; then
  previous="$(newest_of_major)"
fi
if [[ -z "$previous" ]]; then
  previous="$(stable_below)"
fi
if [[ -n "$previous" ]]; then
  echo "$previous"
fi
