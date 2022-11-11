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
package me.ahoo.cosky.config

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.config.redis.RedisConfigEventListenerContainer
import me.ahoo.cosky.config.redis.RedisConfigService
import me.ahoo.cosky.config.redis.RedisConsistencyConfigService
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
open class ConsistencyRedisConfigServiceBenchmark : AbstractReactiveRedisTest() {
    private lateinit var configService: ConfigService

    @Setup
    override fun afterPropertiesSet() {
        super.afterPropertiesSet()
        val redisConfigService = RedisConfigService(redisTemplate)
        redisConfigService.setConfig(TestData.NAMESPACE, CONFIG_ID, TestData.DATA).block()
        val configEventListenerContainer =
            RedisConfigEventListenerContainer(ReactiveRedisMessageListenerContainer(connectionFactory))
        configService = RedisConsistencyConfigService(redisConfigService, configEventListenerContainer)
    }

    override val enableShare: Boolean
        get() = true

    @TearDown
    override fun destroy() {
        super.destroy()
    }

    @Benchmark
    fun getConfig() = configService.getConfig(TestData.NAMESPACE, CONFIG_ID).block()!!

    companion object {
        private val CONFIG_ID = MockIdGenerator.INSTANCE.generateAsString()
    }
}
