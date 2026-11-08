-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Atomically checks that a namespace is still the one a deletion began on and empty, and stops writes to it (design
-- §7.2). One created again since, or whose epoch was replaced, has another epoch and is left alone.
-- KEYS: state, epoch, obj:config, obj:instance
-- ARGV: namespace, epoch
local state = redis.call('GET', KEYS[1])
if not state then
  return 'NOT_FOUND'
end
if state == 'active' then
  if redis.call('GET', KEYS[2]) ~= ARGV[2] then
    return 'SUPERSEDED'
  end
  if redis.call('HLEN', KEYS[3]) > 0 or redis.call('HLEN', KEYS[4]) > 0 then
    return 'NOT_EMPTY'
  end
  redis.call('SET', KEYS[1], 'terminating')
  redis.call('PUBLISH', 'cosky:hint', ARGV[1])
end
return 'TERMINATED'
