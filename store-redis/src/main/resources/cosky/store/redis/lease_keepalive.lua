-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Renews leases by their own TTL. An expired lease not reaped yet renews too: expiry only makes it reapable (§6.2).
-- KEYS: lease, lease:ttl
-- ARGV: lease ids
local t = redis.call('TIME')
local now = t[1] .. string.format('%03d', math.floor(tonumber(t[2]) / 1000))
local out = {}
for i, id in ipairs(ARGV) do
  local ttl = redis.call('HGET', KEYS[2], id)
  if ttl then
    redis.call('ZADD', KEYS[1], tonumber(now) + tonumber(ttl), id)
    out[i] = ttl
  else
    out[i] = ''
  end
end
return out
