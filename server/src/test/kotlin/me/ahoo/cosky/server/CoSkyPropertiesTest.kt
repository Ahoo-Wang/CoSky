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
    /** As the environment variables of a process are bound, to the defaults where none is set. */
    private fun bind(environment: Map<String, String>): CoSkyProperties {
        val variables =
            SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environment)
        return Binder(checkNotNull(ConfigurationPropertySource.from(variables)))
            .bindOrCreate("cosky", CoSkyProperties::class.java)
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
    fun `a value that cannot work stops the start, naming the property and the value`() {
        val refused = mapOf(
            mapOf("COSKY_LEASE_REAPER_INTERVAL" to "0") to "cosky.lease.reaper-interval is 0s",
            mapOf("COSKY_LEASE_REAPER_INTERVAL" to "2", "COSKY_LEASE_LOCK_TTL" to "2") to "cosky.lease.lock-ttl is 2s",
            mapOf("COSKY_LEASE_REAPER_GRACE" to "-1") to "cosky.lease.reaper-grace is -1s",
            mapOf("COSKY_LEASE_REAPER_MAX_PER_TICK" to "0") to "cosky.lease.reaper-max-per-tick is 0",
            mapOf("COSKY_LEASE_PROTECTION_THRESHOLD" to "1.5") to "cosky.lease.protection-threshold is 1.5",
            mapOf("COSKY_LEASE_PROTECTION_MIN_LEASES" to "-1") to "cosky.lease.protection-min-leases is -1",
            mapOf("COSKY_LEASE_PROTECTION_MAX_DURATION" to "-5") to "cosky.lease.protection-max-duration is -5s",
            mapOf("COSKY_WATCH_WINDOW" to "0") to "cosky.watch.window is 0",
            mapOf("COSKY_WATCH_WINDOW_BYTES" to "0") to "cosky.watch.window-bytes is 0B",
            mapOf("COSKY_WATCH_PROGRESS_INTERVAL" to "0") to "cosky.watch.progress-interval is 0s",
            mapOf("COSKY_WATCH_MAX_PER_STREAM" to "0") to "cosky.watch.max-per-stream is 0",
            mapOf("COSKY_WATCH_ANTI_ENTROPY_INTERVAL" to "0") to "cosky.watch.anti-entropy-interval is 0s",
            mapOf("COSKY_READ_MIN_REVISION_TIMEOUT" to "0") to "cosky.read.min-revision-timeout is 0s",
            mapOf("COSKY_VIEW_TOPOLOGY_FLUSH_INTERVAL" to "0") to "cosky.view.topology-flush-interval is 0s",
            mapOf("COSKY_STORE_REDIS_TAIL_POLL_INTERVAL" to "0ms") to "cosky.store.redis.tail-poll-interval is 0s",
            mapOf("COSKY_STORE_TYPE" to "mongo") to "cosky.store.type is 'mongo'",
        )
        refused.forEach { (environment, description) ->
            val failure = generateSequence(runCatching { bind(environment) }.exceptionOrNull()) { it.cause }
                .filterIsInstance<StartupFailure>()
                .firstOrNull()
            failure.assert().describedAs("$environment").isNotNull()
            checkNotNull(failure).message.assert().startsWith(description)
            val property = description.substringBefore(" is ")
            val variable = property.uppercase().replace('.', '_').replace('-', '_')
            failure.action.assert().contains(property).contains(variable)
        }
    }

    @Test
    fun `zero turns the grace and the pause on mass expiry off, which is no mistake`() {
        val properties = bind(
            mapOf(
                "COSKY_LEASE_REAPER_GRACE" to "0",
                "COSKY_LEASE_PROTECTION_MAX_DURATION" to "0",
                "COSKY_LEASE_PROTECTION_MIN_LEASES" to "0",
                "COSKY_LEASE_PROTECTION_THRESHOLD" to "1",
            ),
        )

        properties.lease.reaperGrace.assert().isEqualTo(Duration.ZERO)
        properties.lease.protectionThreshold.assert().isEqualTo(1.0)
    }

    @Test
    fun `what Kubernetes sets for a Service named cosky is no setting, and stops nothing`() {
        val properties = bind(
            mapOf(
                "COSKY_SERVICE_HOST" to "10.0.0.1",
                "COSKY_SERVICE_PORT" to "8080",
                "COSKY_SERVICE_PORT_GRPC" to "9090",
                "COSKY_PORT" to "tcp://10.0.0.1:8080",
                "COSKY_PORT_8080_TCP" to "tcp://10.0.0.1:8080",
                "COSKY_PORT_8080_TCP_ADDR" to "10.0.0.1",
                "COSKY_PORT_8080_TCP_PORT" to "8080",
                "COSKY_PORT_8080_TCP_PROTO" to "tcp",
                // And for one named cosky-lease, under a prefix of settings.
                "COSKY_LEASE_SERVICE_HOST" to "10.0.0.2",
                "COSKY_LEASE_PORT" to "tcp://10.0.0.2:8080",
            ),
        )

        properties.assert().isEqualTo(CoSkyProperties())
    }

    @Test
    fun `a duration with a unit keeps it`() {
        val properties = bind(mapOf("COSKY_LEASE_REAPER_GRACE" to "500ms"))

        properties.lease.reaperGrace.assert().isEqualTo(Duration.ofMillis(500))
    }
}
