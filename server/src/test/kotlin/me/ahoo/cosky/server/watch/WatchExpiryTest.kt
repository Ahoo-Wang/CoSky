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

package me.ahoo.cosky.server.watch

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The server keeps no record of a person, so the expiry of the OIDC token a stream was opened with is what ends it
 * (design §7.8): otherwise one removed from a group at the provider would go on seeing what the group may.
 */
class WatchExpiryTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, WINDOW, scope)
    private val watches = WatchService(caches, CoSkyProperties())

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private fun person(expiresAt: Instant?) = Principal(Subject.Type.TYPE_USER, "ann", expiresAt = expiresAt)

    private fun scenario(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(VirtualThreads.dispatcher) {
        withTimeout(10.seconds, block)
    }

    @Test
    fun `a stream opened with an OIDC token ends when the token expires`() = scenario {
        val expiresAt = Instant.now().minus(Principal.CLOCK_SKEW).plusMillis(300)
        val stream = watches.stream(person(expiresAt), null, this) {}
        val failure = runCatching { stream.maintain() }.exceptionOrNull() as CoSkyException
        failure.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_UNAUTHENTICATED)
    }

    @Test
    fun `a stream opened with a token that does not expire goes on`() = scenario {
        val stream = watches.stream(person(null), null, this) {}
        withTimeoutOrNull(500.milliseconds) { stream.maintain() }.assert().isNull()
    }

    @Test
    fun `an OIDC principal whose token has expired is no longer valid`() = scenario {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        val access = AccessControl(caches)
        access.isValid(person(Instant.now().plusSeconds(60))).assert().isTrue()
        // As long as the server takes the token, with the clock skew it allows.
        access.isValid(person(Instant.now().minusSeconds(1))).assert().isTrue()
        access.isValid(person(Instant.now().minus(Principal.CLOCK_SKEW).minusSeconds(1))).assert().isFalse()
    }

    private companion object {
        const val WINDOW = 16
    }
}
