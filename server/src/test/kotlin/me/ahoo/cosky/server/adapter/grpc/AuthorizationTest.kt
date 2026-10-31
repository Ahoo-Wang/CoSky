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

package me.ahoo.cosky.server.adapter.grpc

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.ServerInterceptors
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.v1.RevokeRequest
import me.ahoo.cosky.api.v1.Rule
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.WatchServiceGrpcKt
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.roleBindingSpec
import me.ahoo.cosky.api.v1.roleSpec
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.ParsedToken
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.key
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Authentication and authorization through the real services and the interceptor.
 */
class AuthorizationTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, 64, scope)
    private val access = AccessControl(caches)
    private val name = UUID.randomUUID().toString()
    private val interceptor = AuthInterceptor(access, AuditLogger())
    private val server = InProcessServerBuilder.forName(name)
        .addService(
            ServerInterceptors.intercept(
                ResourceGrpcService(ResourceService(store, caches, authorizer = access)),
                interceptor,
            ),
        )
        .addService(ServerInterceptors.intercept(LeaseGrpcService(store, access), interceptor))
        .addService(ServerInterceptors.intercept(WatchGrpcService(caches, CoSkyProperties(), access), interceptor))
        .build()
        .start()
    private val channels = mutableListOf<io.grpc.ManagedChannel>()

    @AfterEach
    fun stop() {
        channels.forEach { it.shutdownNow() }
        server.shutdownNow()
        scope.cancel()
    }

    private inner class Client(token: ParsedToken?) {
        private val channel: Channel = InProcessChannelBuilder.forName(name)
            .also { builder -> token?.let { builder.intercept(bearer(it.plaintext)) } }
            .build()
            .also { channels += it }
        val resources = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel)
        val leases = LeaseServiceGrpcKt.LeaseServiceCoroutineStub(channel)
        val watches = WatchServiceGrpcKt.WatchServiceCoroutineStub(channel)
    }

    private fun bearer(plaintext: String): ClientInterceptor = object : ClientInterceptor {
        override fun <ReqT : Any, RespT : Any> interceptCall(
            method: MethodDescriptor<ReqT, RespT>,
            options: CallOptions,
            next: Channel,
        ): ClientCall<ReqT, RespT> = object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
            next.newCall(method, options),
        ) {
            override fun start(listener: Listener<RespT>, headers: Metadata) {
                headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer $plaintext")
                super.start(listener, headers)
            }
        }
    }

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(VirtualThreads.dispatcher) {
        withTimeout(10.seconds) {
            store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
            store.createNamespace(NAMESPACE)
            block()
        }
    }

    private suspend fun system(vararg resources: Resource) {
        store.apply(NamingRules.SYSTEM_NAMESPACE, resources.map { Mutation.Put(it) }, "test")
        val revision = checkNotNull(store.position(NamingRules.SYSTEM_NAMESPACE)).revision
        caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null && it.revision >= revision }
    }

    /**
     * A ServiceAccount with a token, bound to [role] in [NAMESPACE].
     */
    private suspend fun account(accountName: String, role: String): Client {
        val token = Tokens.generate()
        system(
            resource {
                meta = objectMeta {
                    namespace = NamingRules.SYSTEM_NAMESPACE
                    this.name = accountName
                }
                serviceAccount = serviceAccountSpec {
                    tokens += TokenRef.newBuilder().setId(token.id).setSha256(Tokens.sha256Hex(token.secret)).build()
                }
            },
            binding(accountName, role),
        )
        return Client(token)
    }

    private fun binding(accountName: String, role: String): Resource = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            this.name = "$accountName-$role"
        }
        roleBinding = roleBindingSpec {
            this.role = role
            subjects += Subject.newBuilder().setType(Subject.Type.TYPE_SERVICE_ACCOUNT).setName(accountName).build()
            namespaces += NAMESPACE
        }
    }

    private fun customRole(roleName: String, vararg rules: Rule): Resource = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            this.name = roleName
        }
        role = roleSpec { this.rules += rules.toList() }
    }

    private fun rule(kind: Kind, verbs: List<Verb>, names: List<String> = emptyList()): Rule =
        Rule.newBuilder().addKinds(kind).addAllVerbs(verbs).addNamespaces("*").addAllNames(names).build()

    private fun config(configName: String, ns: String = NAMESPACE): Resource = resource {
        meta = objectMeta {
            namespace = ns
            this.name = configName
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            content = "a"
        }
    }

    private fun instance(instanceName: String, service: String, lease: Long = 0): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = instanceName
            this.lease = lease
        }
        instance = instanceSpec {
            this.service = service
            endpoint = Endpoint.newBuilder().setScheme("http").setHost("localhost").setPort(8080).build()
        }
    }

    private fun put(resource: Resource): PutRequest = PutRequest.newBuilder().setResource(resource).build()

    private suspend fun codeOf(block: suspend () -> Unit): Status.Code =
        (runCatching { block() }.exceptionOrNull() as StatusException).status.code

    private fun watch(kind: Kind): Flow<WatchRequest> = flow {
        emit(
            WatchRequest.newBuilder().apply {
                createBuilder.setWatchId(1).setNamespace(NAMESPACE).setKind(kind)
            }.build(),
        )
        CompletableDeferred<Unit>().await()
    }

    @Test
    fun `calling without a token, or a wrong one, is not authenticated`() = scenario {
        caches.of(NamingRules.SYSTEM_NAMESPACE).awaitView()
        val request = GetRequest.newBuilder().setKey(config("a").key).build()
        codeOf { Client(null).resources.get(request) }.assert().isEqualTo(Status.Code.UNAUTHENTICATED)
        codeOf { Client(Tokens.generate()).resources.get(request) }.assert().isEqualTo(Status.Code.UNAUTHENTICATED)
    }

    @Test
    fun `the right id with a wrong secret is not authenticated`() = scenario {
        val token = Tokens.generate()
        system(
            resource {
                meta = objectMeta {
                    namespace = NamingRules.SYSTEM_NAMESPACE
                    this.name = "order"
                }
                serviceAccount = serviceAccountSpec {
                    tokens += TokenRef.newBuilder().setId(token.id).setSha256(Tokens.sha256Hex(token.secret)).build()
                }
            },
        )
        val forged = Client(token.copy(secret = "wrong"))
        codeOf { forged.resources.get(GetRequest.newBuilder().setKey(config("a").key).build()) }
            .assert().isEqualTo(Status.Code.UNAUTHENTICATED)
    }

    @Test
    fun `a write records who made it`() = scenario {
        val admin = account("root", "admin")
        admin.resources.put(put(config("a")))
        store.readAll(NAMESPACE).first().meta.updatedBy.assert().isEqualTo("serviceaccount:root")
    }

    @Test
    fun `a viewer reads, and may not write`() = scenario {
        store.apply(NAMESPACE, listOf(Mutation.Put(config("a"))), "test")
        val viewer = account("eye", "viewer")
        val revision = checkNotNull(store.position(NAMESPACE)).revision
        val request = GetRequest.newBuilder().setKey(config("a").key).setMinRevision(revision).build()
        viewer.resources.get(request).meta.name.assert().isEqualTo("a")
        codeOf { viewer.resources.put(put(config("b"))) }.assert().isEqualTo(Status.Code.PERMISSION_DENIED)
    }

    @Test
    fun `nothing is granted outside the namespaces of the binding`() = scenario {
        store.createNamespace("elsewhere")
        val admin = account("root", "admin")
        codeOf { admin.resources.put(put(config("a", "elsewhere"))) }.assert().isEqualTo(Status.Code.PERMISSION_DENIED)
    }

    @Test
    fun `a list shows only the names a rule covers`() = scenario {
        store.apply(NAMESPACE, listOf(Mutation.Put(config("a")), Mutation.Put(config("b"))), "test")
        val revision = checkNotNull(store.position(NAMESPACE)).revision
        system(customRole("reader", rule(Kind.KIND_CONFIG, listOf(Verb.VERB_GET), listOf("a"))))
        val reader = account("narrow", "reader")
        val request = ListRequest.newBuilder()
            .setNamespace(NAMESPACE)
            .setKind(Kind.KIND_CONFIG)
            .setMinRevision(revision)
            .build()
        reader.resources.list(request).itemsList.map { it.meta.name }.assert().containsExactly("a")
        codeOf { reader.resources.get(GetRequest.newBuilder().setKey(config("b").key).build()) }
            .assert().isEqualTo(Status.Code.PERMISSION_DENIED)
    }

    @Test
    fun `an account cannot register as another service, or take over its instance`() = scenario {
        system(customRole("order", rule(Kind.KIND_INSTANCE, listOf(Verb.VERB_PUT, Verb.VERB_GET), listOf("order"))))
        val order = account("order-account", "order")
        order.resources.put(put(instance("order-1", "order")))
        codeOf { order.resources.put(put(instance("payment-1", "payment"))) }
            .assert().isEqualTo(Status.Code.PERMISSION_DENIED)

        store.apply(NAMESPACE, listOf(Mutation.Put(instance("payment-2", "payment"))), "test")
        val revision = checkNotNull(store.position(NAMESPACE)).revision
        caches.of(NAMESPACE).views.first { it != null && it.revision >= revision }
        codeOf { order.resources.put(put(instance("payment-2", "order"))) }
            .assert().isEqualTo(Status.Code.PERMISSION_DENIED)
    }

    @Test
    fun `a service account's tokens are not for writing`() = scenario {
        val admin = account("root", "admin")
        val forged = TokenRef.newBuilder().setId("forged").setSha256("0").build()
        system(
            resource {
                meta = objectMeta {
                    namespace = NamingRules.SYSTEM_NAMESPACE
                    this.name = "target"
                }
                serviceAccount = serviceAccountSpec {}
            },
        )
        val update = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                this.name = "target"
            }
            serviceAccount = serviceAccountSpec {
                description = "updated"
                tokens += forged
            }
        }
        // Bound to one namespace, this admin may not touch accounts at all.
        codeOf { admin.resources.put(put(update)) }.assert().isEqualTo(Status.Code.PERMISSION_DENIED)

        val global = Tokens.generate()
        system(
            resource {
                meta = objectMeta {
                    namespace = NamingRules.SYSTEM_NAMESPACE
                    this.name = "global"
                }
                serviceAccount = serviceAccountSpec {
                    tokens += TokenRef.newBuilder().setId(global.id).setSha256(Tokens.sha256Hex(global.secret)).build()
                }
            },
            resource {
                meta = objectMeta {
                    namespace = NamingRules.SYSTEM_NAMESPACE
                    this.name = "global-admin"
                }
                roleBinding = roleBindingSpec {
                    role = "admin"
                    subjects += Subject.newBuilder()
                        .setType(Subject.Type.TYPE_SERVICE_ACCOUNT)
                        .setName("global")
                        .build()
                    namespaces += "*"
                }
            },
        )
        Client(global).resources.put(put(update))
        val stored = store.readAll(NamingRules.SYSTEM_NAMESPACE).toList().single { it.meta.name == "target" }
        stored.serviceAccount.description.assert().isEqualTo("updated")
        stored.serviceAccount.tokensList.assert().isEmpty()
    }

    @Test
    fun `only the grantor may use a lease`() = scenario {
        val first = account("first", "client")
        val second = account("second", "client")
        val grant = GrantRequest.newBuilder().setNamespace(NAMESPACE).setTtlSeconds(60).build()
        val lease = first.leases.grant(grant).lease
        first.resources.put(put(instance("order-1", "order", lease)))

        codeOf { second.resources.put(put(instance("order-2", "order", lease))) }
            .assert().isEqualTo(Status.Code.PERMISSION_DENIED)
        val renewal = KeepAliveRequest.newBuilder().setNamespace(NAMESPACE).setLease(lease).build()
        codeOf { second.leases.keepAlive(flowOf(renewal)).toList() }.assert().isEqualTo(Status.Code.PERMISSION_DENIED)
        val revoke = RevokeRequest.newBuilder().setNamespace(NAMESPACE).setLease(lease).build()
        codeOf { second.leases.revoke(revoke) }.assert().isEqualTo(Status.Code.PERMISSION_DENIED)

        first.leases.keepAlive(flowOf(renewal)).toList().single().attachedCount.assert().isEqualTo(1)
        first.leases.revoke(revoke)
    }

    @Test
    fun `granting a lease takes the right to write instances`() = scenario {
        val viewer = account("eye", "viewer")
        codeOf { viewer.leases.grant(GrantRequest.newBuilder().setNamespace(NAMESPACE).setTtlSeconds(60).build()) }
            .assert().isEqualTo(Status.Code.PERMISSION_DENIED)
    }

    @Test
    fun `a watch without permission is canceled, and the stream goes on`() = scenario {
        system(customRole("nothing"))
        val client = account("order", "nothing")
        val response = client.watches.watch(watch(Kind.KIND_CONFIG)).first()
        response.canceled.detail.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
    }

    @Test
    fun `a watch delivers only the names a rule covers`() = scenario {
        system(customRole("reader", rule(Kind.KIND_CONFIG, listOf(Verb.VERB_WATCH), listOf("a"))))
        store.apply(NAMESPACE, listOf(Mutation.Put(config("a")), Mutation.Put(config("b"))), "test")
        val revision = checkNotNull(store.position(NAMESPACE)).revision
        caches.of(NAMESPACE).views.first { it != null && it.revision >= revision }
        val reader = account("narrow", "reader")
        val snapshot = reader.watches.watch(watch(Kind.KIND_CONFIG)).first { it.hasSnapshot() }.snapshot
        snapshot.itemsList.map { it.meta.name }.assert().containsExactly("a")
    }

    @Test
    fun `losing a permission cancels a running watch`() = scenario {
        val reader = account("eye", "viewer")
        val seen = mutableListOf<WatchResponse>()
        val responses = reader.watches.watch(watch(Kind.KIND_CONFIG))
        responses.take(2).collect { response ->
            seen += response
            if (response.hasSnapshot()) {
                store.apply(
                    NamingRules.SYSTEM_NAMESPACE,
                    listOf(Mutation.Delete(binding("eye", "viewer").key)),
                    "test",
                )
            }
        }
        seen.last().canceled.detail.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_PERMISSION_DENIED)
    }

    @Test
    fun `revoking a token ends its streams`() = scenario {
        val reader = account("eye", "viewer")
        val failure = runCatching {
            reader.watches.watch(watch(Kind.KIND_CONFIG)).collect { response ->
                if (response.hasSnapshot()) {
                    delay(50)
                    system(
                        resource {
                            meta = objectMeta {
                                namespace = NamingRules.SYSTEM_NAMESPACE
                                this.name = "eye"
                            }
                            serviceAccount = serviceAccountSpec {}
                        },
                    )
                }
            }
        }.exceptionOrNull() as StatusException
        failure.status.code.assert().isEqualTo(Status.Code.UNAUTHENTICATED)
    }

    private companion object {
        const val NAMESPACE = "prod"
    }
}
