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

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class StoreBootstrapTest {
    private val store = MemoryStore()
    private val auth = CoSkyProperties.Auth(bootstrapToken = "cosky_abc_def")

    private suspend fun objects() = store.readAll(NamingRules.SYSTEM_NAMESPACE).toList()

    private suspend fun put(actor: String, resource: Resource) {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(resource)), actor)
    }

    @Test
    fun `an empty store gets the system namespace, default and the admin`() = runBlocking<Unit> {
        StoreBootstrap(store, auth).run()
        store.namespaceState(NamingRules.SYSTEM_NAMESPACE).assert().isEqualTo(NamespaceState.ACTIVE)
        store.namespaceState("default").assert().isEqualTo(NamespaceState.ACTIVE)
        objects().map { it.kind }
            .assert().containsExactlyInAnyOrder(Kind.KIND_NAMESPACE, Kind.KIND_SERVICE_ACCOUNT, Kind.KIND_ROLE_BINDING)
    }

    @Test
    fun `with auth off nobody is created`() = runBlocking<Unit> {
        StoreBootstrap(store, CoSkyProperties.Auth(enabled = false, bootstrapToken = "cosky_abc_def")).run()
        objects().map { it.kind }.assert().containsExactly(Kind.KIND_NAMESPACE)
    }

    @Test
    fun `a store with another namespace does not get default back`() = runBlocking<Unit> {
        put(
            "test",
            resource {
                meta = objectMeta {
                    namespace = NamingRules.SYSTEM_NAMESPACE
                    name = "orders"
                }
                this.namespace = namespaceSpec {}
            },
        )
        StoreBootstrap(store, auth).run()
        store.namespaceState("default").assert().isNull()
    }

    @Test
    fun `a replica that loses the race for the admin starts anyway`() = runBlocking<Unit> {
        put(
            "test",
            resource {
                meta = objectMeta {
                    namespace = NamingRules.SYSTEM_NAMESPACE
                    name = "admin"
                }
                serviceAccount = serviceAccountSpec {}
            },
        )
        StoreBootstrap(store, auth).run()
        objects().none { it.kind == Kind.KIND_ROLE_BINDING }.assert().isTrue()
    }
}
