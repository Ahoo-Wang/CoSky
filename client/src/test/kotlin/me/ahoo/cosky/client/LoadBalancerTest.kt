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

import kotlinx.coroutines.flow.MutableStateFlow
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class LoadBalancerTest {
    private fun instance(name: String, weight: Int? = null): Resource = resource {
        meta = objectMeta { this.name = name }
        instance = instanceSpec {
            weight?.let { this.weight = it }
        }
    }

    private fun counts(balancer: LoadBalancer): Map<String?, Int> =
        List(SAMPLES) { balancer.choose()?.meta?.name }.groupingBy { it }.eachCount()

    @Test
    fun `no instances means no choice`() {
        val instances = MutableStateFlow(emptyList<Resource>())
        WeightedRandomLoadBalancer(instances).choose().assert().isNull()
        RoundRobinLoadBalancer(instances).choose().assert().isNull()
    }

    @Test
    fun `only drained instances means no choice`() {
        val instances = MutableStateFlow(listOf(instance("a", weight = 0)))
        WeightedRandomLoadBalancer(instances).choose().assert().isNull()
        RoundRobinLoadBalancer(instances).choose().assert().isNull()
    }

    @Test
    fun `weighted random follows the weights and skips drained instances`() {
        val instances = MutableStateFlow(listOf(instance("light"), instance("drained", 0), instance("heavy", 3)))
        val counts = counts(WeightedRandomLoadBalancer(instances))
        counts.keys.assert().containsExactlyInAnyOrder("light", "heavy")
        val share = counts.getValue("heavy").toDouble() / SAMPLES
        share.assert().isBetween(0.7, 0.8)
    }

    @Test
    fun `weighted random notices a new view`() {
        val instances = MutableStateFlow(listOf(instance("old")))
        val balancer = WeightedRandomLoadBalancer(instances)
        balancer.choose()?.meta?.name.assert().isEqualTo("old")
        instances.value = listOf(instance("new"))
        balancer.choose()?.meta?.name.assert().isEqualTo("new")
    }

    @Test
    fun `round robin takes turns and skips drained instances`() {
        val instances = MutableStateFlow(listOf(instance("a", 5), instance("drained", 0), instance("b")))
        val balancer = RoundRobinLoadBalancer(instances)
        List(4) { balancer.choose()?.meta?.name }.assert().containsExactly("a", "b", "a", "b")
    }

    @Test
    fun `round robin notices a new view`() {
        val instances = MutableStateFlow(listOf(instance("old")))
        val balancer = RoundRobinLoadBalancer(instances)
        balancer.choose()?.meta?.name.assert().isEqualTo("old")
        instances.value = listOf(instance("new"))
        balancer.choose()?.meta?.name.assert().isEqualTo("new")
    }

    @Test
    fun `backoff doubles up to its limit, with jitter, and resets`() {
        val backoff = Backoff(100.milliseconds, 400.milliseconds)
        repeat(5) { backoff.next() }
        backoff.next().assert().isBetween(200.milliseconds, 400.milliseconds)
        backoff.reset()
        backoff.next().assert().isBetween(50.milliseconds, 100.milliseconds)
    }

    @Test
    fun `at least one endpoint is required`() {
        runCatching { CoSkyClientOptions(endpoints = emptyList()) }.exceptionOrNull()
            .assert().isInstanceOf(IllegalArgumentException::class.java)
    }

    private companion object {
        const val SAMPLES = 10_000
    }
}
