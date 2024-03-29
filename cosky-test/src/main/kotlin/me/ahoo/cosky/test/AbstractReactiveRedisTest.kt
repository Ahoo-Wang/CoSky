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
package me.ahoo.cosky.test

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import reactor.core.publisher.Hooks

/**
 * ReactiveRedisTest .
 *
 * @author ahoo wang
 */
abstract class AbstractReactiveRedisTest : InitializingBean, DisposableBean {

    protected lateinit var connectionFactory: LettuceConnectionFactory
    protected lateinit var redisTemplate: ReactiveStringRedisTemplate

    @BeforeEach
    override fun afterPropertiesSet() {
        if (enableOperatorDebug) {
            Hooks.onOperatorDebug()
        }

        val lettuceClientConfiguration = LettuceClientConfiguration
            .builder()
            .build()
        val redisConfig = RedisStandaloneConfiguration()
        connectionFactory = LettuceConnectionFactory(redisConfig, lettuceClientConfiguration)
        connectionFactory.afterPropertiesSet()
        connectionFactory.shareNativeConnection = enableShare
        customizeConnectionFactory(connectionFactory)
        redisTemplate = ReactiveStringRedisTemplate(connectionFactory)
        afterInitializedRedisClient()
    }

    protected open fun afterInitializedRedisClient() = Unit
    protected open fun customizeConnectionFactory(connectionFactory: LettuceConnectionFactory) = Unit
    protected open val enableOperatorDebug: Boolean
        get() {
            return false
        }

    protected open val enableShare: Boolean
        get() {
            return false
        }

    @AfterEach
    override fun destroy() {
        connectionFactory.destroy()
        afterDestroyRedisClient()
    }

    protected open fun afterDestroyRedisClient() = Unit
}
