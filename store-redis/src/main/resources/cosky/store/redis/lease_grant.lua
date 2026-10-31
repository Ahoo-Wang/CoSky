-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- KEYS: state, lease, lease:ttl, lease:owner, lease:seq
-- ARGV: ttl_ms, owner
if redis.call('GET', KEYS[1]) ~= 'active' then
  return {'NAMESPACE_NOT_ACTIVE'}
end
local t = redis.call('TIME')
local now = t[1] .. string.format('%03d', math.floor(tonumber(t[2]) / 1000))
local id = string.format('%d', redis.call('INCR', KEYS[5]))
redis.call('ZADD', KEYS[2], tonumber(now) + tonumber(ARGV[1]), id)
redis.call('HSET', KEYS[3], id, ARGV[1])
redis.call('HSET', KEYS[4], id, ARGV[2])
return {'OK', id}
