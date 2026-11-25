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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class WatchCacheTest {
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    @Test
    fun `a namespace this replica is still loading is not ready here, where another may be`() = runBlocking<Unit> {
        val memory = MemoryStore()
        memory.createNamespace(NAMESPACE)
        val loading = object : Store by memory {
            override fun readAll(namespace: String): Flow<Resource> = flow { awaitCancellation() }
        }
        val failure = runCatching { WatchCache(loading, WINDOW, scope).viewAt(NAMESPACE, 0, 50.milliseconds) }
            .exceptionOrNull() as CoSkyException
        failure.reason.assert().isEqualTo(ErrorReason.ERROR_REASON_REPLICA_NOT_READY)
        failure.detail.namespace.namespace.assert().isEqualTo(NAMESPACE)
    }

    private companion object {
        const val NAMESPACE = "prod"
        const val WINDOW = 16
    }
}
