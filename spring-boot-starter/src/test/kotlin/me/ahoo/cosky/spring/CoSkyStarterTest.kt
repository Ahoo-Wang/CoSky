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

package me.ahoo.cosky.spring

import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.ConnectionState
import me.ahoo.cosky.spring.config.CoSkyConfigRefresher
import me.ahoo.cosky.spring.discovery.CoSkyDiscoveryClient
import me.ahoo.cosky.spring.discovery.CoSkyReactiveDiscoveryClient
import me.ahoo.cosky.spring.registry.CoSkyAutoServiceRegistration
import me.ahoo.cosky.spring.registry.CoSkyRegistration
import me.ahoo.cosky.spring.registry.CoSkyServiceRegistry
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.health.contributor.Status
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.cloud.client.discovery.DiscoveryClient
import org.springframework.cloud.endpoint.event.RefreshEvent
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CoSkyStarterTest {
    private val server = TestCoSkyServer()
    private val contexts = mutableListOf<ConfigurableApplicationContext>()

    @EnableAutoConfiguration
    class Application {
        val refreshes = CopyOnWriteArrayList<RefreshEvent>()
        val refreshThreads = CopyOnWriteArrayList<Thread>()

        @Bean
        fun refreshListener(): ApplicationListener<RefreshEvent> = ApplicationListener {
            refreshes += it
            refreshThreads += Thread.currentThread()
        }
    }

    private fun application(vararg arguments: String, web: Boolean = false): ConfigurableApplicationContext {
        val common = listOf(
            "--spring.application.name=order",
            "--spring.grpc.server.enabled=false",
            "--spring.main.web-application-type=${if (web) "reactive" else "none"}",
            "--spring.cloud.compatibility-verifier.enabled=false",
            "--server.port=0",
            "--cosky.endpoints=${server.endpoint}",
            "--cosky.namespace=${TestCoSkyServer.NAMESPACE}",
            "--cosky.snapshot.enabled=false",
            "--cosky.config.load-timeout=3s",
            "--cosky.discovery.load-timeout=3s",
        )
        return SpringApplicationBuilder(Application::class.java)
            .run(*(common + arguments).toTypedArray())
            .also { contexts += it }
    }

    @AfterEach
    fun stop() {
        contexts.forEach { it.close() }
        server.close()
    }

    private suspend fun eventually(condition: () -> Boolean) {
        withTimeout(15.seconds) {
            while (!condition()) {
                delay(20.milliseconds)
            }
        }
    }

    // --- configuration ---

    @Test
    fun `a yaml config is imported`() {
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 10"))
        val context = application("--spring.config.import=cosky:order.yaml")
        context.environment.getProperty("order.limit").assert().isEqualTo("10")
    }

    @Test
    fun `the format comes from the config, not its name`() {
        server.put(TestCoSkyServer.config("props", "a.b=1", ConfigFormat.CONFIG_FORMAT_PROPERTIES))
        server.put(TestCoSkyServer.config("json", """{"c": {"d": 2}}""", ConfigFormat.CONFIG_FORMAT_JSON))
        server.put(TestCoSkyServer.config("text", "plain", ConfigFormat.CONFIG_FORMAT_TEXT))
        val context = application("--spring.config.import=cosky:props,cosky:json,cosky:text")
        context.environment.getProperty("a.b").assert().isEqualTo("1")
        context.environment.getProperty("c.d").assert().isEqualTo("2")
        context.environment.getProperty("text").assert().isEqualTo("plain")
    }

    @Test
    fun `a path-style config name works`() {
        server.put(TestCoSkyServer.config("shared/datasource.yaml", "url: jdbc"))
        val context = application("--spring.config.import=cosky:shared/datasource.yaml")
        context.environment.getProperty("url").assert().isEqualTo("jdbc")
    }

    @Test
    fun `a missing optional config is fine`() {
        val context = application("--spring.config.import=optional:cosky:missing.yaml")
        context.environment.getProperty("anything").assert().isNull()
    }

    @Test
    fun `a missing required config stops the application`() {
        val failure = runCatching { application("--spring.config.import=cosky:missing.yaml") }.exceptionOrNull()
        failure.assert().isInstanceOf(ConfigDataResourceNotFoundException::class.java)
    }

    @Test
    fun `changing an imported config publishes a refresh`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 10"))
        val context = application("--spring.config.import=cosky:order.yaml")
        val application = context.getBean(Application::class.java)
        delay(200.milliseconds)
        application.refreshes.assert().isEmpty()
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 20"))
        eventually { application.refreshes.isNotEmpty() }
        application.refreshThreads.first().isVirtual.assert().isFalse()
    }

    @Test
    fun `a refresh reads the new content`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 10"))
        val context = application("--spring.config.import=cosky:order.yaml")
        context.environment.getProperty("order.limit").assert().isEqualTo("10")
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 20"))
        eventually { context.environment.getProperty("order.limit") == "20" }
    }

    @Test
    fun `content that does not parse keeps the old values and does not stop later refreshes`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 10"))
        val context = application("--spring.config.import=cosky:order.yaml")
        server.put(TestCoSkyServer.config("order.yaml", "order: [unclosed"))
        delay(1.seconds)
        context.environment.getProperty("order.limit").assert().isEqualTo("10")
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 20"))
        eventually { context.environment.getProperty("order.limit") == "20" }
    }

    @Test
    fun `an optional config that appears later is picked up`() = runBlocking<Unit> {
        val context = application("--spring.config.import=optional:cosky:late.yaml")
        context.environment.getProperty("late.value").assert().isNull()
        server.put(TestCoSkyServer.config("late.yaml", "late:\n  value: here"))
        eventually { context.environment.getProperty("late.value") == "here" }
    }

    @Test
    fun `a change between loading and watching still counts`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("order.yaml", "order:\n  limit: 10"))
        val context = application("--spring.config.import=cosky:order.yaml")
        val properties = context.getBean(CoSkyProperties::class.java)
        // Stands in for that gap: this refresher starts from a revision older than the one the view is at.
        CoSkyClients.markImported(properties, "order.yaml", 0)
        val events = CopyOnWriteArrayList<Any>()
        val refresher = CoSkyConfigRefresher(context.getBean(CoSkyClient::class.java), properties) { events += it }
        refresher.afterSingletonsInstantiated()
        eventually { events.isNotEmpty() }
        refresher.destroy()
    }

    // --- discovery ---

    @Test
    fun `discovery reads instances and services`() {
        server.put(TestCoSkyServer.instance("order-1", "order"))
        server.put(TestCoSkyServer.instance("payment-1", "payment"))
        val discovery = application().getBean(CoSkyDiscoveryClient::class.java)
        val instance = discovery.getInstances("order").single()
        instance.serviceId.assert().isEqualTo("order")
        instance.instanceId.assert().isEqualTo("order-1")
        instance.host.assert().isEqualTo("10.0.0.1")
        instance.port.assert().isEqualTo(8080)
        instance.isSecure.assert().isFalse()
        instance.metadata.assert().containsEntry("zone", "az1")
        discovery.services.assert().containsExactly("order", "payment")
        discovery.description().assert().isEqualTo("CoSky")
        discovery.getInstances("unknown").assert().isEmpty()
    }

    @Test
    fun `reactive discovery reads the same`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.instance("order-1", "order"))
        val discovery = application().getBean(CoSkyReactiveDiscoveryClient::class.java)
        discovery.getInstances("order").collectList().awaitSingle().single().instanceId.assert().isEqualTo("order-1")
        discovery.services.collectList().awaitSingle().assert().containsExactly("order")
        discovery.description().assert().isEqualTo("CoSky")
    }

    @Test
    fun `the application appears in the topology under its name`() = runBlocking<Unit> {
        val context = application()
        context.getBean(CoSkyDiscoveryClient::class.java).getInstances("payment")
        server.topology.flush()
        val edge = server.store.topology(TestCoSkyServer.NAMESPACE).single()
        edge.consumer.assert().isEqualTo("order")
        edge.producer.assert().isEqualTo("payment")
    }

    @Test
    fun `spring cloud finds the discovery client`() {
        val context = application()
        context.getBeansOfType(DiscoveryClient::class.java).values
            .filterIsInstance<CoSkyDiscoveryClient>().size.assert().isEqualTo(1)
    }

    @Test
    fun `discovery can be turned off`() {
        val context = application("--cosky.discovery.enabled=false")
        context.getBeansOfType(CoSkyDiscoveryClient::class.java).assert().isEmpty()
    }

    @Test
    fun `spring cloud loadbalancer follows the view`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.instance("order-1", "order"))
        val context = application()
        val factory = context.getBean(LoadBalancerClientFactory::class.java)
        val supplier = checkNotNull(factory.getInstance("order", ServiceInstanceListSupplier::class.java))
        supplier.serviceId.assert().isEqualTo("order")
        val seen = CopyOnWriteArrayList<List<String>>()
        val subscription = supplier.get().subscribe { list -> seen.add(list.map { it.instanceId.orEmpty() }) }
        eventually { seen.any { it == listOf("order-1") } }
        server.put(TestCoSkyServer.instance("order-2", "order"))
        eventually { seen.any { it == listOf("order-1", "order-2") } }
        // Drained: it stays registered, and takes no traffic.
        server.put(TestCoSkyServer.instance("order-1", "order", weight = 0))
        eventually { seen.last() == listOf("order-2") }
        subscription.dispose()
    }

    // --- registration ---

    @Test
    fun `an application registers when its web server starts, and leaves when it stops`() = runBlocking<Unit> {
        val context = application("--cosky.registry.weight=7", "--cosky.registry.metadata.zone=az9", web = true)
        eventually { server.instances().isNotEmpty() }
        val instance = server.instances().single()
        instance.instance.service.assert().isEqualTo("order")
        instance.instance.weight.assert().isEqualTo(7)
        instance.instance.endpoint.port.assert().isGreaterThan(0)
        instance.instance.metadataMap.assert().containsEntry("zone", "az9")
        context.getBean(CoSkyAutoServiceRegistration::class.java).isRunning.assert().isTrue()

        context.close()
        contexts.remove(context)
        eventually { server.instances().isEmpty() }
    }

    @Test
    fun `registration can be turned off`() {
        val context = application("--cosky.registry.enabled=false", web = true)
        context.getBeansOfType(CoSkyAutoServiceRegistration::class.java).assert().isEmpty()
        server.instances().assert().isEmpty()
    }

    @Test
    fun `spring cloud's own switch turns automatic registration off`() {
        val context = application("--spring.cloud.service-registry.auto-registration.enabled=false", web = true)
        context.getBeansOfType(CoSkyAutoServiceRegistration::class.java).assert().isEmpty()
        context.getBeansOfType(CoSkyServiceRegistry::class.java).assert().hasSize(1)
        server.instances().assert().isEmpty()
    }

    @Test
    fun `a registration describes the instance, which has no status`() {
        val registry = application().getBean(CoSkyServiceRegistry::class.java)
        val registration = CoSkyRegistration("order", "10.0.0.1", secure = true, metadata = emptyMap())
        registration.setPort(8443)
        registration.isSecure.assert().isTrue()
        registration.uri.toString().assert().isEqualTo("https://10.0.0.1:8443")
        assertThrownBy<UnsupportedOperationException> { registry.setStatus(registration, "UP") }
        assertThrownBy<UnsupportedOperationException> { registry.getStatus<String>(registration) }
    }

    // --- the rest ---

    @Test
    fun `health stays up when degraded, and is down once closed`() = runBlocking<Unit> {
        server.put(TestCoSkyServer.config("order.yaml", "a: 1"))
        val context = application("--spring.config.import=cosky:order.yaml")
        val client = context.getBean(CoSkyClient::class.java)
        val indicator = context.getBean("coSkyHealthIndicator", HealthIndicator::class.java)
        eventually { client.connectionState.value == ConnectionState.SYNCED }
        checkNotNull(indicator.health()).status.assert().isEqualTo(Status.UP)

        server.close()
        eventually { client.connectionState.value == ConnectionState.DEGRADED }
        val degraded = checkNotNull(indicator.health())
        degraded.status.assert().isEqualTo(Status.UP)
        degraded.details["connection"].assert().isEqualTo("DEGRADED")

        client.close()
        checkNotNull(indicator.health()).status.assert().isEqualTo(Status.DOWN)
    }

    @Test
    fun `configuration loading and the context share one client`() {
        server.put(TestCoSkyServer.config("order.yaml", "a: 1"))
        val context = application("--spring.config.import=cosky:order.yaml")
        // Clients are shared by their settings: the context finds what loading did under the settings it bound itself.
        CoSkyClients.importedBy(context.getBean(CoSkyProperties::class.java)).keys.assert()
            .containsExactly("order.yaml")
    }

    @Test
    fun `each integration applies only when its framework is there`() {
        ApplicationContextRunner()
            .withClassLoader(
                FilteredClassLoader(
                    RefreshEvent::class.java,
                    ServiceInstanceListSupplier::class.java,
                    HealthIndicator::class.java,
                    WebServerApplicationContext::class.java,
                ),
            )
            .withConfiguration(AutoConfigurations.of(CoSkyAutoConfiguration::class.java))
            .withPropertyValues("cosky.endpoints=${server.endpoint}", "cosky.snapshot.enabled=false")
            .run { context ->
                context.startupFailure.assert().isNull()
                context.containsBean("coSkyDiscoveryClient").assert().isTrue()
                context.containsBean("coSkyServiceRegistry").assert().isTrue()
                context.containsBean("coSkyConfigRefresher").assert().isFalse()
                context.containsBean("coSkyHealthIndicator").assert().isFalse()
                context.containsBean("coSkyAutoServiceRegistration").assert().isFalse()
            }
    }

    @Test
    fun `the starter can be turned off`() {
        val context = application("--cosky.enabled=false")
        context.getBeansOfType(CoSkyClient::class.java).assert().isEmpty()
    }
}
