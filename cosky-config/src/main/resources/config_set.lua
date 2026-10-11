-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

redis.replicate_commands();
-- @include cosky/config/config_lib.lua

local configIdxKey = KEYS[1];
local configKey = KEYS[2];
local configHistoryIdxKey = KEYS[3];
local namespace = ARGV[1];
local configId = ARGV[2];
local data = ARGV[3];
local hash = ARGV[4];
local op = 'set';

if redis.call('hget', configKey, 'hash') == hash then
    return 0;
end

redis.call('sadd', configIdxKey, configKey);
local nextVersion = archiveAndNextVersion(namespace, configId, configKey, configHistoryIdxKey, op);
writeConfig(configKey, configId, data, hash, nextVersion, op);
return 1;
