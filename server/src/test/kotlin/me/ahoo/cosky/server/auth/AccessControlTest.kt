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

package me.ahoo.cosky.server.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class AccessControlTest {
    /**
     * Reads differently from now on, as a timeline that was replaced does.
     */
    private class DriftingStore(private val delegate: Store) : Store by delegate {
        @Volatile
        var hidden: String? = null

        override fun readAll(namespace: String): Flow<Resource> =
            delegate.readAll(namespace).filter { it.meta.name != hidden }
    }

    @Test
    fun `rules from a timeline that was replaced at the same revision are not kept`() = runBlocking<Unit> {
        val memory = MemoryStore()
        val store = DriftingStore(memory)
        val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
        try {
            withTimeout(10.seconds) {
                val token = Tokens.generate()
                memory.createNamespace(NamingRules.SYSTEM_NAMESPACE)
                memory.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(account(token))), "test")
                val caches = WatchCache(store, WINDOW, scope)
                val access = AccessControl(caches)
                val system = caches.of(NamingRules.SYSTEM_NAMESPACE)
                val before = system.views.first { it != null && it.revision == 1L }!!
                access.authenticate(token.plaintext).name.assert().isEqualTo(ACCOUNT)

                store.hidden = ACCOUNT
                memory.rotateEpoch(NamingRules.SYSTEM_NAMESPACE, before.epoch)
                val after = system.views.first { it != null && it.epoch != before.epoch }!!
                after.revision.assert().isEqualTo(before.revision)

                val failure = runCatching { access.authenticate(token.plaintext) }.exceptionOrNull() as CoSkyException
                failure.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_UNAUTHENTICATED)
            }
        } finally {
            scope.cancel()
        }
    }

    private fun account(token: ParsedToken) = resource {
        meta = objectMeta {
            namespace = NamingRules.SYSTEM_NAMESPACE
            name = ACCOUNT
        }
        serviceAccount = serviceAccountSpec {
            tokens += TokenRef.newBuilder().setId(token.id).setSha256(Tokens.sha256Hex(token.secret)).build()
        }
    }

    private companion object {
        const val ACCOUNT = "a"
        const val WINDOW = 16
    }
}
