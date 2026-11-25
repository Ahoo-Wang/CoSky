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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class AuthenticationStatusTest {
    private val registry = SimpleMeterRegistry()
    private val logger = LoggerFactory.getLogger(AuthenticationStatus::class.java) as Logger
    private val lines = ListAppender<ILoggingEvent>()

    @BeforeEach
    fun start() {
        lines.start()
        logger.addAppender(lines)
    }

    @AfterEach
    fun stop() {
        logger.detachAppender(lines)
    }

    private fun report(enabled: Boolean, store: String) {
        val properties = CoSkyProperties(
            store = CoSkyProperties.Store(type = store),
            auth = CoSkyProperties.Auth(enabled = enabled),
        )
        AuthenticationStatus.report(properties, CoSkyMetrics(registry))
    }

    private fun gauge(): Double = registry.get("cosky_auth_enabled").gauge().value()

    @Test
    fun `authentication off on Redis is an error that says every caller is admin, and the gauge says 0`() {
        report(enabled = false, store = CoSkyProperties.REDIS)

        gauge().assert().isEqualTo(0.0)
        val line = lines.list.single()
        line.level.assert().isEqualTo(Level.ERROR)
        line.formattedMessage.assert().contains("Authentication is off").contains("every caller is admin")
    }

    @Test
    fun `authentication off in memory, for development, is a warning`() {
        report(enabled = false, store = CoSkyProperties.MEMORY)

        gauge().assert().isEqualTo(0.0)
        lines.list.single().level.assert().isEqualTo(Level.WARN)
    }

    @Test
    fun `authentication on says 1, and nothing else`() {
        report(enabled = true, store = CoSkyProperties.REDIS)

        gauge().assert().isEqualTo(1.0)
        lines.list.assert().isEmpty()
    }

    @Test
    fun `a server that never says reports nothing rather than authentication off`() {
        CoSkyMetrics(registry)

        registry.find("cosky_auth_enabled").gauge().assert().isNull()
    }
}
