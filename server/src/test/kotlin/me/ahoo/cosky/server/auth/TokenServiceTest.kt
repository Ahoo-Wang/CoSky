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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class TokenServiceTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, 16, scope)
    private val audit = LoggerFactory.getLogger("cosky.audit") as Logger
    private val lines = ListAppender<ILoggingEvent>()
    private val previousLevel = audit.level

    @BeforeEach
    fun start(): Unit = runBlocking {
        audit.level = Level.INFO
        lines.start()
        audit.addAppender(lines)
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        val account = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                name = ACCOUNT
            }
            serviceAccount = serviceAccountSpec {}
        }
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(account)), "test")
    }

    @AfterEach
    fun stop() {
        audit.detachAppender(lines)
        audit.level = previousLevel
        scope.cancel()
    }

    @Test
    fun `creating and revoking a token leave an audit line, without its secret`() = runBlocking<Unit> {
        val tokens = TokenService(store, caches)
        val created = tokens.create(ACCOUNT, null, Caller(Principal.ANONYMOUS, "10.0.0.1")).token
        tokens.revoke(ACCOUNT, created.id, Caller.ANONYMOUS)

        val (create, revoke) = lines.list.map { it.formattedMessage }
        create.assert().contains("\"verb\":\"PUT\"", "\"kind\":\"SERVICE_ACCOUNT\"", "\"result\":\"OK\"")
        create.assert().contains("\"name\":\"$ACCOUNT/tokens/${created.id}\"", "\"remote\":\"10.0.0.1\"")
        create.assert().doesNotContain(created.secret)
        revoke.assert().contains("\"verb\":\"DELETE\"", "\"result\":\"OK\"")
    }

    @Test
    fun `an attempt that is refused leaves an audit line too`() = runBlocking<Unit> {
        val denied = object : Authorizer {
            override val changes: Flow<Unit> = emptyFlow()

            override fun isValid(principal: Principal): Boolean = true

            override fun permission(principal: Principal, verb: Verb, kind: Kind, namespace: String): Permission =
                Permission.None
        }
        val tokens = TokenService(store, caches, denied)
        runCatching { tokens.create(ACCOUNT, null, Caller.ANONYMOUS) }.exceptionOrNull()
            .assert().isInstanceOf(CoSkyException::class.java)
        lines.list.single().formattedMessage.assert().contains("\"result\":\"PERMISSION_DENIED\"")
    }

    private companion object {
        const val ACCOUNT = "a"
    }
}
