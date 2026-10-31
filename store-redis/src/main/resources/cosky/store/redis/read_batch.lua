-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- One batch of a full read: each object comes with its metadata atomically, so the two always match.
-- KEYS: state, ver, obj
-- ARGV: cursor, count
if redis.call('GET', KEYS[1]) ~= 'active' then
  return {'NOT_ACTIVE'}
end
local scan = redis.call('HSCAN', KEYS[2], ARGV[1], 'COUNT', ARGV[2])
local out = {scan[1]}
for i = 1, #scan[2], 2 do
  out[#out + 1] = scan[2][i]
  out[#out + 1] = scan[2][i + 1]
  out[#out + 1] = redis.call('HGET', KEYS[3], scan[2][i])
end
return out
