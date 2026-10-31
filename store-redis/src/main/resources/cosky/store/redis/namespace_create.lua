-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- KEYS: state, epoch, rev
-- ARGV: new_epoch
if redis.call('GET', KEYS[1]) ~= 'active' then
  redis.call('SET', KEYS[1], 'active')
  redis.call('SET', KEYS[2], ARGV[1])
end
return {redis.call('GET', KEYS[2]), redis.call('GET', KEYS[3]) or '0'}
