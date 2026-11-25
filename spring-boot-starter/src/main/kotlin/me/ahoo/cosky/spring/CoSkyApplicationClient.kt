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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The client of one Spring application (design §7.7), and what its imports loaded through it. Each application has its
 * own, so that it reports under its own name and closing it touches no other application in the same JVM.
 *
 * Configuration loads before the context exists and again on every refresh, so the first import creates it in the
 * bootstrap context, which Spring Cloud keeps in the application context to run every refresh in. When the bootstrap
 * context closes, the application context takes the client over and closes it after everything that uses it; an
 * application that fails to start before its context could close it has it closed by [CoSkyClientRunListener]. An
 * application that imports nothing at startup gets it from the auto-configuration, which puts it in that bootstrap
 * context too, so that a refresh adding the first import loads through it rather than creating another.
 */
class CoSkyApplicationClient internal constructor(
    properties: CoSkyProperties,
    applicationName: String?,
    handedOver: Boolean = false,
) : DisposableBean {
    /**
     * Named by `spring.application.name` in the server's topology view.
     */
    val client = CoSkyClient(properties.toOptions(applicationName))
    private val imports = ConcurrentHashMap<Pair<String, String>, Import>()

    /**
     * Whether the application context has it: from the start for the one the auto-configuration creates, otherwise
     * once the bootstrap context hands it over. Imports load before that only at startup, so after it in a refresh.
     */
    @Volatile
    internal var handedOver = handedOver
        private set
    private val closed = AtomicBoolean()

    /** Held while imports, the run below and the listeners change. */
    private val lock = Any()

    /**
     * The run of the imports that [loaded] belongs to: Spring builds the loaders afresh for each, at startup and in
     * every refresh, so a loader stands for its run. Null once the run has been reconciled, or was the one at startup.
     */
    private var run: Any? = null
    private val loaded = HashSet<Pair<String, String>>()

    /**
     * Whether the [run] went through, rather than failed half way: only then is [loaded] all that the imports load.
     */
    private var complete = false

    private val importListeners = mutableListOf<ImportListener>()
    private val dropListeners = mutableListOf<(namespace: String, config: String) -> Unit>()

    /**
     * What this application keeps of a Config its imports load: the hold its loads and its refreshes read through,
     * what the last load saw, what property sources were last made up from, and every property source it has made up.
     */
    private class Import(val hold: ConfigHold) {
        var seen: Resource? = null
        var loaded: Resource? = null
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
            complete = false
        }
        loaded += namespace to config
        val import = importOf(namespace to config)
        val first = import.seen == null
        import.seen = seen.orNone()
        if (first) {
            importListeners.forEach { it(namespace, config, seen.orNone(), import.hold.changes) }
        }
    }

    /**
     * What the Config held when its property sources were last made up from it, until [reconcile] drops the Config.
     */
    internal fun lastLoaded(namespace: String, config: String): Resource? = imports[namespace to config]?.loaded

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
        complete = false
    }

    /**
     * Before the imports load, in a refresh: whatever an earlier run that failed loaded does not count.
     */
    internal fun runBegins() = synchronized(lock) {
        run = null
        loaded.clear()
        complete = false
    }

    /**
     * Once a run went through: what it loaded is what the imports load now.
     */
    internal fun runEnds() = synchronized(lock) {
        complete = run != null
    }

    /**
     * Once a refresh has applied what its run of the imports loaded, drops every Config the run did not load, such as
     * one that only another Config's `spring.config.import` named, until that import was removed. A refresh only adds
     * and replaces property sources, and a Config no longer imported has no load left to replace them by nothing
     * (design §7.7). Its watch ends with it.
     *
     * A refresh that loaded no Config at all is left alone: it may not have run the imports. So is a run that failed,
     * which a refresh does not apply, should something else announce a change before the next run (#1263).
     *
     * @return the properties that left with them.
     */
    internal fun reconcile(environment: ConfigurableEnvironment): Set<String> = synchronized(lock) {
        if (run == null || !complete) {
            return@synchronized emptySet()
        }
        val dropped = imports.keys - loaded
        run = null
        loaded.clear()
        complete = false
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
     * Remembers the property sources a Config makes up now, from [loaded] if anything, along with all it made up
     * before. None is forgotten until [reconcile]: a refresh that fails keeps the Environment as it was, old sources
     * included, and a load cannot tell whether its refresh will.
     *
     * @return every property source the Config has made up.
     */
    internal fun addSources(namespace: String, config: String, names: Set<String>, loaded: Resource?): Set<String> =
        synchronized(lock) {
            val import = importOf(namespace to config)
            import.sources += names
            loaded?.let { import.loaded = it }
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
     * Closes the client, once: the context and [CoSkyClientRunListener] may both get to it.
     */
    override fun destroy() {
        if (closed.compareAndSet(false, true)) {
            client.close()
        }
    }

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

        /**
         * The client the auto-configuration creates for an application that imported nothing at startup. It goes into
         * the bootstrap context Spring Cloud keeps for refreshes, if any, where an import would have created it: a
         * refresh adding the first import would otherwise create a second client there, which nothing closes and
         * nothing refreshes for (design D41).
         */
        fun ofContext(
            bootstrap: ConfigurableBootstrapContext?,
            properties: CoSkyProperties,
            applicationName: String?,
        ): CoSkyApplicationClient = CoSkyApplicationClient(properties, applicationName, handedOver = true).also {
            bootstrap?.registerIfAbsent(CoSkyApplicationClient::class.java, InstanceSupplier.of(it))
        }
    }
}

/**
 * Closes the client of an application that fails to start before its context could. Spring Boot closes the bootstrap
 * context only once the application context is prepared, so a failed import, say, never gets that far; and a context
 * closes nothing unless refreshing made it active, which loading its sources or an `ApplicationPreparedEvent` listener
 * can still fail before. One that refreshing failed has closed it already, and an active one closes it after
 * everything that uses it.
 */
internal class CoSkyClientRunListener : SpringApplicationRunListener {
    private var bootstrap: ConfigurableBootstrapContext? = null

    override fun starting(bootstrapContext: ConfigurableBootstrapContext) {
        bootstrap = bootstrapContext
    }

    override fun failed(context: ConfigurableApplicationContext?, exception: Throwable) {
        if (context?.isActive != true) {
            bootstrap?.getOrElse(CoSkyApplicationClient::class.java, null)?.destroy()
        }
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
