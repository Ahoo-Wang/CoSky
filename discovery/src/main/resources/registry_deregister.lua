local namespace = KEYS[1];
local serviceId = KEYS[2];
local instanceId = KEYS[3];
local instanceIdxKey = namespace .. ":svc_itc_idx:" .. serviceId;
local instanceKey = namespace .. ":svc_itc:" .. instanceId;

local removed = redis.call("srem", instanceIdxKey, instanceId);
if removed == 1 then
    return redis.call("del", instanceKey);
else
    return 0;
end

