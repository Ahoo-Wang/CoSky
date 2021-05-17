local namespace = KEYS[1];
local configId = ARGV[1];
local data = ARGV[2];
local hash = ARGV[3];
local version = 1;
local versionField = "version";
local hashField = "hash";
local configIdxKey = namespace .. ":cfg_idx";
local configHistoryIdxKey = namespace .. ":cfg_htr_idx:" .. configId;
local configKey = namespace .. ":cfg:" .. configId;
local preHash = redis.call("hget", configKey, hashField)
if (preHash ~= nil) and (preHash == hash) then
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
    return redis.call("hmset", configHistoryKey, "op", "set", "opTime", opTime);
end

redis.call("sadd", configIdxKey, configKey)
local preVersion = redis.call("hget", configKey, versionField)
if preVersion then
    version = preVersion + 1;
    addHistory(preVersion, configKey);
end

local createTime = redis.call('time')[1];
local result = redis.call("hmset", configKey, "configId", configId, "data", data, "hash", hash, "version", version, "createTime", createTime);
redis.call("publish", configKey, "set");
return result;
