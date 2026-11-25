-- Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
-- Licensed under the Apache License, Version 2.0.
-- You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
-- Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
-- specific language governing permissions and limitations under the License.

-- Replaces the epoch if it is still the expected one, so that replicas finding the same fork rotate it once (§6.4).
-- Only of an active namespace: a deletion terminates and drops one by its epoch (D43), and a rotation in between would
-- leave the drop nothing to do, and the data of the namespace behind.
-- KEYS: state, epoch, rev
-- ARGV: expected, next, namespace
if redis.call('GET', KEYS[1]) == 'active' and redis.call('GET', KEYS[2]) == ARGV[1] then
  redis.call('SET', KEYS[2], ARGV[2])
  redis.call('PUBLISH', 'cosky:hint', ARGV[3])
end
return {redis.call('GET', KEYS[2]) or '', redis.call('GET', KEYS[3]) or '0'}
