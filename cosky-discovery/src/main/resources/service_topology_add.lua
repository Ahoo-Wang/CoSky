-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

redis.replicate_commands();

local topologyIdxKey = KEYS[1];
local topologyKey = KEYS[2];
local consumerName = ARGV[1];
local producerName = ARGV[2];

local nowTime = redis.call('time')[1];
redis.call('hsetnx', topologyIdxKey, consumerName, nowTime);
redis.call('hsetnx', topologyKey, producerName, nowTime);
