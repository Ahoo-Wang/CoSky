local namespace = KEYS[1];
local serviceId = KEYS[2];
local instanceIdxKey = namespace .. ':svc_itc_idx:' .. serviceId;
local instanceIds = redis.call('smembers', instanceIdxKey);
local instances = {};
local instanceKey;
local instanceData;
local instancesIdx = 0;
for index, instanceId in ipairs(instanceIds) do
    instanceKey = namespace .. ':svc_itc:' .. instanceId;
    instanceData = redis.call('hgetall', instanceKey);
    if #instanceData > 0 then
        instancesIdx = instancesIdx + 1;
        instances[instancesIdx] = instanceData;
    else
        redis.call('srem', instanceIdxKey, instanceId);
    end
end
return instances;



