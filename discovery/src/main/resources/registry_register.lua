local namespace = KEYS[1];
local instanceTtl = KEYS[2];
local fixed = instanceTtl == "-1";
local serviceId = KEYS[3];
local instanceId = KEYS[4];
local schema = KEYS[5];
local ip = KEYS[6];
local port = KEYS[7];
local weight = KEYS[8];
local ephemeral;
if fixed then
    ephemeral = "false"
else
    ephemeral = "true"
end
local serviceIdxKey = namespace .. ":svc_idx";
local instanceIdxKey = namespace .. ":svc_itc_idx:" .. serviceId;
local instanceKey = namespace .. ":svc_itc:" .. instanceId;

local added = redis.call("sadd", instanceIdxKey, instanceId);

if added == 1 then
    redis.call("publish", serviceIdxKey, "register");
    redis.call("sadd", serviceIdxKey, serviceId);
end
redis.call("hmset", instanceKey, "instanceId", instanceId, "serviceId", serviceId, "schema", schema, "ip", ip, "port", port, "weight", weight, "ephemeral", ephemeral, unpack(ARGV));
redis.call("publish", instanceKey, "register");
if not fixed then
    return redis.call("expire", instanceKey, instanceTtl);
end
return 1;
