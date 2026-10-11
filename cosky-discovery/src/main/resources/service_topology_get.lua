-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

local topologyIdxKey = KEYS[1];
local namespace = ARGV[1];
-- @include cosky/discovery/discovery_lib.lua

local topology = {};
for _, consumerName in ipairs(redis.call('hkeys', topologyIdxKey)) do
    topology[#topology + 1] = consumerName;
    topology[#topology + 1] = redis.call('hkeys', topologyKeyOf(namespace, consumerName));
end
return topology;
