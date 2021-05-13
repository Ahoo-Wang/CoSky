local namespace = KEYS[1];
local instanceId = ARGV[1];

local instanceKey = namespace .. ":svc_itc:" .. instanceId;

local result = redis.call("hmset", instanceKey, unpack(ARGV, 2, #ARGV));
redis.call("publish", instanceKey, "set_metadata");
return result;

