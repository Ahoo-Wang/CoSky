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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.InstanceSpec
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ObjectMeta
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.validation.NamingRules
import java.util.concurrent.ConcurrentHashMap

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
                val state = watches.watch(WatchKey(name, Kind.KIND_CONFIG, name = config))
                state.view.map { objects -> objects[config] }
                    .stateIn(scope, SharingStarted.Eagerly, state.view.value[config])
            }
        }

        inner class Instances internal constructor() {
            /**
             * A view of a service's instances, in name order. Calls with the same service share it.
             */
            fun watch(service: String): StateFlow<List<Resource>> = instanceViews.computeIfAbsent(service) {
                val state = watches.watch(WatchKey(name, Kind.KIND_INSTANCE, service = service))
                state.view.map { objects -> sorted(objects) }
                    .stateIn(scope, SharingStarted.Eagerly, sorted(state.view.value))
            }

            /**
             * Registers an instance on the namespace's lease, which keeps it alive until [Registration.close].
             *
             * @param instanceName defaults to one derived from the service and the endpoint.
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
                leases.register(resource)
                return Registration(instanceName, leases)
            }

            private fun sorted(objects: Map<String, Resource>): List<Resource> =
                objects.values.sortedBy { it.meta.name }
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
    suspend fun close() {
        leases.deregister(instanceName)
    }
}
