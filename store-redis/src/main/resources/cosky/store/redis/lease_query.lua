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
local limit = tonumber(ARGV[1])
-- Leases are ordered by expiry, and one with a long TTL can still be waiting when one behind it with a short TTL is
-- ready: reading on past those keeps them from holding it back. The bound keeps a mass expiry from making one call long;
-- what it leaves unread becomes ready within a third of the longest TTL anyway.
local page = 256
local max_read = 10000
local ready = {}
local read = 0
while #ready < limit and read < max_read do
  local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now, 'WITHSCORES', 'LIMIT', read, page)
  if #expired == 0 then
    break
  end
  local ids = {}
  for i = 1, #expired, 2 do
    ids[#ids + 1] = expired[i]
  end
  local ttls = redis.call('HMGET', KEYS[2], unpack(ids))
  for j = 1, #ids do
    if #ready < limit and tonumber(expired[2 * j]) + tonumber(ttls[j] or '0') / 3 <= tonumber(now) then
      ready[#ready + 1] = ids[j]
    end
  end
  read = read + #ids
  if #ids < page then
    break
  end
end
return {
  string.format('%d', redis.call('ZCARD', KEYS[1])),
  string.format('%d', redis.call('ZCOUNT', KEYS[1], '-inf', now)),
  ready,
}
