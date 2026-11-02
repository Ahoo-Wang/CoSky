-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distri
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
-- specific language governing permissions and limitations under the License.

-- What the scripts that write the log share (design D38). They trim it themselves, because MAXLEN does not say what it
-- removed, and the log is limited in bytes as well as in entries.
local function logEntrySize(fields)
  local size = 0
  for i = 1, #fields do
    size = size + #fields[i]
  end
  return size
end

local function logAdd(log, bytes, id, ...)
  redis.call('XADD', log, id, ...)
  redis.call('INCRBY', bytes, logEntrySize({...}))
end

-- Removes the oldest entries while over a limit; what was just written stays however large it is. A log far over, as
-- after lowering a limit, goes down over several writes rather than holding up Redis in one: this removes a little more
-- than was added, so it gets there.
local function logTrim(log, bytes, maxEntries, maxBytes, revision, added)
  local budget = added * 2 + 64
  while budget > 0 and (redis.call('XLEN', log) > maxEntries or tonumber(redis.call('GET', bytes) or '0') > maxBytes) do
    local oldest = redis.call('XRANGE', log, '-', '+', 'COUNT', 1)[1]
    if not oldest or string.match(oldest[1], '^(%d+)-') == revision then
      return
    end
    redis.call('XDEL', log, oldest[1])
    if redis.call('DECRBY', bytes, logEntrySize(oldest[2])) < 0 then
      redis.call('SET', bytes, '0')
    end
    budget = budget - 1
  end
end
