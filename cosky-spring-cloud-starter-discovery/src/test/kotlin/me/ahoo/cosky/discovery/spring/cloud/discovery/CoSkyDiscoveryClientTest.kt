package me.ahoo.cosky.discovery.spring.cloud.discovery

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.core.NamespacedContext
import me.ahoo.cosky.discovery.Instance
import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.ServiceInstance.Companion.asServiceInstance
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test
import reactor.kotlin.test.test
import java.util.concurrent.ThreadLocalRandom

internal class CoSkyDiscoveryClientTest : AbstractReactiveRedisTest() {
    lateinit var serviceRegistry: RedisServiceRegistry
    lateinit var serviceDiscovery: RedisServiceDiscovery
    lateinit var coSkyDiscoveryClient: CoSkyDiscoveryClient

    override fun afterInitializedRedisClient() {
        serviceRegistry = RedisServiceRegistry(RegistryProperties(), redisTemplate)
        serviceDiscovery = RedisServiceDiscovery(redisTemplate)
        coSkyDiscoveryClient = CoSkyDiscoveryClient(serviceDiscovery, CoSkyDiscoveryProperties())
    }

    @Test
    fun description() {
        assertThat(coSkyDiscoveryClient.description(), `is`("CoSky Discovery Client"))
    }

    @Test
    fun getInstances() {
        val instance = Instance.asInstance(
            MockIdGenerator.INSTANCE.generateAsString(),
            "http",
            "127.0.0.1",
            ThreadLocalRandom.current().nextInt(65535),
        ).asServiceInstance(metadata = mapOf("from" to "test"))
        serviceRegistry.register(NamespacedContext.namespace, instance)
            .test()
            .expectNext(true)
            .verifyComplete()
        val instances = coSkyDiscoveryClient.getInstances(instance.serviceId)
            .map { it.instanceId }
        assertThat(instances, hasItem(instance.instanceId))
    }

    @Test
    fun getServices() {
        val services = coSkyDiscoveryClient.services
        assertThat(services, notNullValue())
    }

    @Test
    fun getOrder() {
        assertThat(coSkyDiscoveryClient.order, `is`(0))
    }
}
