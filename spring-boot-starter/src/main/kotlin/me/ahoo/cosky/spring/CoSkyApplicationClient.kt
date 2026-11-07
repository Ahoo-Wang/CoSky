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

import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.client.CoSkyClient
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry
import org.springframework.boot.SpringApplicationRunListener
import org.springframework.boot.bootstrap.BootstrapRegistry.InstanceSupplier
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext
import org.springframework.context.ConfigurableApplicationContext
import java.util.concurrent.ConcurrentHashMap

/**
 * The client of one Spring application (design §7.7), and what its imports loaded through it. Each application has its
 * own, so that it reports under its own name and closing it touches no other application in the same JVM.
 *
 * Configuration loads before the context exists and again on every refresh, so the first import creates it in the
 * bootstrap context, which Spring Cloud keeps in the application context to run every refresh in. When the bootstrap
 * context closes, the application context takes the client over and closes it after everything that uses it; an
 * application that fails to start before then has it closed by [CoSkyClientRunListener]. An application that imports
 * nothing gets it from the auto-configuration.
 */
class CoSkyApplicationClient internal constructor(properties: CoSkyProperties, applicationName: String?) :
    DisposableBean {
    /**
     * Named by `spring.application.name` in the server's topology view.
     */
    val client = CoSkyClient(properties.toOptions(applicationName))
    private val imported = ConcurrentHashMap<String, MutableMap<String, Resource>>()
    private val sources = ConcurrentHashMap<Pair<String, String>, Set<String>>()
    private var handedOver = false

    /**
     * Remembers a Config this application loaded and what it saw, so that a change to it can trigger a refresh once the
     * context exists.
     */
    internal fun markImported(namespace: String, config: String, seen: Resource?) {
        imported.computeIfAbsent(namespace) { ConcurrentHashMap() }[config] = seen.orNone()
    }

    internal fun importedBy(namespace: String): Map<String, Resource> = imported[namespace].orEmpty()

    /**
     * Remembers the property sources a Config makes up now, along with all it made up before. None is forgotten: a
     * refresh that fails keeps the Environment as it was, old sources included, and a load cannot tell whether its
     * refresh will.
     *
     * @return every property source the Config has made up.
     */
    internal fun addSources(namespace: String, config: String, names: Set<String>): Set<String> =
        sources.merge(namespace to config, names) { before, now -> before + now }.orEmpty()

    /**
     * A singleton registered as it is gets no destruction callback, so this asks for one as well.
     */
    private fun handOver(context: ConfigurableApplicationContext) {
        val beans = context.beanFactory
        beans.registerSingleton(BEAN_NAME, this)
        (beans as DefaultSingletonBeanRegistry).registerDisposableBean(BEAN_NAME, this)
        handedOver = true
    }

    /**
     * Once handed over, the context closes it, also when the application fails to start.
     */
    internal fun closeUnlessHandedOver() {
        if (!handedOver) {
            client.close()
        }
    }

    override fun destroy() = client.close()

    internal companion object {
        private const val BEAN_NAME = "coSkyApplicationClient"

        /**
         * The application's client in this bootstrap context, which its first import creates.
         */
        fun of(
            bootstrap: ConfigurableBootstrapContext,
            properties: CoSkyProperties,
            applicationName: String?,
        ): CoSkyApplicationClient = bootstrap.getOrElse(CoSkyApplicationClient::class.java, null)
            ?: CoSkyApplicationClient(properties, applicationName).also { created ->
                bootstrap.register(CoSkyApplicationClient::class.java, InstanceSupplier.of(created))
                bootstrap.addCloseListener { created.handOver(it.applicationContext) }
            }
    }
}

/**
 * Closes the client an import created for an application that fails to start before its context takes the client over:
 * Spring Boot closes the bootstrap context only once the application context is prepared, so a failed import, say,
 * never gets that far.
 */
internal class CoSkyClientRunListener : SpringApplicationRunListener {
    private var bootstrap: ConfigurableBootstrapContext? = null

    override fun starting(bootstrapContext: ConfigurableBootstrapContext) {
        bootstrap = bootstrapContext
    }

    override fun failed(context: ConfigurableApplicationContext?, exception: Throwable) {
        bootstrap?.getOrElse(CoSkyApplicationClient::class.java, null)?.closeUnlessHandedOver()
    }
}

/**
 * Something to compare a Config with, whether or not it exists.
 */
internal fun Resource?.orNone(): Resource = this ?: Resource.getDefaultInstance()
