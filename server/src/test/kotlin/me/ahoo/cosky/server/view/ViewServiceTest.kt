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

package me.ahoo.cosky.server.view

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ServicesRequest
import me.ahoo.cosky.api.v1.StatsRequest
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TopologyEdge
import me.ahoo.cosky.api.v1.TopologyRequest
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class ViewServiceTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, 64, scope)
    private val views = ViewService(store, caches)

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun instance(name: String, service: String): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
        }
        instance = instanceSpec {
            this.service = service
            endpoint = Endpoint.newBuilder().setScheme("http").setHost("localhost").setPort(8080).build()
        }
    }

    private fun config(name: String): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            content = "a"
        }
    }

    private suspend fun fill() {
        store.createNamespace(NAMESPACE)
        val objects = listOf(instance("o1", "order"), instance("o2", "order"), instance("p1", "payment"), config("c"))
        store.apply(NAMESPACE, objects.map { Mutation.Put(it) }, "test")
        caches.of(NAMESPACE).views.first { it != null && it.revision >= 1 }
    }

    @Test
    fun `services are counted and ordered by name`() = runBlocking<Unit> {
        fill()
        val response = views.services(ServicesRequest.newBuilder().setNamespace(NAMESPACE).build(), Caller.ANONYMOUS)
        response.servicesList.map { it.service to it.instanceCount }
            .assert().containsExactly("order" to 2, "payment" to 1)
        response.revision.assert().isEqualTo(1)
    }

    @Test
    fun `stats count services, instances and configs`() = runBlocking<Unit> {
        fill()
        val stats = views.stats(StatsRequest.newBuilder().setNamespace(NAMESPACE).build(), Caller.ANONYMOUS)
        stats.serviceCount.assert().isEqualTo(2)
        stats.instanceCount.assert().isEqualTo(3)
        stats.configCount.assert().isEqualTo(1)
        stats.revision.assert().isEqualTo(1)
    }

    @Test
    fun `the topology comes from the store, ordered`() = runBlocking<Unit> {
        store.createNamespace(NAMESPACE)
        val edges = listOf(edge("order", "payment"), edge("cart", "payment"), edge("cart", "order"))
        store.recordTopology(NAMESPACE, edges, 1.minutes)
        views.topology(TopologyRequest.newBuilder().setNamespace(NAMESPACE).build(), Caller.ANONYMOUS)
            .edgesList.assert().containsExactly(
                edge("cart", "order"),
                edge("cart", "payment"),
                edge("order", "payment")
            )
    }

    @Test
    fun `a view of a namespace that does not exist fails`() = runBlocking<Unit> {
        val failure = runCatching {
            views.stats(StatsRequest.newBuilder().setNamespace("missing").build(), Caller.ANONYMOUS)
        }.exceptionOrNull() as CoSkyException
        failure.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND)
    }

    @Test
    fun `a rule limited to some services is not enough for a view`() = runBlocking<Unit> {
        fill()
        val limited = ViewService(store, caches, grantingOnInstances(Permission.Names(setOf("order"))))
        val caller = Caller(Principal(Subject.Type.TYPE_USER, "ann"))
        val failure = runCatching {
            limited.stats(StatsRequest.newBuilder().setNamespace(NAMESPACE).build(), caller)
        }.exceptionOrNull() as CoSkyException
        failure.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
    }

    private fun edge(consumer: String, producer: String): TopologyEdge =
        TopologyEdge.newBuilder().setConsumer(consumer).setProducer(producer).build()

    private fun grantingOnInstances(permission: Permission): Authorizer = object : Authorizer by Authorizer.ALLOW_ALL {
        override fun permission(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission =
            if (kind == Kind.KIND_INSTANCE && verb == Verb.VERB_GET) permission else Permission.None
    }

    private companion object {
        const val NAMESPACE = "views"
    }
}
