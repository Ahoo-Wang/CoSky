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

package me.ahoo.cosky.server.audit

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.CancellationException
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.store.StoreUnavailableException
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

/**
 * Every write leaves a line, however it ends (design §7.8).
 */
class AuditLoggerTest {
    private val audit = LoggerFactory.getLogger("cosky.audit") as Logger
    private val lines = ListAppender<ILoggingEvent>()
    private val previousLevel = audit.level
    private val logger = AuditLogger()
    private val key = ResourceKey.newBuilder().setNamespace("prod").setKind(Kind.KIND_CONFIG).setName("a").build()

    @BeforeEach
    fun start() {
        audit.level = Level.INFO
        lines.start()
        audit.addAppender(lines)
    }

    @AfterEach
    fun stop() {
        audit.detachAppender(lines)
        audit.level = previousLevel
    }

    private fun resultOf(failure: Throwable): String {
        runCatching { logger.audited<Long>(Caller.ANONYMOUS, Verb.VERB_PUT, key, { it }) { throw failure } }
            .exceptionOrNull().assert().isSameAs(failure)
        return Regex("\"result\":\"([A-Z_]+)\"").find(lines.list.last().formattedMessage)!!.groupValues[1]
    }

    @Test
    fun `a write that fails as the store goes away, or as its caller does, leaves a line`() {
        resultOf(StoreUnavailableException("Redis timed out.")).assert().isEqualTo("UNAVAILABLE")
        resultOf(CancellationException("The caller went away.")).assert().isEqualTo("CANCELLED")
        resultOf(IllegalStateException("Unexpected.")).assert().isEqualTo("UNKNOWN")
        resultOf(CoSkyException.permissionDenied("No.")).assert().isEqualTo("PERMISSION_DENIED")
        lines.list.size.assert().isEqualTo(4)
    }

    @Test
    fun `a write that lands leaves a line with its revision`() {
        logger.audited<Long>(Caller.ANONYMOUS, Verb.VERB_PUT, key, { it }) { 7L }.assert().isEqualTo(7L)
        lines.list.single().formattedMessage.assert().contains("\"revision\":7").contains("\"result\":\"OK\"")
    }
}
