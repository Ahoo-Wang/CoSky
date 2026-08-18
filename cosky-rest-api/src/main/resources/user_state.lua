-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

redis.replicate_commands();

local userIdxKey = KEYS[1];
local userRoleBindKey = KEYS[2];
local loginLockKey = KEYS[3];
local operation = ARGV[1];
local username = ARGV[2];
local manualLockMarker = ARGV[3];

if operation == "lock" then
    if redis.call("hexists", userIdxKey, username) == 0 then
        return 0;
    end
    redis.call("set", loginLockKey, manualLockMarker);
    return 1;
end

if operation == "remove" then
    local removed = redis.call("hdel", userIdxKey, username);
    redis.call("del", userRoleBindKey, loginLockKey);
    return removed;
end

if operation == "login-attempt" then
    if redis.call("get", loginLockKey) == manualLockMarker then
        return -1;
    end
    local tryCount = redis.call("incr", loginLockKey);
    if tryCount <= tonumber(ARGV[4]) then
        redis.call("pexpire", loginLockKey, ARGV[5]);
    end
    return tryCount;
end

if operation == "login-success" then
    if redis.call("get", loginLockKey) == manualLockMarker then
        return -1;
    end
    return redis.call("del", loginLockKey);
end

return redis.error_reply("Unsupported user state operation: " .. operation);
