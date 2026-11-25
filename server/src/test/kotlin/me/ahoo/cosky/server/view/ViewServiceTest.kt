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
import me.ahoo.cosky.api.validation.Limits
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

    private fun services(pageSize: Int = 0, token: String = ""): ServicesRequest = ServicesRequest.newBuilder()
        .setNamespace(NAMESPACE)
        .setPageSize(pageSize)
        .setPageToken(token)
        .build()

    @Test
    fun `services page by count, each page with the first page's revision`() = runBlocking<Unit> {
        fill()
        val first = views.services(services(pageSize = 1), Caller.ANONYMOUS)
        first.servicesList.map { it.service }.assert().containsExactly("order")
        store.apply(NAMESPACE, listOf(Mutation.Put(instance("a1", "audit"))), "test")
        caches.of(NAMESPACE).views.first { it != null && it.revision >= 2 }

        val second = views.services(services(pageSize = 1, token = first.nextPageToken), Caller.ANONYMOUS)
        second.servicesList.map { it.service }.assert().containsExactly("payment")
        second.revision.assert().isEqualTo(first.revision)
        second.nextPageToken.assert().isEmpty()
    }

    @Test
    fun `without a page size every service comes at once, unless the size cap splits them`() = runBlocking<Unit> {
        fill()
        views.services(services(), Caller.ANONYMOUS).nextPageToken.assert().isEmpty()
        val capped = ViewService(store, caches, limits = Limits(maxConfigContentBytes = 4, maxMessageBytes = 12))
        val pages = generateSequence(capped.services(services(), Caller.ANONYMOUS)) { page ->
            page.nextPageToken.takeIf { it.isNotEmpty() }
                ?.let { runBlocking { capped.services(services(token = it), Caller.ANONYMOUS) } }
        }.toList()
        pages.size.assert().isEqualTo(2)
        pages.flatMap { page -> page.servicesList.map { it.service } }.assert().containsExactly("order", "payment")
    }

    @Test
    fun `a services token from another timeline, or of another kind, is refused`() = runBlocking<Unit> {
        fill()
        val token = views.services(services(pageSize = 1), Caller.ANONYMOUS).nextPageToken
        val epoch = checkNotNull(store.position(NAMESPACE)).epoch
        store.rotateEpoch(NAMESPACE, epoch)
        caches.of(NAMESPACE).views.first { it != null && it.epoch != epoch }
        val topologyToken = run {
            store.recordTopology(NAMESPACE, listOf(edge("a", "b"), edge("c", "d")), 1.minutes)
            val request = TopologyRequest.newBuilder().setNamespace(NAMESPACE).setPageSize(1).build()
            views.topology(request, Caller.ANONYMOUS).nextPageToken
        }
        listOf(token, topologyToken, "nonsense").forEach { stale ->
            val failure = runCatching { views.services(services(token = stale), Caller.ANONYMOUS) }
                .exceptionOrNull() as CoSkyException
            failure.detail.badRequest.violationsList.single().field.assert().isEqualTo("page_token")
        }
    }

    @Test
    fun `the topology pages after the last edge of the page before`() = runBlocking<Unit> {
        store.createNamespace(NAMESPACE)
        // A consumer is whatever a client calls itself.
        val edges = listOf(edge("cart\nb", "order"), edge("cart", "payment"), edge("order", "payment"))
        store.recordTopology(NAMESPACE, edges, 1.minutes)
        val seen = mutableListOf<TopologyEdge>()
        var token = ""
        do {
            val request = TopologyRequest.newBuilder().setNamespace(NAMESPACE).setPageSize(1).setPageToken(token)
            val page = views.topology(request.build(), Caller.ANONYMOUS)
            page.edgesCount.assert().isEqualTo(1)
            seen += page.edgesList
            token = page.nextPageToken
        } while (token.isNotEmpty())
        seen.assert().containsExactly(edge("cart", "payment"), edge("cart\nb", "order"), edge("order", "payment"))
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
