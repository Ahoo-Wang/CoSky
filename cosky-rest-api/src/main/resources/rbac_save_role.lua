-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Saves a role and replaces its resource bindings atomically, then announces the change on the bindings key.
local roleIdxKey = KEYS[1];
local roleResourceBindKey = KEYS[2];
local roleName = ARGV[1];
local roleDesc = ARGV[2];

redis.call('hset', roleIdxKey, roleName, roleDesc);
redis.call('del', roleResourceBindKey);
if #ARGV > 2 then
    redis.call('hset', roleResourceBindKey, unpack(ARGV, 3, #ARGV));
end
redis.call('publish', roleResourceBindKey, 'save');
return 1;
