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

-- What this script has added to the log, for [logTrim] to take out again.
local logAddedBytes = 0

local function logAdd(log, bytes, id, ...)
  redis.call('XADD', log, id, ...)
  local size = logEntrySize({...})
  redis.call('INCRBY', bytes, size)
  logAddedBytes = logAddedBytes + size
end

-- Removes the oldest entries while over a limit; what was just written stays however large it is. It removes at least
-- as many bytes as were written, so that a write that found the log within its limits leaves it there whatever the
-- sizes of what it takes out: small entries going for a large one must not let the log grow past its byte limit. A log
-- far over, as after lowering a limit, goes down over several writes rather than holding up Redis in one: this removes
-- a few more entries than were added, so it gets there.
local function logTrim(log, bytes, maxEntries, maxBytes, revision, added)
  local entries = redis.call('XLEN', log)
  local size = tonumber(redis.call('GET', bytes) or '0')
  local budget = added * 2 + 64
  local owed = logAddedBytes
  local removed = 0
  local function going()
    return (entries > maxEntries or size > maxBytes) and (budget > 0 or owed > 0)
  end
  while going() do
    local batch = redis.call('XRANGE', log, '-', '+', 'COUNT', 128)
    local ids = {}
    for _, entry in ipairs(batch) do
      if not going() or string.match(entry[1], '^(%d+)-') == revision then
        break
      end
      local entrySize = logEntrySize(entry[2])
      ids[#ids + 1] = entry[1]
      entries = entries - 1
      size = size - entrySize
      removed = removed + entrySize
      owed = owed - entrySize
      budget = budget - 1
    end
    if #ids > 0 then
      redis.call('XDEL', log, unpack(ids))
    end
    if #ids < #batch or #batch == 0 then
      break
    end
  end
  if removed > 0 and redis.call('DECRBY', bytes, removed) < 0 then
    redis.call('SET', bytes, '0')
  end
end
