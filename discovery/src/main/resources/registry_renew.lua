local namespace = KEYS[1];
local instanceId = KEYS[2];
local instanceTtl = KEYS[3];

local instanceKey = namespace .. ":svc_itc:" .. instanceId;

--local nowTime = redis.call('time')[1];
--local nextTtlAt = nowTime + instanceTtl;
--local preTtlAt = redis.call("hget", instanceKey, ttlAtField);

local result = redis.call("expire", instanceKey, instanceTtl);
if result == 1 then
    redis.call("publish", instanceKey, "renew");
end

return result;

