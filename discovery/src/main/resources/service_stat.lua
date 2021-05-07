local namespace = KEYS[1];
local serviceIdxKey = namespace .. ":svc_idx";
local serviceIdxStatKey = namespace .. ":svc_stat";
local function getInstanceIdxKey(serviceId)
    return namespace .. ":svc_itc_idx:" .. serviceId;
end

local function getInstanceKey(instanceId)
    return namespace .. ":svc_itc:" .. instanceId;
end

local function ensureNotExpired(instanceIdxKey, instanceId)
    local instanceKey = getInstanceKey(instanceId);
    local instanceTtl = redis.call("ttl", instanceKey);
    -- -2: The key doesn't exist | -1: The key is fixed | >0: ttl(second)
    if instanceTtl == -2 then
        redis.call("srem", instanceIdxKey, instanceId);
        redis.call("del", instanceKey);
        redis.call("publish", instanceKey, "expired");
    end

    return instanceTtl;
end

local function setServiceStat(serviceId, instanceCount)
    return redis.call("hset", serviceIdxStatKey, serviceId, instanceCount)
end

local function statService(serviceId)
    local instanceIdxKey = getInstanceIdxKey(serviceId);
    local instanceIds = redis.call("smembers", instanceIdxKey);
    local instanceCount = 0;
    if #instanceIds == 0 then
        setServiceStat(serviceId, instanceCount);
        return ;
    end

    for index, instanceId in ipairs(instanceIds) do
        local instanceTtl = ensureNotExpired(instanceIdxKey, instanceId);
        if instanceTtl ~= -2 then
            instanceCount = instanceCount + 1;
        end
    end
    setServiceStat(serviceId, instanceCount);
end

if #KEYS == 2 then
    local serviceId = KEYS[2];
    statService(serviceId)
    return ;
end

local serviceIds = redis.call("smembers", serviceIdxKey);

for index, serviceId in ipairs(serviceIds) do
    statService(serviceId)
end
