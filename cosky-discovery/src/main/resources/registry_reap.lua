-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

redis.replicate_commands();

-- Reaps up to `limit` instances whose ttlAt has passed, in O(log N + limit).
-- The expiry index only lists candidates: the TTL of the instance key stays authoritative.
local instanceExpiryKey = KEYS[1];
local namespace = ARGV[1];
local limit = tonumber(ARGV[2]);
-- @include cosky/discovery/discovery_lib.lua

local nowTime = redis.call('time')[1];
local reaped = 0;
for _, member in ipairs(redis.call('zrangebyscore', instanceExpiryKey, '-inf', nowTime, 'LIMIT', 0, limit)) do
    local serviceId, instanceId = parseExpiryMember(member);
    local instanceKey = instanceKeyOf(namespace, instanceId);
    local instanceTtl = ensureNotExpired(instanceIdxKeyOf(namespace, serviceId), instanceId, instanceKey);
    if instanceTtl > 0 then
        -- Still alive, e.g. renewed by a client that does not maintain the index.
        redis.call('zadd', instanceExpiryKey, nowTime + instanceTtl, member);
    else
        redis.call('zrem', instanceExpiryKey, member);
        if instanceTtl == -2 then
            reaped = reaped + 1;
        end
    end
end
return reaped;
