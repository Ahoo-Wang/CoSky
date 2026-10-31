-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Lists leases ready to be reaped: those a third of their TTL past expiry (design D32).
-- KEYS: lease, lease:ttl
-- ARGV: limit
local t = redis.call('TIME')
local now = t[1] .. string.format('%03d', math.floor(tonumber(t[2]) / 1000))
local ready = {}
local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now, 'WITHSCORES', 'LIMIT', 0, ARGV[1])
for i = 1, #expired, 2 do
  local ttl = tonumber(redis.call('HGET', KEYS[2], expired[i]) or '0')
  if tonumber(expired[i + 1]) + ttl / 3 <= tonumber(now) then
    ready[#ready + 1] = expired[i]
  end
end
return {
  string.format('%d', redis.call('ZCARD', KEYS[1])),
  string.format('%d', redis.call('ZCOUNT', KEYS[1], '-inf', now)),
  ready,
}
