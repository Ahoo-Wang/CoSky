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

package me.ahoo.cosky.client

import io.grpc.StatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.InternalCoSkyApi
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.InstanceSpec
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ObjectMeta
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.validation.NamingRules
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The CoSky client (design §6.5): everything it knows is a local view that keeps following the server, so reads never
 * wait for the network and survive a server outage.
 */
class CoSkyClient(private val options: CoSkyClientOptions) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val connection = Connection(options)
    private val watches = WatchMultiplexer(connection, options, SnapshotStore(options, scope), scope)
    private val namespaces = ConcurrentHashMap<String, Namespace>()

    val connectionState: StateFlow<ConnectionState>
        get() = watches.state

    fun namespace(name: String): Namespace = namespaces.computeIfAbsent(name) { Namespace(it) }

    /**
     * Revokes leases within the close timeout, and stops following.
     */
    override fun close() {
        runBlocking(VirtualThreads.dispatcher) {
            // Revoking is best effort: a lease that cannot be revoked does not keep the rest of closing from running.
            withTimeoutOrNull(options.closeTimeout) {
                namespaces.values.forEach { it.leases.close() }
            }
        }
        watches.close()
        scope.cancel()
        connection.close()
    }

    inner class Namespace internal constructor(private val name: String) {
        internal val leases = LeaseKeeper(name, connection, options, scope)
        private val configViews = ConcurrentHashMap<String, StateFlow<Resource?>>()
        private val instanceViews = ConcurrentHashMap<String, StateFlow<List<Resource>>>()

        val configs: Configs = Configs()
        val instances: Instances = Instances()

        /**
         * @param service whose instances to pick from.
         */
        fun loadBalancer(service: String): LoadBalancer = WeightedRandomLoadBalancer(instances.watch(service))

        inner class Configs internal constructor() {
            /**
             * A view of one Config, null while unknown or deleted. Calls with the same name share it.
             */
            fun watch(config: String): StateFlow<Resource?> = configViews.computeIfAbsent(config) {
                val state = stateOf(config)
                state.view.map { objects -> objects[config] }
                    .stateIn(scope, SharingStarted.Eagerly, state.view.value[config])
            }

            /**
             * Waits until the view is known, then reads it: null really means the Config does not exist, as far as the
             * server or a snapshot can tell.
             *
             * @throws CoSkyException if the server refuses, say for lack of permission, and no snapshot knows better.
             */
            suspend fun load(config: String): Resource? {
                val state = stateOf(config)
                state.awaitKnown()
                return state.view.value[config]
            }

            /**
             * Follows one Config until the hold is closed. The watch of [watch] and [load] lasts as long as the client;
             * this one ends with the last hold of the Config, unless one of them was called for it too. For the
             * starter, whose imports come and go with refreshes.
             */
            @InternalCoSkyApi
            fun hold(config: String): ConfigHold {
                val key = keyOf(config)
                return ConfigHold(config, watches.hold(key)) { watches.release(key) }
            }

            private fun stateOf(config: String) = watches.watch(keyOf(config))

            private fun keyOf(config: String) = WatchKey(name, Kind.KIND_CONFIG, name = config)
        }

        inner class Instances internal constructor() {
            /**
             * A view of a service's instances, in name order. Calls with the same service share it.
             */
            fun watch(service: String): StateFlow<List<Resource>> = instanceViews.computeIfAbsent(service) {
                val state = stateOf(service)
                state.view.map { objects -> sorted(objects) }
                    .stateIn(scope, SharingStarted.Eagerly, sorted(state.view.value))
            }

            /**
             * Waits until the view of a service is known, then reads it.
             *
             * @throws CoSkyException if the server refuses, say for lack of permission, and no snapshot knows better.
             */
            suspend fun load(service: String): List<Resource> {
                val state = stateOf(service)
                state.awaitKnown()
                return sorted(state.view.value)
            }

            /**
             * The services with instances in this namespace, as of now. This watches every Instance of the namespace,
             * so call it only where the whole list is really needed.
             */
            suspend fun services(): Set<String> {
                val state = watches.watch(WatchKey(name, Kind.KIND_INSTANCE))
                state.awaitKnown()
                return state.view.value.values.mapTo(sortedSetOf()) { it.instance.service }
            }

            private fun stateOf(service: String) =
                watches.watch(WatchKey(name, Kind.KIND_INSTANCE, service = service))

            /**
             * Registers an instance on the namespace's lease, which keeps it alive until [Registration.close].
             *
             * A server out of reach is no failure: the registration is remembered, and goes out once one answers.
             * Reads fail with a [CoSkyException]; this, a write, fails with the gRPC status the server answered, and
             * [CoSkyException.from] gives its reason when the server sent one.
             *
             * @param instanceName defaults to one derived from the service and the endpoint.
             * @throws StatusException when the server refuses the registration, such as `UNAUTHENTICATED` without a
             * valid token, `PERMISSION_DENIED` without a role that may register the service, or `INVALID_ARGUMENT`.
             * The client then holds what it held before.
             */
            suspend fun register(
                service: String,
                endpoint: Endpoint,
                instanceName: String = NamingRules.defaultInstanceName(service, endpoint.host, endpoint.port),
                metadata: Map<String, String> = emptyMap(),
                weight: Int? = null,
            ): Registration {
                val spec = InstanceSpec.newBuilder().setService(service).setEndpoint(endpoint).putAllMetadata(metadata)
                weight?.let { spec.setWeight(it) }
                val resource = Resource.newBuilder()
                    .setMeta(ObjectMeta.newBuilder().setNamespace(name).setName(instanceName))
                    .setInstance(spec)
                    .build()
                return leases.register(resource)
            }

            private fun sorted(objects: Map<String, Resource>): List<Resource> =
                objects.values.sortedBy { it.meta.name }
        }
    }
}

/**
 * One Config followed until [close], from [CoSkyClient.Namespace.Configs.hold].
 */
@InternalCoSkyApi
class ConfigHold internal constructor(
    private val config: String,
    private val state: WatchState,
    private val release: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean()

    /**
     * The Config now and as it changes, null while unknown or deleted.
     */
    val changes: Flow<Resource?> = state.view.map { it[config] }.distinctUntilChanged()

    /**
     * Waits until the view is known, then reads it, as [CoSkyClient.Namespace.Configs.load] does.
     *
     * @throws CoSkyException if the server refuses, say for lack of permission, and no snapshot knows better.
     */
    suspend fun load(): Resource? {
        state.awaitKnown()
        return state.view.value[config]
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            release()
        }
    }
}

/**
 * One registered instance. Closing it removes the instance, and the lease with the last one.
 */
class Registration internal constructor(
    val instanceName: String,
    private val leases: LeaseKeeper,
) {
    /**
     * Removes the instance. Once it is closed, or the name registered again, which the new handle closes, this does
     * nothing.
     *
     * @throws StatusException when the instance cannot be removed now, such as when no server answers or the server
     * refuses; [CoSkyException.from] gives the reason when the server sent one. The instance stays registered and
     * renewed, and closing can be tried again.
     */
    suspend fun close() {
        leases.deregister(this)
    }
}
