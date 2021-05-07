local namespace = KEYS[1];
local serviceId = KEYS[2];
local instanceId = KEYS[3];
local instanceIdxKey = namespace .. ':svc_itc_idx:' .. serviceId;

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

local instanceTtl = ensureNotExpired(instanceIdxKey, instanceId);
if instanceTtl ~= -2 then
    local instanceKey = getInstanceKey(instanceId);
    local instanceData = redis.call('hgetall', instanceKey);
    if instanceTtl > 0 then
        instanceData[#instanceData + 1] = "ttl_at";
        local nowTime = redis.call('time')[1];
        local ttlAt = nowTime + instanceTtl;
        instanceData[#instanceData + 1] = tostring(ttlAt);
    end
    return instanceData;
end



