local namespace = KEYS[1];
local configId = ARGV[1];
local targetVersion = ARGV[2];
local versionField = "version";
local hashField = "hash";
local configIdxKey = namespace .. ":cfg_idx";
local configHistoryIdxKey = namespace .. ":cfg_htr_idx:" .. configId;
local configKey = namespace .. ":cfg:" .. configId;

local function getConfigHistoryKey(version)
    return namespace .. ":cfg_htr:" .. configId .. ":" .. tostring(version);
end

local targetConfigHistoryKey = getConfigHistoryKey(targetVersion);
local targetHistoryConfigArray = redis.call("hgetall", targetConfigHistoryKey)
if #targetHistoryConfigArray == 0 then
    return 0;
end

local function arrayToDictionary(arrayTable)
    local dicTable = {};
    for idx, val in ipairs(arrayTable) do
        if idx % 2 == 1 then
            dicTable[val] = arrayTable[idx + 1]
        end
    end
    return dicTable;
end

local function addHistory(preVersion, configKey)
    local configHistoryKey = getConfigHistoryKey(preVersion);
    redis.call("zadd", configHistoryIdxKey, preVersion, configHistoryKey);
    redis.call("rename", configKey, configHistoryKey);
    local opTime = redis.call('time')[1];
    return redis.call("hmset", configHistoryKey, "op", "rollback", "opTime", opTime);
end

local targetHistoryConfig = arrayToDictionary(targetHistoryConfigArray);

local hash = targetHistoryConfig[hashField];

local preHash = redis.call("hget", configKey, hashField)
if (preHash ~= nil) and (preHash == hash) then
    return 0;
end
redis.call("sadd", configIdxKey, configKey)

local preVersion = redis.call("hget", configKey, versionField)
local version = preVersion + 1;
addHistory(preVersion, configKey)
local data = targetHistoryConfig["data"];
local createTime = redis.call('time')[1];

local result = redis.call("hmset", configKey, "configId", configId, "data", data, "hash", hash, "version", version, "createTime", createTime);
redis.call("publish", configKey, "rollback");
return result;
