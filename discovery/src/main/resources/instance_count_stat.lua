local namespace = KEYS[1];
local serviceIdxKey = namespace .. ":svc_idx";

local instanceCount = 0;

local function getInstanceIdxKey(serviceId)
    return namespace .. ":svc_itc_idx:" .. serviceId;
end

local function statInstance(serviceId)
    local instanceIdxKey = getInstanceIdxKey(serviceId);
    local servicedInstanceCount = redis.call("scard", instanceIdxKey);
    instanceCount = instanceCount + servicedInstanceCount;
end

local serviceIds = redis.call("smembers", serviceIdxKey);

for index, serviceId in ipairs(serviceIds) do
    statInstance(serviceId)
end

return instanceCount;
