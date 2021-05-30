local namespace = KEYS[1];
local instanceId = ARGV[1];
local instanceTtl = ARGV[2];

local pubTolerance = 2;
local lastRenewPublishTtlAtField = "__last_renew_pub_ttl_at";
local instanceKey = namespace .. ":svc_itc:" .. instanceId;

local preTtl = redis.call("ttl", instanceKey);

local result = redis.call("expire", instanceKey, instanceTtl);

if result == 0 then
    return result
end

local nowTime = redis.call('time')[1];

local lastRenewPublishTtlAt = redis.call("hget", instanceKey, lastRenewPublishTtlAtField);
if not lastRenewPublishTtlAt then
    lastRenewPublishTtlAt = preTtl + nowTime;
    redis.call("hset", instanceKey, lastRenewPublishTtlAtField, lastRenewPublishTtlAt);
end

local shouldPub = (lastRenewPublishTtlAt - nowTime - pubTolerance) < 0;

if shouldPub then
    local currentTtlAt = nowTime + tonumber(instanceTtl);
    redis.call("hset", instanceKey, lastRenewPublishTtlAtField, currentTtlAt);
    redis.call("publish", instanceKey, "renew");
end

return result;

