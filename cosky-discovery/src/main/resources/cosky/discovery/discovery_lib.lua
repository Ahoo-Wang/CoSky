-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Shared by the discovery scripts. The key functions must stay in sync with DiscoveryKeyGenerator
-- (see DiscoveryLuaKeysTest).

local function instanceIdxKeyOf(namespace, serviceId)
    return namespace .. ':svc_itc_idx:' .. serviceId;
end

local function instanceKeyOf(namespace, instanceId)
    return namespace .. ':svc_itc:' .. instanceId;
end

local function topologyKeyOf(namespace, consumerName)
    return namespace .. ':topology:' .. consumerName;
end

-- Members of the expiry index identify an instance by service and instance id, length-prefixed so that no
-- separator character has to be reserved: '<#serviceId>:<serviceId><instanceId>'.
local function expiryMemberOf(serviceId, instanceId)
    return #serviceId .. ':' .. serviceId .. instanceId;
end

local function parseExpiryMember(member)
    local separator = string.find(member, ':', 1, true);
    local serviceIdLength = tonumber(string.sub(member, 1, separator - 1));
    local serviceId = string.sub(member, separator + 1, separator + serviceIdLength);
    return serviceId, string.sub(member, separator + serviceIdLength + 1);
end

-- Returns the TTL of the instance (-2: gone, -1: fixed, >0: seconds left). A gone instance is removed from its
-- index and announced as expired.
local function ensureNotExpired(instanceIdxKey, instanceId, instanceKey)
    local instanceTtl = redis.call('ttl', instanceKey);
    if instanceTtl == -2 and redis.call('srem', instanceIdxKey, instanceId) > 0 then
        redis.call('publish', instanceKey, 'expired');
    end
    return instanceTtl;
end

-- The instance fields as a flat field/value list, with 'ttl_at' appended for ephemeral instances.
local function readInstance(instanceKey, instanceTtl)
    local instanceData = redis.call('hgetall', instanceKey);
    if instanceTtl > 0 then
        instanceData[#instanceData + 1] = 'ttl_at';
        instanceData[#instanceData + 1] = tostring(redis.call('time')[1] + instanceTtl);
    end
    return instanceData;
end

-- Removes the user metadata fields ('_' prefix) while keeping system fields ('__' prefix), as ServiceInstanceCodec does.
local function clearMetadata(instanceKey)
    for _, key in ipairs(redis.call('hkeys', instanceKey)) do
        if string.sub(key, 1, 1) == '_' and string.sub(key, 1, 2) ~= '__' then
            redis.call('hdel', instanceKey, key);
        end
    end
end
