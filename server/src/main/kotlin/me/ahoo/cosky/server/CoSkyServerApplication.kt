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
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.adapter.grpc.AuthInterceptor
import me.ahoo.cosky.server.adapter.grpc.ClientNameInterceptor
import me.ahoo.cosky.server.adapter.rest.RestRoutes
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.OidcAuthenticator
import me.ahoo.cosky.server.auth.TokenService
import me.ahoo.cosky.server.lease.LeaseReaper
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.server.observe.MeteredStore
import me.ahoo.cosky.server.observe.ReadinessIndicator
import me.ahoo.cosky.server.resource.NamespaceReconciler
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.resource.StoreBootstrap
import me.ahoo.cosky.server.view.TopologyRecorder
import me.ahoo.cosky.server.view.ViewService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreOptions
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.cosky.store.redis.RedisStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.grpc.server.GlobalServerInterceptor
import org.springframework.grpc.server.ServerBuilderCustomizer
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
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
    fun metrics(registry: MeterRegistry): CoSkyMetrics = CoSkyMetrics(registry)

    @Bean
    fun store(
        properties: CoSkyProperties,
        resources: ObjectProvider<ClientResources>,
        metrics: CoSkyMetrics,
    ): Store {
        val options = StoreOptions(logSize = properties.watch.window)
        return when (properties.store.type) {
            CoSkyProperties.MEMORY -> {
                log.warn("The store is in memory: nothing survives this process. Use it for development only.")
                MemoryStore(options)
            }

            CoSkyProperties.REDIS -> redisStore(properties, resources, options)

            else -> error("cosky.store.type must be ${CoSkyProperties.REDIS} or ${CoSkyProperties.MEMORY}.")
        }.let { MeteredStore(it, metrics) }
    }

    private fun redisStore(
        properties: CoSkyProperties,
        resources: ObjectProvider<ClientResources>,
        options: StoreOptions,
    ): Store {
        val redis = properties.store.redis
        val uri = RedisURI.create(redis.uri)
        val clientResources = resources.getIfAvailable { ClientResources.create() }
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
    fun watchCache(store: Store, properties: CoSkyProperties, metrics: CoSkyMetrics): WatchCache = WatchCache(
        store,
        properties.watch.window,
        scope,
        properties.watch.antiEntropyInterval.toKotlinDuration(),
        metrics,
    )

    @Bean("coSkyReadiness")
    fun readiness(caches: WatchCache): HealthIndicator = ReadinessIndicator(caches)

    @Bean
    fun topologyRecorder(store: Store, properties: CoSkyProperties): TopologyRecorder =
        TopologyRecorder(store, properties.view.topologyFlushInterval.toKotlinDuration())

    @Bean
    fun viewService(store: Store, caches: WatchCache, authorizer: Authorizer): ViewService =
        ViewService(store, caches, authorizer)

    @Bean
    @GlobalServerInterceptor
    fun clientNameInterceptor(): ServerInterceptor = ClientNameInterceptor()

    @Bean
    fun accessControl(caches: WatchCache, properties: CoSkyProperties): AccessControl {
        val oidc = properties.auth.oidc
        val authenticator = oidc.issuer?.let { issuer ->
            val audience = checkNotNull(oidc.audience ?: oidc.clientId) {
                "With an OIDC issuer set, cosky.auth.oidc.audience or client-id is required."
            }
            OidcAuthenticator.create(issuer, audience, oidc.groupsClaim)
        }
        return AccessControl(caches, oidc = authenticator)
    }

    @Bean
    fun tokenService(store: Store, caches: WatchCache, authorizer: Authorizer, audit: AuditLogger): TokenService =
        TokenService(store, caches, authorizer, audit = audit)

    @Bean
    fun restRoutes(
        resources: ResourceService,
        views: ViewService,
        tokens: TokenService,
        caches: WatchCache,
        authorizer: Authorizer,
        access: AccessControl,
        audit: AuditLogger,
        properties: CoSkyProperties,
    ): RouterFunction<ServerResponse> = RestRoutes(
        resources,
        views,
        tokens,
        caches,
        authorizer,
        access.takeIf { properties.auth.enabled },
        audit,
        properties,
    ).routes()

    @Bean
    @Primary
    fun authorizer(access: AccessControl, properties: CoSkyProperties): Authorizer =
        if (properties.auth.enabled) access else Authorizer.ALLOW_ALL

    @Bean
    fun auditLogger(): AuditLogger = AuditLogger()

    @Bean
    @GlobalServerInterceptor
    @ConditionalOnProperty(prefix = "cosky.auth", name = ["enabled"], matchIfMissing = true)
    fun authInterceptor(access: AccessControl, audit: AuditLogger, metrics: CoSkyMetrics): ServerInterceptor =
        AuthInterceptor(access, audit, metrics)

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
    fun storeBootstrap(store: Store, properties: CoSkyProperties): StoreBootstrap =
        StoreBootstrap(store, properties.auth)

    @Bean
    fun leaseReaper(
        store: Store,
        caches: WatchCache,
        bootstrap: StoreBootstrap,
        properties: CoSkyProperties,
        metrics: CoSkyMetrics,
    ): LeaseReaper {
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
            maintenance = NamespaceReconciler(store, caches, bootstrap)::reconcile,
            metrics = metrics,
        )
    }

    @Bean
    fun bootstrap(
        bootstrap: StoreBootstrap,
        caches: WatchCache,
        reaper: LeaseReaper,
        topology: TopologyRecorder,
    ): ApplicationRunner = ApplicationRunner {
        runBlocking(VirtualThreads.dispatcher) { bootstrap.run() }
        caches.of(NamingRules.SYSTEM_NAMESPACE)
        scope.launch { reaper.run() }
        scope.launch { topology.run() }
    }

    override fun destroy() {
        scope.cancel()
    }

    private companion object {
        val log = LoggerFactory.getLogger(CoSkyServerApplication::class.java)
    }
}

/**
 * `--dev` is the shortest way to try the server: no Redis, and no sign-in.
 */
fun main(args: Array<String>) {
    val development = listOf("--cosky.store.type=${CoSkyProperties.MEMORY}", "--cosky.auth.enabled=false")
    val arguments = args.flatMap { if (it == DEV_FLAG) development else listOf(it) }
    runApplication<CoSkyServerApplication>(*arguments.toTypedArray())
}

private const val DEV_FLAG = "--dev"
