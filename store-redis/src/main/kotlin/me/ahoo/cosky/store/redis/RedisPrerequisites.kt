/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosky.store.redis

/**
 * @param problems what makes this Redis unfit.
 * @param unknown the fields `INFO` did not report, which could not be checked.
 */
internal data class RedisCheck(val problems: List<String>, val unknown: List<String>)

/**
 * What the store needs from Redis (design §5.3, D31), judged from the output of `INFO`.
 */
internal object RedisPrerequisites {
    private const val EVICTION_POLICY = "maxmemory_policy"
    private const val REQUIRED_EVICTION_POLICY = "noeviction"
    private const val VERSION = "redis_version"
    private const val MIN_MAJOR_VERSION = 7

    fun check(info: String): RedisCheck {
        val fields = info.lineSequence()
            .map { it.trim() }
            .filter { ':' in it && !it.startsWith('#') }
            .associate { it.substringBefore(':') to it.substringAfter(':') }
        return RedisCheck(
            problems = listOfNotNull(evictionProblem(fields[EVICTION_POLICY]), versionProblem(fields[VERSION])),
            unknown = listOf(EVICTION_POLICY, VERSION).filter { it !in fields },
        )
    }

    private fun evictionProblem(policy: String?): String? = policy
        ?.takeIf { it != REQUIRED_EVICTION_POLICY }
        ?.let { "$EVICTION_POLICY is $it, and eviction silently deletes data; it must be $REQUIRED_EVICTION_POLICY." }

    private fun versionProblem(version: String?): String? = version
        ?.takeIf { (it.substringBefore('.').toIntOrNull() ?: 0) < MIN_MAJOR_VERSION }
        ?.let { "Version $it is older than the required $MIN_MAJOR_VERSION.0." }
}
