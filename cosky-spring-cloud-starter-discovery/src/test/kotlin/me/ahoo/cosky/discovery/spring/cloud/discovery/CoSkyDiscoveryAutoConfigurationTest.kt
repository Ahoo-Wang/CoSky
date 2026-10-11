package me.ahoo.cosky.discovery.spring.cloud.discovery

import me.ahoo.cosky.discovery.ServiceDiscovery
import me.ahoo.cosky.discovery.TopologyRecordingServiceDiscovery
import me.ahoo.cosky.discovery.loadbalancer.LoadBalancer
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisInstanceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceEventListenerContainer
import me.ahoo.cosky.discovery.redis.RedisServiceStatistic
import me.ahoo.cosky.discovery.redis.RedisServiceTopology
import me.ahoo.cosky.spring.cloud.CoSkyAutoConfiguration
import org.assertj.core.api.AssertionsForInterfaceTypes.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import reactor.core.publisher.Mono
import java.time.Duration

internal class CoSkyDiscoveryAutoConfigurationTest {
    private val contextRunner = ApplicationContextRunner()

    @Test
    fun contextLoads() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads", "spring.application.name=app")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
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

    @Test
    fun contextLoadsWhenCoSkyDisabled() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.enabled=false", "spring.application.name=app")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyDiscoveryAutoConfiguration::class.java,
            )
            .run {
                assertThat(it)
                    .doesNotHaveBean(ServiceDiscovery::class.java)
                    .doesNotHaveBean(LoadBalancer::class.java)
            }
    }

    @Test
    fun serviceDiscoveryRecordsTopologyByDefault() {
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=contextLoads", "spring.application.name=app")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyDiscoveryAutoConfiguration::class.java,
            )
            .run {
                assertThat(it.getBean(ServiceDiscovery::class.java))
                    .isInstanceOf(TopologyRecordingServiceDiscovery::class.java)
            }
    }

    @Test
    fun serviceDiscoveryWithoutTopology() {
        contextRunner
            .withPropertyValues(
                "spring.cloud.cosky.namespace=contextLoads",
                "spring.application.name=app",
                "${CoSkyDiscoveryProperties.PREFIX}.topology-enabled=false",
            )
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyDiscoveryAutoConfiguration::class.java,
            )
            .run {
                assertThat(it.getBean(ServiceDiscovery::class.java))
                    .isInstanceOf(ConsistencyRedisServiceDiscovery::class.java)
            }
    }

    /**
     * Discovery issued while the application is starting, before it registers itself, must still be attributed to
     * the application in the topology.
     */
    @Test
    fun topologyRecordsApplicationAsConsumerBeforeRegistration() {
        val namespace = "{topology-${System.nanoTime()}}"
        contextRunner
            .withPropertyValues("spring.cloud.cosky.namespace=$namespace", "spring.application.name=consumer-app")
            .withUserConfiguration(
                DataRedisAutoConfiguration::class.java,
                DataRedisReactiveAutoConfiguration::class.java,
                CoSkyAutoConfiguration::class.java,
                CoSkyDiscoveryAutoConfiguration::class.java,
            )
            .run {
                it.getBean(ServiceDiscovery::class.java).getInstances(namespace, "producer-app").block()
                val topology = it.getBean(RedisServiceTopology::class.java)
                val consumers = Mono.defer { topology.getTopology(namespace) }
                    .filter { deps -> deps.isNotEmpty() }
                    .repeatWhenEmpty { repeat -> repeat.delayElements(Duration.ofMillis(20)) }
                    .block(Duration.ofSeconds(5))!!
                assertThat(consumers).isEqualTo(mapOf("consumer-app" to setOf("producer-app")))
            }
    }
}
