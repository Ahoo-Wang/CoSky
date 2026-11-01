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

package me.ahoo.cosky.server.resource

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.HistoryRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.putRequest
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.resourceKey
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ResourceServiceTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, WINDOW, scope)
    private val resources = ResourceService(store, caches, minRevisionTimeout = SHORT_TIMEOUT)

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(VirtualThreads.dispatcher) {
        withTimeout(10.seconds) {
            store.createNamespace(NAMESPACE)
            block()
        }
    }

    private fun config(name: String, content: String = "a", labels: Map<String, String> = emptyMap()): Resource =
        resource {
            meta = objectMeta {
                namespace = NAMESPACE
                this.name = name
                this.labels.putAll(labels)
            }
            config = configSpec {
                format = ConfigFormat.CONFIG_FORMAT_TEXT
                this.content = content
            }
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

    private fun namespace(name: String): Resource = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            this.name = name
        }
        this.namespace = namespaceSpec {}
    }

    private fun instanceKey(name: String): ResourceKey = resourceKey {
        namespace = NAMESPACE
        kind = Kind.KIND_INSTANCE
        this.name = name
    }

    private fun configKey(name: String): ResourceKey = resourceKey {
        namespace = NAMESPACE
        kind = Kind.KIND_CONFIG
        this.name = name
    }

    private fun namespaceKey(name: String): ResourceKey = resourceKey {
        namespace = NamingRules.SYSTEM_NAMESPACE
        kind = Kind.KIND_NAMESPACE
        this.name = name
    }

    private suspend fun put(resource: Resource, expected: Long = 0) = resources.put(
        PutRequest.newBuilder().setResource(resource).setExpectedRevision(expected).build(),
        CALLER,
    )

    private fun get(name: String, minRevision: Long = 0): GetRequest =
        GetRequest.newBuilder().setKey(configKey(name)).setMinRevision(minRevision).build()

    private fun list(kind: Kind = Kind.KIND_CONFIG): ListRequest.Builder =
        ListRequest.newBuilder().setNamespace(NAMESPACE).setKind(kind)

    private suspend fun errorOf(block: suspend () -> Unit): ErrorReason =
        (runCatching { block() }.exceptionOrNull() as CoSkyException).reason

    // --- get ---

    @Test
    fun `a get waits for the revision it asks for`() = scenario {
        val revision = put(config("a")).revision
        resources.get(get("a", revision), CALLER).meta.updatedBy.assert().isEqualTo(CALLER.principal.id)
    }

    @Test
    fun `a revision that does not come in time fails`() = scenario {
        put(config("a"))
        errorOf {
            resources.get(
                get("a", 100),
                CALLER
            )
        }.assert().isEqualTo(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT)
    }

    @Test
    fun `a get of a missing resource fails`() = scenario {
        errorOf { resources.get(get("a"), CALLER) }.assert().isEqualTo(ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND)
    }

    @Test
    fun `reading a namespace that does not exist fails`() = scenario {
        val key = configKey("a").toBuilder().setNamespace("missing").build()
        errorOf { resources.get(GetRequest.newBuilder().setKey(key).build(), CALLER) }
            .assert().isEqualTo(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND)
    }

    @Test
    fun `an invalid request fails before anything else`() = scenario {
        errorOf { resources.get(GetRequest.getDefaultInstance(), CALLER) }
            .assert().isEqualTo(ErrorReason.ERROR_REASON_INVALID_ARGUMENT)
    }

    // --- list ---

    @Test
    fun `a list holds one kind, in name order, with its position`() = scenario {
        put(config("b"))
        put(config("a"))
        val revision = put(instance("order-1", "order")).revision
        val response = resources.list(list().setMinRevision(revision).build(), CALLER)
        response.itemsList.map { it.meta.name }.assert().containsExactly("a", "b")
        response.revision.assert().isEqualTo(revision)
        response.epoch.assert().isNotEmpty()
        response.nextPageToken.assert().isEmpty()
    }

    @Test
    fun `a list filters by service`() = scenario {
        put(instance("order-1", "order"))
        val revision = put(instance("payment-1", "payment")).revision
        val request = list(Kind.KIND_INSTANCE).setService("payment").setMinRevision(revision).build()
        resources.list(request, CALLER).itemsList.map { it.meta.name }.assert().containsExactly("payment-1")
    }

    @Test
    fun `a list filters by all the labels it names`() = scenario {
        put(config("a", labels = mapOf("team" to "x", "tier" to "1")))
        put(config("b", labels = mapOf("team" to "x", "tier" to "2")))
        val revision = put(config("c", labels = mapOf("team" to "y"))).revision
        val request = list().putLabelSelector("team", "x").putLabelSelector("tier", "2").setMinRevision(revision)
        resources.list(request.build(), CALLER).itemsList.map { it.meta.name }.assert().containsExactly("b")
    }

    @Test
    fun `pages cover everything once and report the first revision`() = scenario {
        var revision = 0L
        repeat(5) { revision = put(config("c$it")).revision }
        val first = resources.list(list().setPageSize(2).setMinRevision(revision).build(), CALLER)
        first.itemsList.map { it.meta.name }.assert().containsExactly("c0", "c1")
        // A write between pages does not change what the pages report.
        val later = put(config("c9")).revision
        val second = resources.list(
            list().setPageSize(2).setPageToken(first.nextPageToken).setMinRevision(later).build(),
            CALLER,
        )
        second.itemsList.map { it.meta.name }.assert().containsExactly("c2", "c3")
        second.revision.assert().isEqualTo(revision)
        val third = resources.list(list().setPageSize(2).setPageToken(second.nextPageToken).build(), CALLER)
        third.itemsList.map { it.meta.name }.assert().containsExactly("c4", "c9")
        third.nextPageToken.assert().isEmpty()
    }

    @Test
    fun `a page stays within the size cap`() {
        val capped = ResourceService(store, caches, CAPPED, SHORT_TIMEOUT)
        scenario {
            var revision = 0L
            repeat(3) { revision = put(config("c$it", "x".repeat(200))).revision }
            val first = capped.list(list().setMinRevision(revision).build(), CALLER)
            first.itemsList.map { it.meta.name }.assert().containsExactly("c0")
            val second = capped.list(list().setPageToken(first.nextPageToken).build(), CALLER)
            second.itemsList.map { it.meta.name }.assert().containsExactly("c1")
        }
    }

    @Test
    fun `an instance stays in its service, and can change anything else`() = scenario {
        val created = put(instance("a", "order")).revision
        caches.viewAt(NAMESPACE, created, SHORT_TIMEOUT)
        val moved = runCatching { put(instance("a", "payment")) }.exceptionOrNull() as CoSkyException
        moved.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_INVALID_ARGUMENT)
        moved.detail.badRequest.violationsList.single().field.assert().isEqualTo("resource.instance.service")

        val drained = put(instance("a", "order").toBuilder().apply { instanceBuilder.setWeight(0) }.build())
        drained.changed.assert().isTrue()
        val stored = caches.viewAt(NAMESPACE, drained.revision, SHORT_TIMEOUT).objects.getValue(instanceKey("a"))
        stored.instance.service.assert().isEqualTo("order")
        stored.meta.createRevision.assert().isEqualTo(created)
    }

    @Test
    fun `another service can use the name once the instance is gone`() = scenario {
        put(instance("a", "order"))
        val request = DeleteRequest.newBuilder().setKey(instanceKey("a")).build()
        caches.viewAt(NAMESPACE, resources.delete(request, CALLER).revision, SHORT_TIMEOUT)
        put(instance("a", "payment")).changed.assert().isTrue()
    }

    @Test
    fun `a replica that is behind judges an instance at the revision the write expects`() = scenario {
        val (replica, following) = replicaThatFollows()
        val order = applied(NAMESPACE, instance("a", "order"), Mutation.NO_CHECK)
        replica.caches.viewAt(NAMESPACE, order, SHORT_TIMEOUT)

        following.value = false
        val payment = applied(NAMESPACE, instance("a", "payment"), order)
        errorOf {
            replica.resources.put(
                putRequest {
                    resource = instance("a", "order")
                    expectedRevision = payment
                },
                CALLER,
            )
        }.assert().isEqualTo(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT)
    }

    private class Replica(val resources: ResourceService, val caches: WatchCache)

    private fun replicaThatFollows(): Pair<Replica, MutableStateFlow<Boolean>> {
        val following = MutableStateFlow(true)
        val behind = object : Store by store {
            override fun changes(namespace: String, from: Position): Flow<ChangeBatch> =
                store.changes(namespace, from).onEach { following.first { it } }
        }
        val caches = WatchCache(behind, WINDOW, scope)
        return Replica(ResourceService(behind, caches, minRevisionTimeout = SHORT_TIMEOUT), caches) to following
    }

    @Test
    fun `a replica that is behind does not bring back a revoked token`() = scenario {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        val (replica, following) = replicaThatFollows()
        val two = applied(NamingRules.SYSTEM_NAMESPACE, account("kept", "revoked"), Mutation.NO_CHECK)
        replica.caches.viewAt(NamingRules.SYSTEM_NAMESPACE, two, SHORT_TIMEOUT)

        following.value = false
        val one = applied(NamingRules.SYSTEM_NAMESPACE, account("kept"), two)
        errorOf {
            replica.resources.put(
                putRequest {
                    resource = account()
                    expectedRevision = one
                },
                CALLER
            )
        }.assert().isEqualTo(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT)

        store.readAll(NamingRules.SYSTEM_NAMESPACE).toList().single().serviceAccount.tokensList.map { it.id }
            .assert().containsExactly("kept")
    }

    private fun account(vararg tokens: String): Resource = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            name = "app"
        }
        serviceAccount = serviceAccountSpec {
            tokens.forEach { this.tokens += TokenRef.newBuilder().setId(it).setSha256("00").build() }
        }
    }

    private suspend fun applied(namespace: String, resource: Resource, expected: Long): Long {
        val mutation = Mutation.Put(resource, expected)
        return (store.apply(namespace, listOf(mutation), "test") as ApplyResult.Applied).revision
    }

    @Test
    fun `a page token from another epoch asks to start over`() = scenario {
        val revision = put(config("a")).revision
        val token = PageTokens.encode(ListPageToken("another", revision, "a"))
        errorOf { resources.list(list().setPageToken(token).setMinRevision(revision).build(), CALLER) }
            .assert().isEqualTo(ErrorReason.ERROR_REASON_INVALID_ARGUMENT)
    }

    @Test
    fun `garbage is not a page token`() = scenario {
        errorOf { resources.list(list().setPageToken("not a token").build(), CALLER) }
            .assert().isEqualTo(ErrorReason.ERROR_REASON_INVALID_ARGUMENT)
    }

    // --- put and delete ---

    @Test
    fun `a put can only create, or compare and set`() = scenario {
        val created = put(config("a"), expected = -1)
        created.changed.assert().isTrue()
        errorOf { put(config("a", "b"), expected = -1) }.assert().isEqualTo(ErrorReason.ERROR_REASON_REVISION_CONFLICT)
        errorOf { put(config("a", "b"), expected = 99) }.assert().isEqualTo(ErrorReason.ERROR_REASON_REVISION_CONFLICT)
        put(config("a", "b"), expected = created.revision).changed.assert().isTrue()
    }

    @Test
    fun `the hash of a Config is the server's to state, in every revision`() = scenario {
        val forged = config("a", "first").toBuilder().apply { configBuilder.sha256 = "forged" }.build()
        val first = put(forged)
        val hash = Tokens.sha256Hex("first")
        resources.get(get("a", first.revision), CALLER).config.sha256.assert().isEqualTo(hash)

        put(config("a", "second"))
        val rolledBack = resources.rollback(configKey("a"), first.revision, 0, CALLER)

        resources.get(get("a", rolledBack.revision), CALLER).config.sha256.assert().isEqualTo(hash)
    }

    @Test
    fun `writing the same content changes nothing`() = scenario {
        val first = put(config("a"))
        val second = put(config("a"))
        second.changed.assert().isFalse()
        second.revision.assert().isEqualTo(first.revision)
    }

    @Test
    fun `a write to a namespace that does not exist fails`() = scenario {
        val elsewhere = config("a").toBuilder().apply { metaBuilder.namespace = "missing" }.build()
        errorOf { put(elsewhere) }.assert().isEqualTo(ErrorReason.ERROR_REASON_NAMESPACE_NOT_FOUND)
    }

    @Test
    fun `an instance needs a lease that exists`() = scenario {
        val leased = instance("order-1", "order").toBuilder().apply { metaBuilder.lease = 99 }.build()
        errorOf { put(leased) }.assert().isEqualTo(ErrorReason.ERROR_REASON_LEASE_NOT_FOUND)
    }

    @Test
    fun `a delete reports whether anything was deleted`() = scenario {
        put(config("a"))
        val request = DeleteRequest.newBuilder().setKey(configKey("a")).build()
        resources.delete(request, CALLER).deleted.assert().isTrue()
        resources.delete(request, CALLER).deleted.assert().isFalse()
    }

    // --- namespaces ---

    @Test
    fun `a namespace is created with its object, and starts empty`() = scenario {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        put(namespace("orders"))
        store.namespaceState("orders").assert().isEqualTo(NamespaceState.ACTIVE)
        store.position("orders")?.revision.assert().isEqualTo(0)
    }

    @Test
    fun `a namespace with objects cannot be deleted`() = scenario {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        put(namespace(NAMESPACE))
        put(config("a"))
        val request = DeleteRequest.newBuilder().setKey(namespaceKey(NAMESPACE)).build()
        errorOf { resources.delete(request, CALLER) }.assert().isEqualTo(ErrorReason.ERROR_REASON_NAMESPACE_NOT_EMPTY)
        store.namespaceState(NAMESPACE).assert().isEqualTo(NamespaceState.ACTIVE)
    }

    @Test
    fun `deleting an empty namespace removes it and its object`() = scenario {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        put(namespace("orders"))
        val request = DeleteRequest.newBuilder().setKey(namespaceKey("orders")).build()
        resources.delete(request, CALLER).deleted.assert().isTrue()
        store.position("orders").assert().isNull()
        delay(SETTLE)
        errorOf { resources.get(GetRequest.newBuilder().setKey(namespaceKey("orders")).build(), CALLER) }
            .assert().isEqualTo(ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND)
    }

    @Test
    fun `a stale revision does not delete a namespace`() = scenario {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        val revision = put(namespace("orders")).revision
        val request = DeleteRequest.newBuilder().setKey(
            namespaceKey("orders")
        ).setExpectedRevision(revision + 5).build()
        delay(SETTLE)
        errorOf { resources.delete(request, CALLER) }.assert().isEqualTo(ErrorReason.ERROR_REASON_REVISION_CONFLICT)
        store.namespaceState("orders").assert().isEqualTo(NamespaceState.ACTIVE)
    }

    // --- history ---

    private fun history(limit: Int = 0, token: String = ""): HistoryRequest =
        HistoryRequest.newBuilder().setKey(configKey("a")).setLimit(limit).setPageToken(token).build()

    @Test
    fun `history is newest first, up to the limit`() = scenario {
        repeat(3) { put(config("a", "v$it")) }
        resources.history(
            history(),
            CALLER
        ).revisionsList.map { it.config.content }.assert().containsExactly("v2", "v1", "v0")
        resources.history(
            history(limit = 2),
            CALLER
        ).revisionsList.map { it.config.content }.assert().containsExactly("v2", "v1")
    }

    @Test
    fun `history pages stay within the size cap`() {
        val capped = ResourceService(store, caches, CAPPED, SHORT_TIMEOUT)
        scenario {
            repeat(3) { put(config("a", "$it".repeat(200))) }
            val first = capped.history(history(), CALLER)
            first.revisionsCount.assert().isEqualTo(1)
            val second = capped.history(history(token = first.nextPageToken), CALLER)
            second.revisionsCount.assert().isEqualTo(1)
            val third = capped.history(history(token = second.nextPageToken), CALLER)
            third.revisionsCount.assert().isEqualTo(1)
            third.nextPageToken.assert().isEmpty()
            listOf(first, second, third).map { it.revisionsList.single().config.content.first() }
                .assert().containsExactly('2', '1', '0')
        }
    }

    @Test
    fun `garbage is not a history page token`() = scenario {
        errorOf {
            resources.history(history(token = "soon"), CALLER)
        }.assert().isEqualTo(ErrorReason.ERROR_REASON_INVALID_ARGUMENT)
    }

    private companion object {
        const val NAMESPACE = "resources"
        const val WINDOW = 64
        val CALLER = Caller(Principal(Subject.Type.TYPE_USER, "someone"))
        val CAPPED = Limits(maxConfigContentBytes = 250, maxMessageBytes = 300)
        val SHORT_TIMEOUT = 300.milliseconds
        val SETTLE = 100.milliseconds
    }
}
