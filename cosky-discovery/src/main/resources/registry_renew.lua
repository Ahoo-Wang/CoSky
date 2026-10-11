-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

redis.replicate_commands();

local instanceKey = KEYS[1];
local instanceExpiryKey = KEYS[2];
local instanceTtl = ARGV[1];
local serviceId = ARGV[2];
local instanceId = ARGV[3];
local publishTtlAt = ARGV[4] == '1';
-- @include cosky/discovery/discovery_lib.lua

-- A renew is only published when the last published ttlAt is about to expire, which throttles renew events.
local pubTolerance = 5;
local lastRenewPublishTtlAtField = '__last_renew_pub_ttl_at';

local preTtl = redis.call('ttl', instanceKey);
if preTtl <= 0 then
    return preTtl;
end
if redis.call('expire', instanceKey, instanceTtl) == 0 then
    return 0;
end

local nowTime = redis.call('time')[1];
local ttlAt = nowTime + tonumber(instanceTtl);
redis.call('zadd', instanceExpiryKey, ttlAt, expiryMemberOf(serviceId, instanceId));
local lastRenewPublishTtlAt = redis.call('hget', instanceKey, lastRenewPublishTtlAtField);
if not lastRenewPublishTtlAt then
    lastRenewPublishTtlAt = preTtl + nowTime;
    redis.call('hset', instanceKey, lastRenewPublishTtlAtField, lastRenewPublishTtlAt);
end
if (lastRenewPublishTtlAt - nowTime - pubTolerance) < 0 then
    redis.call('hset', instanceKey, lastRenewPublishTtlAtField, ttlAt);
    redis.call('publish', instanceKey, publishTtlAt and ('renew:' .. ttlAt) or 'renew');
end
return 1;
