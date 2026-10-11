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
package me.ahoo.cosky.config.spring.cloud

import me.ahoo.cosky.config.ConfigEventListenerContainer
import me.ahoo.cosky.config.redis.RedisConfigService
import me.ahoo.cosky.config.redis.RedisConsistencyConfigService
import me.ahoo.cosky.config.spring.cloud.refresh.CoSkyConfigRefresher
import me.ahoo.cosky.spring.cloud.CoSkyAutoConfiguration
import org.assertj.core.api.AssertionsForInterfaceTypes
import org.junit.jupiter.api.Test
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

internal class CoSkyConfigBootstrapConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads", "spring.application.name=app")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyConfigBootstrapConfiguration::class.java,
            )
            .run {
                AssertionsForInterfaceTypes.assertThat(it)
                    .hasSingleBean(CoSkyConfigProperties::class.java)
                    .hasSingleBean(ConfigEventListenerContainer::class.java)
                    .hasSingleBean(RedisConfigService::class.java)
                    .hasSingleBean(RedisConsistencyConfigService::class.java)
                    .hasSingleBean(CoSkyConfigRefresher::class.java)
                    .hasSingleBean(CoSkyPropertySourceLocator::class.java)
            }
    }

    @Test
    fun contextLoadsWhenCoSkyDisabled() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.enabled=false", "spring.application.name=app")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyConfigBootstrapConfiguration::class.java,
            )
            .run {
                AssertionsForInterfaceTypes.assertThat(it)
                    .hasNotFailed()
                    .doesNotHaveBean(CoSkyPropertySourceLocator::class.java)
            }
    }
}
