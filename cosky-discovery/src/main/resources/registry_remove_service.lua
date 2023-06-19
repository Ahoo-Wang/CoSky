local namespace = KEYS[1];
local serviceId = ARGV[1];

local serviceIdxKey = namespace .. ":svc_idx";
local serviceIdxStatKey = namespace .. ":svc_stat";
local instanceIdxKey = namespace .. ":svc_itc_idx:" .. serviceId;

local instanceCount = redis.call("scard", instanceIdxKey);
if instanceCount > 0 then
    return 0;
end

local affected = redis.call("srem", serviceIdxKey, serviceId);

if affected > 0 then
    redis.call("publish", serviceIdxKey, "remove");
    redis.call("hdel", serviceIdxStatKey, serviceId);
end

return 1;
