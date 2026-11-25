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

# Fails when the REST API lost something the base in $1 has: oasdiff compares the OpenAPI documents generated from the
# base and from this tree (api/build/openapi/cosky.yaml), routes, parameters, headers, status codes and schemas alike.
# buf guards the protos, and the schemas generated from them; this guards what only REST has. Before the freeze
# (frozen.sh) it only warns.
#
#   .github/scripts/rest-only-grows.sh .base
set -euo pipefail

base="$1"
document=api/build/openapi/cosky.yaml
if [[ "$("$(dirname "$0")/frozen.sh" "$base")" == true ]]; then
  oasdiff breaking "$base/$document" "$document" --format githubactions --fail-on ERR
else
  oasdiff breaking "$base/$document" "$document" --format githubactions |
    sed -E 's/^::error /::warning /'
  echo "Fine before the release candidate; from it on, a breaking change fails."
fi
