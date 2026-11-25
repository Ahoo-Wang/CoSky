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

# Prints "true" when a pull request onto the base in $1 must keep what that base has (CONTRIBUTING.md, Releases and
# versioning): from a release candidate on, within one major version. An alpha or a beta makes no promise, and a pull
# request that raises the major version may break anything.
#
#   .github/scripts/frozen.sh .base
set -euo pipefail

version_of() { sed -n 's/^version=//p' "$1/gradle.properties"; }

base="$(version_of "$1")"
head="$(version_of .)"
if [[ "$base" == *-alpha* || "$base" == *-beta* || "${head%%.*}" != "${base%%.*}" ]]; then
  echo false
else
  echo true
fi
