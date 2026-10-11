-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Shared by the config scripts. The key functions must stay in sync with ConfigKeyGenerator (see ConfigLuaKeysTest).

local function configHistoryKeyOf(namespace, configId, version)
    return namespace .. ':cfg_htr:' .. configId .. ':' .. tostring(version);
end

-- Deletes the history beyond the newest historySize versions. The members of the history index are history keys.
local function trimHistory(configHistoryIdxKey, historySize)
    local staleStop = -(tonumber(historySize) + 1);
    for _, staleHistoryKey in ipairs(redis.call('zrange', configHistoryIdxKey, 0, staleStop)) do
        redis.call('del', staleHistoryKey);
    end
    redis.call('zremrangebyrank', configHistoryIdxKey, 0, staleStop);
end

-- Moves the current config to the history of its version, keeping the newest historySize versions.
local function archiveConfig(namespace, configId, configKey, configHistoryIdxKey, currentVersion, op, historySize)
    local configHistoryKey = configHistoryKeyOf(namespace, configId, currentVersion);
    redis.call('zadd', configHistoryIdxKey, currentVersion, configHistoryKey);
    redis.call('rename', configKey, configHistoryKey);
    redis.call('hmset', configHistoryKey, 'op', op, 'opTime', redis.call('time')[1]);
    trimHistory(configHistoryIdxKey, historySize);
end

-- Archives the current config, if any, and returns the version the next config takes.
local function archiveAndNextVersion(namespace, configId, configKey, configHistoryIdxKey, op, historySize)
    local currentVersion = redis.call('hget', configKey, 'version');
    if currentVersion then
        archiveConfig(namespace, configId, configKey, configHistoryIdxKey, currentVersion, op, historySize);
        return currentVersion + 1;
    end
    local lastHistoryVersion = redis.call('zrevrange', configHistoryIdxKey, 0, 0, 'WITHSCORES');
    if #lastHistoryVersion > 0 then
        return lastHistoryVersion[2] + 1;
    end
    return 1;
end

local function writeConfig(configKey, configId, data, hash, version, op)
    redis.call('hmset', configKey, 'configId', configId, 'data', data, 'hash', hash, 'version', version,
        'createTime', redis.call('time')[1]);
    redis.call('publish', configKey, op);
end
