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

import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.observe.CoSkyMetrics
import org.slf4j.LoggerFactory

/**
 * Says at start whether authentication is on: as `cosky_auth_enabled`, and in the log where it is off (design §7.9).
 * Off on a store that keeps its data is no development setup, so that is an error. It does not stop the start: the last
 * way back to a lost admin token is a server with authentication off, where nobody else can reach it.
 */
object AuthenticationStatus {
    private val log = LoggerFactory.getLogger(AuthenticationStatus::class.java)

    fun report(properties: CoSkyProperties, metrics: CoSkyMetrics) {
        val enabled = properties.auth.enabled
        metrics.authEnabled(enabled)
        when {
            enabled -> Unit

            properties.store.type == CoSkyProperties.MEMORY ->
                log.warn("Authentication is off: every caller is admin. Use it for development only.")

            else -> log.error(
                "Authentication is off (cosky.auth.enabled=false) on the {} store: every caller is admin, and may " +
                    "read, change and delete everything in it. Turn it on, unless nobody else can reach this server.",
                properties.store.type,
            )
        }
    }
}
