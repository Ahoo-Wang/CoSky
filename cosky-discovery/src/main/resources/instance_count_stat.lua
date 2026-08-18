local namespace = KEYS[1];
local serviceIdxKey = namespace .. ":svc_idx";

local instanceCount = 0;

local function getInstanceIdxKey(serviceId)
    return namespace .. ":svc_itc_idx:" .. serviceId;
end

local function getInstanceKey(instanceId)
    return namespace .. ":svc_itc:" .. instanceId;
end

local function statInstance(serviceId)
    local instanceIdxKey = getInstanceIdxKey(serviceId);
    local instanceIds = redis.call("smembers", instanceIdxKey);
    for index, instanceId in ipairs(instanceIds) do
        local instanceKey = getInstanceKey(instanceId);
        if redis.call("exists", instanceKey) == 1 then
            instanceCount = instanceCount + 1;
        elseif redis.call("srem", instanceIdxKey, instanceId) > 0 then
            redis.call("publish", instanceKey, "expired");
        end
    end
end

local serviceIds = redis.call("smembers", serviceIdxKey);

for index, serviceId in ipairs(serviceIds) do
    statInstance(serviceId)
end

return instanceCount;
