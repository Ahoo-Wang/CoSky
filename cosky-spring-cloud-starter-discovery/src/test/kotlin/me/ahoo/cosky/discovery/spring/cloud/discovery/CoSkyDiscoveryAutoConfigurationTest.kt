package me.ahoo.cosky.discovery.spring.cloud.discovery

import me.ahoo.cosky.discovery.loadbalancer.LoadBalancer
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisInstanceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceStatistic
import me.ahoo.cosky.discovery.redis.RedisServiceTopology
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

internal class CoSkyDiscoveryAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads", "spring.application.name=app")
            .withUserConfiguration(
                RedisAutoConfiguration::class.java,
                RedisReactiveAutoConfiguration::class.java,
                CoSkyDiscoveryAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .hasSingleBean(CoSkyDiscoveryProperties::class.java)
                    .hasSingleBean(RedisServiceDiscovery::class.java)
                    .hasSingleBean(RedisServiceTopology::class.java)
                    .hasSingleBean(RedisServiceEventListenerContainer::class.java)
                    .hasSingleBean(RedisInstanceEventListenerContainer::class.java)
                    .hasSingleBean(ConsistencyRedisServiceDiscovery::class.java)
                    .hasSingleBean(RedisServiceStatistic::class.java)
                    .hasSingleBean(LoadBalancer::class.java)
            }
    }
}
