--consumer namespace
local namespace = KEYS[1];

local topology = {  };
local topologyIdxKey = namespace .. ':topology_idx';

local consumerNames = redis.call('hkeys', topologyIdxKey)

for idx, consumerName in ipairs(consumerNames) do
    local topologyDependencyKey = namespace .. ':topology:' .. consumerName;
    local consumerDeps = redis.call('hkeys', topologyDependencyKey);
    topology[#topology+1] = consumerName;
    topology[#topology+1] = consumerDeps;
end

return topology;
