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

package me.ahoo.cosky.spring

import me.ahoo.cosky.client.CoSkyClientOptions
import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration
import kotlin.time.toKotlinDuration

/**
 * Client settings (design §7.7). Bound before the context exists as well, since configuration loading needs them.
 */
@Suppress("MagicNumber") // The defaults of design §7.5 and §7.7.
@ConfigurationProperties(CoSkyProperties.PREFIX)
data class CoSkyProperties(
    val enabled: Boolean = true,
    val endpoints: List<String> = listOf("localhost:9090"),
    val token: String? = null,
    val namespace: String = "default",
    val snapshot: Snapshot = Snapshot(),
    val registry: Registry = Registry(),
    val discovery: Discovery = Discovery(),
    val config: Config = Config(),
) {
    data class Snapshot(
        val enabled: Boolean = true,
        val dir: Path = Path.of(System.getProperty("user.home"), ".cosky"),
        val includeConfig: Boolean = true,
    )

    data class Registry(
        val enabled: Boolean = true,
        val weight: Int = 1,
        val metadata: Map<String, String> = emptyMap(),
    )

    data class Discovery(val enabled: Boolean = true, val loadTimeout: Duration = Duration.ofSeconds(5))

    data class Config(val refresh: Refresh = Refresh(), val loadTimeout: Duration = Duration.ofSeconds(5)) {
        data class Refresh(val enabled: Boolean = true)
    }

    fun toOptions(applicationName: String? = null): CoSkyClientOptions = CoSkyClientOptions(
        endpoints = endpoints,
        token = token,
        clientName = applicationName,
        snapshotDir = snapshot.dir.takeIf { snapshot.enabled },
        snapshotIncludeConfig = snapshot.includeConfig,
    )

    val configLoadTimeout: kotlin.time.Duration
        get() = config.loadTimeout.toKotlinDuration()

    val discoveryLoadTimeout: kotlin.time.Duration
        get() = discovery.loadTimeout.toKotlinDuration()

    companion object {
        const val PREFIX = "cosky"
    }
}
