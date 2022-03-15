local prefix = KEYS[1]
local clearKeyPattern = prefix .. "*"
local allKeys = redis.call('keys', clearKeyPattern);
for idx, key in ipairs(allKeys) do
    redis.call('del', key)
end
