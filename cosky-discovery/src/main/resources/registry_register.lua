local namespace = KEYS[1];
local instanceTtl = ARGV[1];
local fixed = instanceTtl == "-1";
local serviceId = ARGV[2];
local instanceId = ARGV[3];
local schema = ARGV[4];
local host = ARGV[5];
local port = ARGV[6];
local weight = ARGV[7];
local ephemeral;
if fixed then
    ephemeral = "false"
else
    ephemeral = "true"
end
local serviceIdxKey = namespace .. ":svc_idx";
local instanceIdxKey = namespace .. ":svc_itc_idx:" .. serviceId;
local instanceKey = namespace .. ":svc_itc:" .. instanceId;
local serviceIdxStatKey = namespace .. ":svc_stat";

local added = redis.call("sadd", instanceIdxKey, instanceId);

if added == 1 then
    local affected = redis.call("sadd", serviceIdxKey, serviceId);

    if affected > 0 then
        redis.call("publish", serviceIdxKey, "set");
        redis.call("hset", serviceIdxStatKey, serviceId, 0);
    end
end

local instanceKeys = redis.call("hkeys", instanceKey)
if #instanceKeys > 0 then
    for i, key in ipairs(instanceKeys) do
        if string.find(key, '_', 1) == 1 and string.find(key, '__', 1) == nil then
            redis.call("hdel", instanceKey, key);
        end
    end
end

redis.call("hmset", instanceKey, "instanceId", instanceId, "serviceId", serviceId, "schema", schema, "host", host, "port", port, "weight", weight, "ephemeral", ephemeral, unpack(ARGV, 8, #ARGV));
redis.call("publish", instanceKey, "register");
if not fixed then
    return redis.call("expire", instanceKey, instanceTtl);
end
return 1;
