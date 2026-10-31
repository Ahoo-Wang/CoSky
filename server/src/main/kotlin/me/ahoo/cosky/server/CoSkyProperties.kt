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

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Server settings (design §7.6).
 */
@Suppress("MagicNumber") // The defaults of design §7.6.
@ConfigurationProperties("cosky")
data class CoSkyProperties(
    val store: Store = Store(),
    val watch: Watch = Watch(),
    val lease: Lease = Lease(),
    val read: Read = Read(),
) {
    data class Store(val redis: Redis = Redis())

    data class Redis(
        val uri: String = "redis://localhost:6379",
        val cluster: Boolean = false,
        val tailPollInterval: Duration = Duration.ofSeconds(1),
    )

    data class Read(val minRevisionTimeout: Duration = Duration.ofSeconds(3))

    data class Watch(
        val window: Int = 10_000,
        val progressInterval: Duration = Duration.ofSeconds(10),
        val maxPerStream: Int = 1000,
    )

    data class Lease(
        val reaperInterval: Duration = Duration.ofSeconds(1),
        val reaperGrace: Duration = Duration.ofSeconds(30),
        val reaperMaxPerTick: Int = 100,
        val lockTtl: Duration = Duration.ofSeconds(15),
        val protectionThreshold: Double = 0.5,
        val protectionMinLeases: Int = 10,
        val protectionMaxDuration: Duration = Duration.ofMinutes(5),
    )
}
