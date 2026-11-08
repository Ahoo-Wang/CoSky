-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Removes one batch of what a terminating namespace holds (design §7.2), as long as it is still the one under the epoch
-- the removal is for: another replica may have created the namespace again since, and nothing of it is for this to
-- delete. The epoch itself goes last, so that a removal cut short can be taken up again.
-- KEYS: state, epoch, then the keys to remove
-- ARGV: epoch
if redis.call('GET', KEYS[1]) == 'active' or redis.call('GET', KEYS[2]) ~= ARGV[1] then
  return 0
end
return redis.call('UNLINK', unpack(KEYS, 3))
