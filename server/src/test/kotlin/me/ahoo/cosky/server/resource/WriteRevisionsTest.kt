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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.resourceKey
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The revision a write answers (design §4.5): the object's, so that it serves as the next expected revision, also for
 * a write that changed nothing; and a rollback that has no revision to go back to.
 */
class WriteRevisionsTest {
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

    private fun config(name: String, content: String = "a"): Resource = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            this.name = name
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

    private fun configKey(name: String): ResourceKey = resourceKey {
        namespace = NAMESPACE
        kind = Kind.KIND_CONFIG
        this.name = name
    }

    private suspend fun put(resource: Resource, expected: Long = 0) = resources.put(
        PutRequest.newBuilder().setResource(resource).setExpectedRevision(expected).build(),
        CALLER,
    )

    private fun get(name: String, minRevision: Long = 0): GetRequest =
        GetRequest.newBuilder().setKey(configKey(name)).setMinRevision(minRevision).build()

    private fun serviceAccount(name: String): Resource = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            this.name = name
        }
        serviceAccount = serviceAccountSpec { description = "app" }
    }

    @Test
    fun `writing the same content answers the object's revision, though the namespace has moved on`() = scenario {
        val first = put(config("a"))
        put(config("b"))
        val same = put(config("a"))
        same.changed.assert().isFalse()
        same.revision.assert().isEqualTo(first.revision)
        put(config("a", "next"), expected = same.revision).changed.assert().isTrue()
    }

    @Test
    fun `every kind answers its own revision for a write that changes nothing`() = scenario {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        val instance = put(instance("order-1", "order"))
        val created = put(namespace("orders"))
        val account = put(serviceAccount("app"))
        put(config("b"))
        put(namespace("others"))
        put(instance("order-1", "order")).revision.assert().isEqualTo(instance.revision)
        put(namespace("orders")).revision.assert().isEqualTo(created.revision)
        put(serviceAccount("app")).revision.assert().isEqualTo(account.revision)
        put(serviceAccount("app"), expected = account.revision).revision.assert().isEqualTo(account.revision)
    }

    @Test
    fun `a write that changed nothing is made again when its object changes before the answer`() = scenario {
        put(config("a"))
        var raced = false
        val racing = object : Store by store {
            override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult {
                val result = store.apply(namespace, mutations, actor)
                if (!raced && result is ApplyResult.Applied && result.changed == 0) {
                    raced = true
                    val theirs = store.apply(namespace, listOf(Mutation.Put(config("a", "theirs"))), "other")
                    caches.viewAt(namespace, (theirs as ApplyResult.Applied).revision, SHORT_TIMEOUT)
                }
                return result
            }
        }
        val service = ResourceService(racing, caches, minRevisionTimeout = SHORT_TIMEOUT)
        val answer = service.put(PutRequest.newBuilder().setResource(config("a")).build(), CALLER)
        answer.changed.assert().isTrue()
        val stored = service.get(get("a", answer.revision), CALLER)
        stored.config.content.assert().isEqualTo("a")
        stored.meta.revision.assert().isEqualTo(answer.revision)
    }

    @Test
    fun `a rollback to a revision the history does not hold is out of range, whether or not the Config exists`() =
        scenario {
            put(config("a"))
            listOf("a", "missing").forEach { name ->
                val failure = runCatching { resources.rollback(configKey(name), 12345, 0, CALLER) }
                    .exceptionOrNull() as CoSkyException
                failure.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_INVALID_ARGUMENT)
                val violation = failure.detail.badRequest.violationsList.single()
                violation.field.assert().isEqualTo("to_revision")
                violation.code.assert().isEqualTo(ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
            }
        }

    private companion object {
        const val NAMESPACE = "resources"
        const val WINDOW = 64
        val CALLER = Caller(Principal(Subject.Type.TYPE_USER, "someone"))
        val SHORT_TIMEOUT = 300.milliseconds
    }
}
