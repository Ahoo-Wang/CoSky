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

package me.ahoo.cosky.client

import kotlinx.coroutines.flow.StateFlow
import me.ahoo.cosky.api.v1.Resource
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger

/**
 * Picks an instance from the local view. It never suspends: picking must stay cheap on a request path (design §6.5).
 */
fun interface LoadBalancer {
    /**
     * @return null when no instance can take traffic.
     */
    fun choose(): Resource?
}

/**
 * Weight 0 drains an instance: it stays registered and takes no traffic. An unset weight means 1.
 */
val Resource.effectiveWeight: Int
    get() = if (instance.hasWeight()) instance.weight else 1

/**
 * Picks in proportion to weight, with a binary search over cumulative weights that are computed once per view.
 */
class WeightedRandomLoadBalancer(private val instances: StateFlow<List<Resource>>) : LoadBalancer {
    @Volatile
    private var chooser = Chooser(emptyList())

    override fun choose(): Resource? {
        val current = instances.value
        var active = chooser
        if (active.source !== current) {
            active = Chooser(current)
            chooser = active
        }
        return active.choose()
    }

    private class Chooser(val source: List<Resource>) {
        private val candidates = source.filter { it.effectiveWeight > 0 }
        private val cumulative = LongArray(candidates.size).also { sums ->
            var sum = 0L
            candidates.forEachIndexed { index, resource ->
                sum += resource.effectiveWeight
                sums[index] = sum
            }
        }

        fun choose(): Resource? {
            if (candidates.isEmpty()) {
                return null
            }
            val point = ThreadLocalRandom.current().nextLong(cumulative.last())
            val found = cumulative.binarySearch(point + 1)
            return candidates[if (found >= 0) found else -found - 1]
        }
    }
}

/**
 * Takes turns among the instances with a positive weight, however large it is.
 */
class RoundRobinLoadBalancer(private val instances: StateFlow<List<Resource>>) : LoadBalancer {
    private val turn = AtomicInteger()

    @Volatile
    private var filtered: Pair<List<Resource>, List<Resource>> = emptyList<Resource>() to emptyList()

    override fun choose(): Resource? {
        val current = instances.value
        var active = filtered
        if (active.first !== current) {
            active = current to current.filter { it.effectiveWeight > 0 }
            filtered = active
        }
        val candidates = active.second
        if (candidates.isEmpty()) {
            return null
        }
        return candidates[Math.floorMod(turn.getAndIncrement(), candidates.size)]
    }
}
