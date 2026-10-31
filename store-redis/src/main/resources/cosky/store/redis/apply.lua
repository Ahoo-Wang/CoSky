-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Applies a batch atomically (design §5.2). A revision's log entries are <revision>-<seq>.
-- KEYS: state, rev, log, lease, lease:attach, hist:idx, then per op: obj, ver, hist
-- ARGV: namespace, actor, n_ops, log_size, history_size, then per op: type, kind, name, expected_rev, bytes, lease
if redis.call('GET', KEYS[1]) ~= 'active' then
  return {'NAMESPACE_NOT_ACTIVE'}
end
local t = redis.call('TIME')
local now = t[1] .. string.format('%03d', math.floor(tonumber(t[2]) / 1000))
local function parse(v)
  if not v then return nil end
  local r, c, cm = string.match(v, '^(%d+):(%d+):(%d+):')
  return {rev = r, createRev = c, createdMs = cm}
end
local n = tonumber(ARGV[3])
local changed = {}
for i = 1, n do
  local a = 5 + (i - 1) * 6
  local k = 6 + (i - 1) * 3
  local op = {type = ARGV[a + 1], kind = ARGV[a + 2], name = ARGV[a + 3], expected = tonumber(ARGV[a + 4]),
              bytes = ARGV[a + 5], lease = ARGV[a + 6], obj = KEYS[k + 1], ver = KEYS[k + 2], hist = KEYS[k + 3]}
  op.cur = parse(redis.call('HGET', op.ver, op.name))
  local curRev = op.cur and tonumber(op.cur.rev) or nil
  if (op.expected == -1 and curRev) or (op.expected > 0 and op.expected ~= curRev) then
    return {'CONFLICT', tostring(i - 1), op.cur and op.cur.rev or ''}
  end
  if op.type == 'P' and op.lease ~= '0' then
    local expiry = redis.call('ZSCORE', KEYS[4], op.lease)
    if not expiry or tonumber(expiry) <= tonumber(now) then
      return {'LEASE_NOT_FOUND', tostring(i - 1), op.lease}
    end
  end
  local unchanged = (op.type == 'P' and redis.call('HGET', op.obj, op.name) == op.bytes)
    or (op.type == 'D' and not op.cur)
  if not unchanged then
    changed[#changed + 1] = op
  end
end
if #changed == 0 then
  return {'OK', redis.call('GET', KEYS[2]) or '0', '0'}
end
local rev = string.format('%d', redis.call('INCR', KEYS[2]))
for i, op in ipairs(changed) do
  local createRev = op.cur and op.cur.createRev or rev
  local createdMs = op.cur and op.cur.createdMs or now
  local m = rev .. ':' .. createRev .. ':' .. createdMs .. ':' .. now .. ':' .. ARGV[2]
  local o = op.bytes
  if op.type == 'P' then
    redis.call('HSET', op.obj, op.name, op.bytes)
    redis.call('HSET', op.ver, op.name, m)
    if op.kind == 'instance' then
      if op.lease ~= '0' then
        redis.call('ZADD', KEYS[5], op.lease, op.name)
      else
        redis.call('ZREM', KEYS[5], op.name)
      end
    end
    if op.kind == 'config' then
      redis.call('ZADD', op.hist, rev, m .. '\0' .. op.bytes)
      redis.call('ZREMRANGEBYRANK', op.hist, 0, -(tonumber(ARGV[5]) + 1))
      redis.call('SADD', KEYS[6], op.name)
    end
  else
    o = redis.call('HGET', op.obj, op.name)
    redis.call('HDEL', op.obj, op.name)
    redis.call('HDEL', op.ver, op.name)
    if op.kind == 'instance' then
      redis.call('ZREM', KEYS[5], op.name)
    end
  end
  redis.call('XADD', KEYS[3], 'MAXLEN', '~', ARGV[4], rev .. '-' .. (i - 1),
    't', op.type, 'k', op.kind, 'n', op.name, 'm', m, 'o', o)
end
redis.call('PUBLISH', 'cosky:hint', ARGV[1])
return {'OK', rev, tostring(#changed)}
