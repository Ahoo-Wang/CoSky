local namespace = KEYS[1];
local configId = ARGV[1];
local versionField = "version";

local configIdxKey = namespace .. ":cfg_idx";
local configHistoryIdxKey = namespace .. ":cfg_htr_idx:" .. configId;
local configKey = namespace .. ":cfg:" .. configId;

local removed = redis.call("srem", configIdxKey, configKey);
if removed == 0 then
    return 0;
end

local function getConfigHistoryKey(version)
    return namespace .. ":cfg_htr:" .. configId .. ":" .. tostring(version);
end

local function addHistory(preVersion, configKey)
    local configHistoryKey = getConfigHistoryKey(preVersion);
    redis.call("zadd", configHistoryIdxKey, preVersion, configHistoryKey);
    redis.call("rename", configKey, configHistoryKey);
    local opTime = redis.call('time')[1];
    return redis.call("hmset", configHistoryKey, "op", "remove", "opTime", opTime);
end

local preVersion = redis.call("hget", configKey, versionField)
if preVersion then
    local result = addHistory(preVersion, configKey);
    redis.call("publish", configKey, "remove");
    return result;
else
    return 0;
end


