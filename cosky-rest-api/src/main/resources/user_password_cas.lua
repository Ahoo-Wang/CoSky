-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Replaces the password hash of a user only if it is still the one the caller verified.
local userIdxKey = KEYS[1];
local username = ARGV[1];
local expectedHash = ARGV[2];
local newHash = ARGV[3];

if redis.call('hget', userIdxKey, username) ~= expectedHash then
    return 0;
end
redis.call('hset', userIdxKey, username, newHash);
return 1;
