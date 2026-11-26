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

package me.ahoo.cosky.client

import io.grpc.Server
import io.grpc.ServerInterceptors
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.v1.tokenRef
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.adapter.grpc.AuthInterceptor
import me.ahoo.cosky.server.adapter.grpc.WatchGrpcService
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.server.watch.WatchService
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A server with sign-in on refuses a stream without a valid token as a whole, before any watch on it: a load waiting
 * for its view says why, as for a watch the server cancels, rather than running into its timeout (design §7.5).
 */
class StreamRefusedTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val name = "cosky-${UUID.randomUUID()}"
    private val token = Tokens.generate()
    private var server: Server? = null
    private var client: CoSkyClient? = null

    @TempDir
    lateinit var directory: Path

    @AfterEach
    fun stop() {
        client?.close()
        server?.shutdownNow()
        scope.cancel()
    }

    private suspend fun start() {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        store.createNamespace(NAMESPACE)
        val account = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                name = "app"
            }
            serviceAccount = serviceAccountSpec {
                tokens += tokenRef {
                    id = token.id
                    sha256 = Tokens.sha256Hex(token.secret)
                }
            }
        }
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(account)), "test")
        val caches = WatchCache(store, WINDOW, scope)
        // Loaded, as a server's readiness waits for: until then it could not tell a token's account.
        caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null }
        val access = AccessControl(caches)
        // Signing in is what is tried here; whatever the account is, it may watch.
        val watches = WatchGrpcService(WatchService(caches, CoSkyProperties()))
        server = InProcessServerBuilder.forName(name)
            .addService(ServerInterceptors.intercept(watches, AuthInterceptor(access, AuditLogger())))
            .build()
            .start()
    }

    private fun client(tokenFile: Path): CoSkyClient = CoSkyClient(
        CoSkyClientOptions(listOf("only")) {
            this.tokenFile = tokenFile
            reconnectMin = 10.milliseconds
            reconnectMax = 100.milliseconds
            channelFactory = { InProcessChannelBuilder.forName(name).build() }
        },
    ).also { client = it }

    @Test
    fun `a load on a stream refused for want of a token says why, and succeeds once the token is there`() =
        runBlocking<Unit>(Dispatchers.Default) {
            start()
            val tokenFile = directory.resolve("token")
            val configs = client(tokenFile).namespace(NAMESPACE).configs

            val refused = withTimeout(LOAD_TIMEOUT) { runCatching { configs.load(CONFIG) }.exceptionOrNull() }
            (refused as CoSkyException).reason.assert().isEqualTo(ErrorReason.ERROR_REASON_UNAUTHENTICATED)

            Files.writeString(tokenFile, token.plaintext)
            withTimeout(LOAD_TIMEOUT) {
                while (runCatching { configs.load(CONFIG) }.isFailure) {
                    delay(10.milliseconds)
                }
            }
            configs.load(CONFIG).assert().isNull()
        }

    private companion object {
        const val NAMESPACE = "prod"
        const val CONFIG = "application.yaml"
        const val WINDOW = 64
        val LOAD_TIMEOUT = 5.seconds
    }
}
