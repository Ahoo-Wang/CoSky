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
package me.ahoo.cosky.discovery

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.discovery.TestServiceInstance.randomFixedInstance
import me.ahoo.cosky.discovery.TestServiceInstance.randomInstance
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * @author ahoo wang
 */
class RenewInstanceServiceTest : AbstractReactiveRedisTest() {
    private lateinit var testInstance: ServiceInstance
    private lateinit var testFixedInstance: ServiceInstance
    private lateinit var redisServiceRegistry: RedisServiceRegistry
    private val renewProperties = RenewProperties()

    override fun afterInitializedRedisClient() {
        testInstance = randomInstance()
        testFixedInstance = randomFixedInstance()
        val registryProperties = RegistryProperties(Duration.ofSeconds(15))
        redisServiceRegistry = RedisServiceRegistry(registryProperties, redisTemplate)
    }

    @Test
    fun start() {
        val namespace = MockIdGenerator.INSTANCE.generateAsString()
        val semaphore = Semaphore(0)
        val renewService =
            RenewInstanceService(
                renewProperties = renewProperties,
                serviceRegistry = redisServiceRegistry,
                hookOnRenew = {
                    assertThat(it, equalTo(testInstance))
                    semaphore.release()
                },
            )
        renewService.start()
        redisServiceRegistry.register(namespace, testInstance)
            .test()
            .expectNext(true)
            .verifyComplete()
        redisServiceRegistry.register(namespace, testFixedInstance)
            .test()
            .expectNext(true)
            .verifyComplete()
        assertThat(
            semaphore.tryAcquire(
                renewProperties.initialDelay.plusSeconds(2).seconds,
                TimeUnit.SECONDS,
            ),
            equalTo(true),
        )

        renewService.stop()
    }
}
