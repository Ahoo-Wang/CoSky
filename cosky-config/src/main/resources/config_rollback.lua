-- Note: starting with Redis 5, the replication method described in this section (scripts effects replication) is the default and does not need to be explicitly enabled.
redis.replicate_commands();

local namespace = KEYS[1];
local configId = ARGV[1];
local targetVersion = ARGV[2];
local op = 'rollback';
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

local function addHistory(currentVersion, configKey, op)
    local configHistoryKey = getConfigHistoryKey(currentVersion);
    redis.call("zadd", configHistoryIdxKey, currentVersion, configHistoryKey);
    redis.call("rename", configKey, configHistoryKey);
    local opTime = redis.call('time')[1];
    return redis.call("hmset", configHistoryKey, 'op', op, "opTime", opTime);
end

local targetHistoryConfig = arrayToDictionary(targetHistoryConfigArray);

local targetHash = targetHistoryConfig[hashField];

local currentHash = redis.call("hget", configKey, hashField)
if (currentHash ~= nil) and (currentHash == targetHash) then
    return 0;
end
redis.call("sadd", configIdxKey, configKey, configKey)

local currentVersion = redis.call("hget", configKey, versionField)
local nextVersion = currentVersion + 1;
addHistory(currentVersion, configKey, op)
local data = targetHistoryConfig["data"];
local createTime = redis.call('time')[1];

redis.call("hmset", configKey, "configId", configId, "data", data, hashField, targetHash, versionField, nextVersion, "createTime", createTime);
redis.call("publish", configKey, op);
return 1;
