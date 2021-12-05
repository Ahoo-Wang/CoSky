-- Note: starting with Redis 5, the replication method described in this section (scripts effects replication) is the default and does not need to be explicitly enabled.
redis.replicate_commands();

local namespace = KEYS[1];
local configId = ARGV[1];
local op = 'remove';
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

local function addHistory(currentVersion, configKey, op)
    local configHistoryKey = getConfigHistoryKey(currentVersion);
    redis.call("zadd", configHistoryIdxKey, currentVersion, configHistoryKey);
    redis.call("rename", configKey, configHistoryKey);
    local opTime = redis.call('time')[1];
    return redis.call("hmset", configHistoryKey, 'op', op, "opTime", opTime);
end

local currentVersion = redis.call("hget", configKey, versionField)
if currentVersion then
    local result = addHistory(currentVersion, configKey, op);
    redis.call("publish", configKey, op);
    return result;
else
    return 0;
end


