-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

local serviceIdxKey = KEYS[1];
local serviceStatKey = KEYS[2];
local namespace = ARGV[1];
local onlyServiceId = ARGV[2];
-- @include cosky/discovery/discovery_lib.lua

local function statService(serviceId)
    local instanceIdxKey = instanceIdxKeyOf(namespace, serviceId);
    local instanceCount = 0;
    for _, instanceId in ipairs(redis.call('smembers', instanceIdxKey)) do
        if ensureNotExpired(instanceIdxKey, instanceId, instanceKeyOf(namespace, instanceId)) ~= -2 then
            instanceCount = instanceCount + 1;
        end
    end
    redis.call('hset', serviceStatKey, serviceId, instanceCount);
end

if onlyServiceId then
    statService(onlyServiceId);
    return;
end
for _, serviceId in ipairs(redis.call('smembers', serviceIdxKey)) do
    statService(serviceId);
end
