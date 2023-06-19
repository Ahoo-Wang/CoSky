-- Note: starting with Redis 5, the replication method described in this section (scripts effects replication) is the default and does not need to be explicitly enabled.
redis.replicate_commands();

local namespace = KEYS[1];
local instanceId = ARGV[1];
local instanceTtl = ARGV[2];

local pubTolerance = 5;
local lastRenewPublishTtlAtField = "__last_renew_pub_ttl_at";
local instanceKey = namespace .. ":svc_itc:" .. instanceId;

-- -2: The key doesn't exist | -1: The key is fixed | >0: ttl(second)
local preTtl = redis.call("ttl", instanceKey);
if preTtl <= 0 then
    return preTtl
end

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

return 1;

