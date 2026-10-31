-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- One atomic read of a follower (design §6.1, §6.4).
-- KEYS: state, epoch, rev, log
-- ARGV: known_epoch, applied, applied_m ('' when not known yet), count
if redis.call('GET', KEYS[1]) ~= 'active' then
  return {'NOT_ACTIVE'}
end
local epoch = redis.call('GET', KEYS[2]) or ''
local rev = redis.call('GET', KEYS[3]) or '0'
if epoch ~= ARGV[1] then
  return {'EPOCH', epoch, rev}
end
local applied = tonumber(ARGV[2])
if tonumber(rev) < applied then
  return {'DIVERGED'}
end
local first = redis.call('XRANGE', KEYS[4], '-', '+', 'COUNT', 1)[1]
if first then
  local r, s = string.match(first[1], '^(%d+)-(%d+)$')
  -- A trim may cut through the oldest revision, which then cannot be resumed from: its batch is incomplete.
  local earliest = tonumber(r)
  if tonumber(s) == 0 then earliest = earliest - 1 end
  if applied < earliest then
    return {'GONE', string.format('%d', earliest)}
  end
end
local m = ''
if applied > 0 then
  local entry = redis.call('XRANGE', KEYS[4], ARGV[2] .. '-0', ARGV[2] .. '-0')[1]
  if entry then
    for i = 1, #entry[2], 2 do
      if entry[2][i] == 'm' then m = entry[2][i + 1] end
    end
    if ARGV[3] ~= '' and m ~= ARGV[3] then
      return {'DIVERGED'}
    end
  end
end
return {'OK', rev, m, redis.call('XRANGE', KEYS[4], string.format('%d', applied + 1) .. '-0', '+', 'COUNT', ARGV[4])}
