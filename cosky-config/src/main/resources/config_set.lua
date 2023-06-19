-- Note: starting with Redis 5, the replication method described in this section (scripts effects replication) is the default and does not need to be explicitly enabled.
redis.replicate_commands();

local namespace = KEYS[1];
local configId = ARGV[1];
local data = ARGV[2];
local hash = ARGV[3];
local op = 'set';
local nextVersion = 1;
local versionField = "version";
local hashField = "hash";
local configIdxKey = namespace .. ":cfg_idx";
local configHistoryIdxKey = namespace .. ":cfg_htr_idx:" .. configId;
local configKey = namespace .. ":cfg:" .. configId;
local currentHash = redis.call("hget", configKey, hashField);
if (currentHash ~= nil) and (currentHash == hash) then
    return 0;
end

local function getConfigHistoryKey(version)
    return namespace .. ":cfg_htr:" .. configId .. ":" .. tostring(version);
end

local function addHistory(currentVersion, configKey, op)
    local configHistoryKey = getConfigHistoryKey(currentVersion);
    redis.call("zadd", configHistoryIdxKey, currentVersion, configHistoryKey);
    redis.call("rename", configKey, configHistoryKey);
    local opTime = redis.call('time')[1];
    return redis.call("hmset", configHistoryKey, 'op', op, "opTime", opTime);
end

redis.call("sadd", configIdxKey, configKey)

local currentVersion = redis.call("hget", configKey, versionField)
if currentVersion then
    nextVersion = currentVersion + 1;
    addHistory(currentVersion, configKey, op);
else
    local lastHistoryVersion = redis.call('zrevrange', configHistoryIdxKey, 0, 0, 'WITHSCORES')
    if #lastHistoryVersion > 0 then
        nextVersion = lastHistoryVersion[2] + 1;
    end
end

local createTime = redis.call('time')[1];
redis.call("hmset", configKey, "configId", configId, "data", data, hashField, hash, versionField, nextVersion, "createTime", createTime);
redis.call("publish", configKey, op);
return 1;
