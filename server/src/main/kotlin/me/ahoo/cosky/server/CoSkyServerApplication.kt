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

package me.ahoo.cosky.server

import io.grpc.ServerInterceptor
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.resource.ClientResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.adapter.grpc.AuthInterceptor
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.AuthBootstrap
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.lease.LeaseReaper
import me.ahoo.cosky.server.resource.NamespaceReconciler
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreOptions
import me.ahoo.cosky.store.redis.RedisStore
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.grpc.server.GlobalServerInterceptor
import org.springframework.grpc.server.ServerBuilderCustomizer
import kotlin.time.toKotlinDuration

@SpringBootApplication
@EnableConfigurationProperties(CoSkyProperties::class)
class CoSkyServerApplication : DisposableBean {
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)

    /**
     * gRPC runs its calls on virtual threads too, before they reach a coroutine (design D28).
     */
    @Bean
    fun virtualThreadExecutor(): ServerBuilderCustomizer<*> = ServerBuilderCustomizer { builder ->
        builder.executor(VirtualThreads.executor)
    }

    @Bean
    fun store(properties: CoSkyProperties, resources: ObjectProvider<ClientResources>): Store {
        val redis = properties.store.redis
        val uri = RedisURI.create(redis.uri)
        val clientResources = resources.getIfAvailable { ClientResources.create() }
        val options = StoreOptions(logSize = properties.watch.window)
        val pollInterval = redis.tailPollInterval.toKotlinDuration()
        val store = if (redis.cluster) {
            RedisStore.cluster(RedisClusterClient.create(clientResources, uri), options, pollInterval)
        } else {
            RedisStore.standalone(RedisClient.create(clientResources, uri), options, pollInterval)
        }
        runBlocking(VirtualThreads.dispatcher) { store.checkPrerequisites() }
        return store
    }

    @Bean
    fun watchCache(store: Store, properties: CoSkyProperties): WatchCache =
        WatchCache(store, properties.watch.window, scope)

    @Bean
    fun accessControl(caches: WatchCache): AccessControl = AccessControl(caches)

    @Bean
    @Primary
    fun authorizer(access: AccessControl, properties: CoSkyProperties): Authorizer =
        if (properties.auth.enabled) access else Authorizer.ALLOW_ALL

    @Bean
    fun auditLogger(): AuditLogger = AuditLogger()

    @Bean
    @GlobalServerInterceptor
    @ConditionalOnProperty(prefix = "cosky.auth", name = ["enabled"], matchIfMissing = true)
    fun authInterceptor(access: AccessControl, audit: AuditLogger): ServerInterceptor = AuthInterceptor(access, audit)

    @Bean
    fun resourceService(
        store: Store,
        caches: WatchCache,
        properties: CoSkyProperties,
        authorizer: Authorizer,
        audit: AuditLogger,
    ): ResourceService = ResourceService(
        store,
        caches,
        minRevisionTimeout = properties.read.minRevisionTimeout.toKotlinDuration(),
        authorizer = authorizer,
        audit = audit,
    )

    @Bean
    fun leaseReaper(store: Store, caches: WatchCache, properties: CoSkyProperties): LeaseReaper {
        val lease = properties.lease
        return LeaseReaper(
            store = store,
            namespaces = {
                caches.of(NamingRules.SYSTEM_NAMESPACE).awaitView().objects.keys
                    .filter { it.kind == Kind.KIND_NAMESPACE }
                    .map { it.name }
            },
            options = LeaseReaper.Options(
                interval = lease.reaperInterval,
                grace = lease.reaperGrace,
                maxPerTick = lease.reaperMaxPerTick,
                lockTtl = lease.lockTtl,
                protectionThreshold = lease.protectionThreshold,
                protectionMinLeases = lease.protectionMinLeases,
                protectionMaxDuration = lease.protectionMaxDuration,
            ),
            maintenance = NamespaceReconciler(store, caches)::reconcile,
        )
    }

    @Bean
    fun bootstrap(
        store: Store,
        caches: WatchCache,
        reaper: LeaseReaper,
        properties: CoSkyProperties,
    ): ApplicationRunner = ApplicationRunner {
        runBlocking(VirtualThreads.dispatcher) {
            createInitialNamespaces(store)
            if (properties.auth.enabled) {
                AuthBootstrap(store).run(properties.auth.bootstrapToken)
            }
        }
        caches.of(NamingRules.SYSTEM_NAMESPACE)
        scope.launch { reaper.run() }
    }

    /**
     * `default` exists from the first start on (design §7.2), but it stays deleted if someone deletes it.
     */
    private suspend fun createInitialNamespaces(store: Store) {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        if (store.readAll(NamingRules.SYSTEM_NAMESPACE).firstOrNull { it.hasNamespace() } != null) {
            return
        }
        store.createNamespace(DEFAULT_NAMESPACE)
        val namespace = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                name = DEFAULT_NAMESPACE
            }
            this.namespace = namespaceSpec {}
        }
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(namespace)), LeaseReaper.ACTOR)
    }

    override fun destroy() {
        scope.cancel()
    }

    private companion object {
        const val DEFAULT_NAMESPACE = "default"
    }
}

fun main(args: Array<String>) {
    runApplication<CoSkyServerApplication>(*args)
}
