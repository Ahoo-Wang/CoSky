local namespace = KEYS[1];
local instanceId = KEYS[2];

local instanceKey = namespace .. ":svc_itc:" .. instanceId;

local result = redis.call("hmset", instanceKey, unpack(ARGV));
redis.call("publish", instanceKey, "set_metadata");
return result;

