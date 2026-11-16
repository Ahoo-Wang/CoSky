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

import kotlinx.coroutines.flow.Flow
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.ConfigHold
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry
import org.springframework.boot.SpringApplicationRunListener
import org.springframework.boot.bootstrap.BootstrapRegistry.InstanceSupplier
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
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
    private val imports = ConcurrentHashMap<Pair<String, String>, Import>()
    private var handedOver = false

    /** Held while imports, the run below and the listeners change. */
    private val lock = Any()

    /**
     * The run of the imports that [loaded] belongs to: Spring builds the loaders afresh for each, at startup and in
     * every refresh, so a loader stands for its run. Null once the run has been reconciled, or was the one at startup.
     */
    private var run: Any? = null
    private val loaded = HashSet<Pair<String, String>>()

    private val importListeners = mutableListOf<ImportListener>()
    private val dropListeners = mutableListOf<(namespace: String, config: String) -> Unit>()

    /**
     * What this application keeps of a Config its imports load: the hold its loads and its refreshes read through,
     * what the last load saw, and every property source it has made up.
     */
    private class Import(val hold: ConfigHold) {
        var seen: Resource? = null
        var sources: Set<String> = emptySet()
    }

    private fun importOf(key: Pair<String, String>): Import =
        imports.computeIfAbsent(key) { (namespace, config) -> Import(client.namespace(namespace).configs.hold(config)) }

    /**
     * What an import of the Config reads through, until [reconcile] drops the Config.
     */
    internal fun holdOf(namespace: String, config: String): ConfigHold = importOf(namespace to config).hold

    /**
     * Remembers a Config [run] loaded and what it saw, so that a change to it can trigger a refresh once the context
     * exists. A Config imported for the first time, say by a refresh that added an import, goes to the listeners too.
     */
    internal fun markImported(run: Any, namespace: String, config: String, seen: Resource?) = synchronized(lock) {
        if (this.run !== run) {
            // A run that failed is not reconciled, and does not count towards the next.
            this.run = run
            loaded.clear()
        }
        loaded += namespace to config
        val import = importOf(namespace to config)
        val first = import.seen == null
        import.seen = seen.orNone()
        if (first) {
            importListeners.forEach { it(namespace, config, seen.orNone(), import.hold.changes) }
        }
    }

    internal fun importedBy(namespace: String): Map<String, Resource> = imports.entries
        .mapNotNull { (key, import) -> import.seen?.takeIf { key.first == namespace }?.let { key.second to it } }
        .toMap()

    /**
     * Calls [listener] once for every Config imported so far, with what its load saw and its changes from then on, and
     * once for every Config imported for the first time later. It must not block.
     */
    internal fun eachImport(listener: ImportListener) = synchronized(lock) {
        imports.forEach { (key, import) ->
            import.seen?.let { listener(key.first, key.second, it, import.hold.changes) }
        }
        importListeners += listener
    }

    /**
     * Calls [listener] for every Config [reconcile] drops. It must not block.
     */
    internal fun onDropped(listener: (namespace: String, config: String) -> Unit) = synchronized(lock) {
        dropListeners += listener
    }

    /**
     * Once the application has started: what a refresh loads counts from here.
     */
    internal fun started() = synchronized(lock) {
        run = null
        loaded.clear()
    }

    /**
     * Once a refresh has applied what its run of the imports loaded, drops every Config the run did not load, such as
     * one that only another Config's `spring.config.import` named, until that import was removed. A refresh only adds
     * and replaces property sources, and a Config no longer imported has no load left to replace them by nothing
     * (design §7.7). Its watch ends with it.
     *
     * A refresh that loaded no Config at all is left alone: it may not have run the imports.
     *
     * @return the properties that left with them.
     */
    internal fun reconcile(environment: ConfigurableEnvironment): Set<String> = synchronized(lock) {
        if (run == null) {
            return@synchronized emptySet()
        }
        val dropped = imports.keys - loaded
        run = null
        loaded.clear()
        val left = mutableSetOf<String>()
        dropped.forEach { key ->
            val import = checkNotNull(imports.remove(key))
            import.sources.forEach { name ->
                val removed = environment.propertySources.remove(name)
                (removed as? EnumerablePropertySource<*>)?.let { left += it.propertyNames }
            }
            dropListeners.forEach { it(key.first, key.second) }
            import.hold.close()
        }
        left
    }

    /**
     * Remembers the property sources a Config makes up now, along with all it made up before. None is forgotten until
     * [reconcile]: a refresh that fails keeps the Environment as it was, old sources included, and a load cannot tell
     * whether its refresh will.
     *
     * @return every property source the Config has made up.
     */
    internal fun addSources(namespace: String, config: String, names: Set<String>): Set<String> = synchronized(lock) {
        val import = importOf(namespace to config)
        import.sources += names
        import.sources
    }

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
 * Told of a Config imported, with what its load saw and its changes from then on.
 */
internal typealias ImportListener =
    (namespace: String, config: String, seen: Resource, changes: Flow<Resource?>) -> Unit

/**
 * Something to compare a Config with, whether or not it exists.
 */
internal fun Resource?.orNone(): Resource = this ?: Resource.getDefaultInstance()
