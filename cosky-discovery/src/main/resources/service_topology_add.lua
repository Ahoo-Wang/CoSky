--consumer namespace
local namespace = KEYS[1];
local consumerName = ARGV[1];
local producerName = ARGV[2];

local nowTime = redis.call('time')[1];

local topologyIdxKey = namespace .. ':topology_idx';
redis.call('hsetnx', topologyIdxKey, consumerName, nowTime)

local topologyDependencyKey = namespace .. ':topology:' .. consumerName;

redis.call('hsetnx', topologyDependencyKey, producerName, nowTime);
