-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Removes a lease and, in one revision, the Instances attached to it. Revoke sets force.
-- KEYS: rev, log, lease, lease:ttl, lease:owner, lease:attach, obj:instance, ver:instance
-- ARGV: namespace, actor, lease, force, log_size
local t = redis.call('TIME')
local now = t[1] .. string.format('%03d', math.floor(tonumber(t[2]) / 1000))
local expiry = redis.call('ZSCORE', KEYS[3], ARGV[3])
if not expiry then
  return {'NONE'}
end
if ARGV[4] ~= '1' and tonumber(expiry) > tonumber(now) then
  return {'ALIVE'}
end
local names = redis.call('ZRANGEBYSCORE', KEYS[6], ARGV[3], ARGV[3])
if #names > 0 then
  local rev = string.format('%d', redis.call('INCR', KEYS[1]))
  for i, name in ipairs(names) do
    local c, cm = string.match(redis.call('HGET', KEYS[8], name), '^%d+:(%d+):(%d+):')
    local o = redis.call('HGET', KEYS[7], name)
    redis.call('HDEL', KEYS[7], name)
    redis.call('HDEL', KEYS[8], name)
    redis.call('XADD', KEYS[2], 'MAXLEN', '~', ARGV[5], rev .. '-' .. (i - 1),
      't', 'D', 'k', 'instance', 'n', name,
      'm', rev .. ':' .. c .. ':' .. cm .. ':' .. now .. ':' .. ARGV[2], 'o', o)
  end
  redis.call('ZREMRANGEBYSCORE', KEYS[6], ARGV[3], ARGV[3])
  redis.call('PUBLISH', 'cosky:hint', ARGV[1])
end
redis.call('ZREM', KEYS[3], ARGV[3])
redis.call('HDEL', KEYS[4], ARGV[3])
redis.call('HDEL', KEYS[5], ARGV[3])
return {'EXPIRED'}
