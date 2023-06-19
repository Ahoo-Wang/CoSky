local namespace = KEYS[1];
local instanceId = ARGV[1];

local instanceKey = namespace .. ":svc_itc:" .. instanceId;

local instanceKeys = redis.call("hkeys", instanceKey)
if #instanceKeys == 0 then
    return 0
end
if #instanceKeys > 0 then
    for i, key in ipairs(instanceKeys) do
        if string.find(key, '_', 1) == 1 and string.find(key, '__', 1) == nil then
            redis.call("hdel", instanceKey, key);
        end
    end
end

redis.call("hmset", instanceKey, unpack(ARGV, 2, #ARGV));
redis.call("publish", instanceKey, "set_metadata");
return 1;

