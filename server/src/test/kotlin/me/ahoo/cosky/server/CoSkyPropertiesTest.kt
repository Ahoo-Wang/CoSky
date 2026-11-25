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

package me.ahoo.cosky.server

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import java.time.Duration

class CoSkyPropertiesTest {
    /** As the environment variables of a process are bound. */
    private fun bind(environment: Map<String, String>): CoSkyProperties {
        val variables =
            SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environment)
        return Binder(checkNotNull(ConfigurationPropertySource.from(variables)))
            .bind("cosky", CoSkyProperties::class.java)
            .get()
    }

    @Test
    fun `a duration without a unit is in seconds`() {
        val properties = bind(
            mapOf(
                "COSKY_LEASE_REAPER_GRACE" to "30",
                "COSKY_LEASE_REAPER_INTERVAL" to "2",
                "COSKY_LEASE_LOCK_TTL" to "20",
                "COSKY_LEASE_PROTECTION_MAX_DURATION" to "600",
                "COSKY_STORE_REDIS_TAIL_POLL_INTERVAL" to "3",
                "COSKY_WATCH_PROGRESS_INTERVAL" to "4",
                "COSKY_WATCH_ANTI_ENTROPY_INTERVAL" to "60",
                "COSKY_READ_MIN_REVISION_TIMEOUT" to "5",
                "COSKY_VIEW_TOPOLOGY_FLUSH_INTERVAL" to "45",
            ),
        )

        properties.lease.reaperGrace.assert().isEqualTo(Duration.ofSeconds(30))
        properties.lease.reaperInterval.assert().isEqualTo(Duration.ofSeconds(2))
        properties.lease.lockTtl.assert().isEqualTo(Duration.ofSeconds(20))
        properties.lease.protectionMaxDuration.assert().isEqualTo(Duration.ofMinutes(10))
        properties.store.redis.tailPollInterval.assert().isEqualTo(Duration.ofSeconds(3))
        properties.watch.progressInterval.assert().isEqualTo(Duration.ofSeconds(4))
        properties.watch.antiEntropyInterval.assert().isEqualTo(Duration.ofMinutes(1))
        properties.read.minRevisionTimeout.assert().isEqualTo(Duration.ofSeconds(5))
        properties.view.topologyFlushInterval.assert().isEqualTo(Duration.ofSeconds(45))
    }

    @Test
    fun `a duration with a unit keeps it`() {
        val properties = bind(mapOf("COSKY_LEASE_REAPER_GRACE" to "500ms"))

        properties.lease.reaperGrace.assert().isEqualTo(Duration.ofMillis(500))
    }
}
