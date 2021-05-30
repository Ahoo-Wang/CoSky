local namespace = KEYS[1];
local serviceId = ARGV[1];
local instanceId = ARGV[2];
local instanceIdxKey = namespace .. ":svc_itc_idx:" .. serviceId;
local instanceKey = namespace .. ":svc_itc:" .. instanceId;

local removed = redis.call("srem", instanceIdxKey, instanceId);
if removed == 1 then
    redis.call("publish", instanceKey, "deregister");
    return redis.call("del", instanceKey);
else
    return 0;
end
