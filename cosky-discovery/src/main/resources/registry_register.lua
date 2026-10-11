-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

local serviceIdxKey = KEYS[1];
local serviceStatKey = KEYS[2];
local instanceIdxKey = KEYS[3];
local instanceKey = KEYS[4];
-- @include cosky/discovery/discovery_lib.lua

local instanceTtl = ARGV[1];
local fixed = instanceTtl == '-1';
local serviceId = ARGV[2];
local instanceId = ARGV[3];
local schema = ARGV[4];
local host = ARGV[5];
local port = ARGV[6];
local weight = ARGV[7];
local ephemeral = fixed and 'false' or 'true';

if redis.call('sadd', instanceIdxKey, instanceId) == 1 and redis.call('sadd', serviceIdxKey, serviceId) > 0 then
    redis.call('publish', serviceIdxKey, 'set');
    redis.call('hset', serviceStatKey, serviceId, 0);
end

clearMetadata(instanceKey);
redis.call('hmset', instanceKey, 'instanceId', instanceId, 'serviceId', serviceId, 'schema', schema, 'host', host,
    'port', port, 'weight', weight, 'ephemeral', ephemeral, unpack(ARGV, 8, #ARGV));
redis.call('publish', instanceKey, 'register');
if not fixed then
    return redis.call('expire', instanceKey, instanceTtl);
end
redis.call('persist', instanceKey);
return 1;
