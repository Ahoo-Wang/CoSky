-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Refreshes topology edges (design D30): soft state, with no revision and no log entry.
-- KEYS: state, topology
-- ARGV: retention in ms, then the edges
if redis.call('GET', KEYS[1]) ~= 'active' then
  return 0
end
local t = redis.call('TIME')
local now = tonumber(t[1] .. string.format('%03d', math.floor(tonumber(t[2]) / 1000)))
for i = 2, #ARGV do
  redis.call('ZADD', KEYS[2], now + tonumber(ARGV[1]), ARGV[i])
end
redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
return 1
