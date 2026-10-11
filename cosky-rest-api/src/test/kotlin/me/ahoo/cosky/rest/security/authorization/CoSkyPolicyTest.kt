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
package me.ahoo.cosky.rest.security.authorization

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigData
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.config.ConfigWatcher
import me.ahoo.cosky.core.cache.Snapshot
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.kotlin.test.test
import java.util.concurrent.atomic.AtomicInteger

class CoSkyPolicyTest {
    private val bundledPolicyJson = javaClass.classLoader.getResource(
        InitialPolicyLoader.policyResourceName
    )!!.readText()

    private fun config(data: String, version: Int): Config = ConfigData(
        CoSkyPolicy.policyId,
        data,
        "hash-$version",
        0,
        version
    )

    @Test
    fun bundledPolicyWhenNoneIsStored() {
        val policy = CoSkyPolicy(
            mockk { every { getConfig(any(), any()) } returns Mono.empty() },
            mockk { every { watchConfig(any(), any()) } returns Flux.never() },
        )
        policy.afterPropertiesSet()

        policy.getPolicy().test().expectNext(InitialPolicyLoader.policy).verifyComplete()
    }

    @Test
    fun failedReadIsNotCached() {
        val reads = AtomicInteger()
        val configService = mockk<ConfigService> {
            every { getConfig(any(), any()) } returns Mono.defer {
                if (reads.incrementAndGet() == 1) Mono.error(IllegalStateException("redis down")) else Mono.empty()
            }
        }
        val policy = CoSkyPolicy(configService, mockk { every { watchConfig(any(), any()) } returns Flux.never() })
        policy.afterPropertiesSet()

        policy.getPolicy().test().expectError(IllegalStateException::class.java).verify()
        policy.getPolicy().test().expectNext(InitialPolicyLoader.policy).verifyComplete()
    }

    @Test
    fun followsStoredPolicyAndKeepsLastGoodOne() {
        val snapshots = Sinks.many().replay().latest<Snapshot<Config>>()
        val watcher = mockk<ConfigWatcher> { every { watchConfig(any(), any()) } returns snapshots.asFlux() }
        val policy = CoSkyPolicy(mockk(), watcher)
        policy.afterPropertiesSet()

        val edited = bundledPolicyJson.replace("\"name\": \"namespace\"", "\"name\": \"namespace-edited\"")
        snapshots.tryEmitNext(Snapshot(1, config(edited, 1)))
        val first = policy.getPolicy().block()!!
        first.statements.map { it.name }.assert().contains("namespace-edited")

        snapshots.tryEmitNext(Snapshot(2, config("{ not json", 2)))
        policy.getPolicy().block().assert().isSameAs(first)
    }
}
