-- Note: starting with Redis 5, the replication method described in this section (scripts effects replication) is the default and does not need to be explicitly enabled.
redis.replicate_commands();

local namespace = KEYS[1];
local serviceId = ARGV[1];
local instanceId = ARGV[2];
local instanceIdxKey = namespace .. ':svc_itc_idx:' .. serviceId;

local function getInstanceKey(instanceId)
    return namespace .. ":svc_itc:" .. instanceId;
end

local function ensureNotExpired(instanceIdxKey, instanceId)
    local instanceKey = getInstanceKey(instanceId);
    local instanceTtl = redis.call("ttl", instanceKey);
    -- -2: The key doesn't exist | -1: The key is fixed | >0: ttl(second)
    if instanceTtl == -2 then
        local removed = redis.call("srem", instanceIdxKey, instanceId);
        if removed > 0 then
            redis.call("publish", instanceKey, "expired");
        end
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

return {  }


