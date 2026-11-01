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

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.server.RunningServer
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class WatchWindowBytesTest {
    @Test
    fun `a limit in bytes makes a client that fell out of the window start over`() = runBlocking<Unit> {
        val properties = mapOf(
            "spring.main.web-application-type" to "none",
            "cosky.store.type" to "memory",
            "cosky.auth.enabled" to "false",
            "cosky.watch.window-bytes" to "1KB",
        )
        RunningServer(properties).use { server ->
            withTimeout(30.seconds) {
                server.createNamespace(NAMESPACE)
                repeat(3) { server.putConfig(NAMESPACE, "a", "$it".repeat(600)) }
                val request = ListRequest.newBuilder()
                    .setNamespace(NAMESPACE)
                    .setKind(Kind.KIND_CONFIG)
                    .setMinRevision(3)
                    .build()
                val epoch = server.resources.list(request).epoch
                val first = server.watch(NAMESPACE, Kind.KIND_CONFIG, fromRevision = 1, fromEpoch = epoch).first()
                first.hasGone().assert().isTrue()
            }
        }
    }

    private companion object {
        const val NAMESPACE = "trial"
    }
}
