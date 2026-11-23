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
 *
 * Bound through its setters, as Spring Boot's own settings are, so that a setting added later is one more property
 * rather than another constructor. Every class here can be made empty and then set.
 */
@Suppress("MagicNumber") // The defaults of design §7.5 and §7.7.
@ConfigurationProperties(CoSkyProperties.PREFIX)
class CoSkyProperties {
    var enabled: Boolean = true
    var endpoints: List<String> = listOf("localhost:9090")
    var token: String? = null

    /** A file holding the token, read again once it changes: a rotated token needs no restart. */
    var tokenFile: Path? = null
    var tls: Tls = Tls()
    var namespace: String = "default"
    var snapshot: Snapshot = Snapshot()
    var registry: Registry = Registry()
    var discovery: Discovery = Discovery()
    var config: Config = Config()

    class Tls {
        var enabled: Boolean = false

        /** A PEM file of the certificates to trust, for a private CA. The JVM's trust store decides otherwise. */
        var trustedCertificates: Path? = null
    }

    class Snapshot {
        var enabled: Boolean = true
        var dir: Path = Path.of(System.getProperty("user.home"), ".cosky")
        var includeConfig: Boolean = true
    }

    class Registry {
        var enabled: Boolean = true
        var weight: Int = 1
        var metadata: Map<String, String> = emptyMap()
    }

    class Discovery {
        var enabled: Boolean = true
        var loadTimeout: Duration = Duration.ofSeconds(5)
    }

    class Config {
        var refresh: Refresh = Refresh()
        var loadTimeout: Duration = Duration.ofSeconds(5)

        class Refresh {
            var enabled: Boolean = true
        }
    }

    fun toOptions(applicationName: String? = null): CoSkyClientOptions = CoSkyClientOptions(endpoints) {
        token = this@CoSkyProperties.token
        tokenFile = this@CoSkyProperties.tokenFile
        clientName = applicationName
        snapshotDir = snapshot.dir.takeIf { snapshot.enabled }
        snapshotIncludeConfig = snapshot.includeConfig
        tls = this@CoSkyProperties.tls.enabled
        tlsTrustedCertificates = this@CoSkyProperties.tls.trustedCertificates
    }

    val configLoadTimeout: kotlin.time.Duration
        get() = config.loadTimeout.toKotlinDuration()

    val discoveryLoadTimeout: kotlin.time.Duration
        get() = discovery.loadTimeout.toKotlinDuration()

    /**
     * Leaves the token out, since Spring puts these in messages, such as that of a Config it cannot find.
     */
    override fun toString(): String = "CoSkyProperties(endpoints=$endpoints, namespace=$namespace)"

    companion object {
        const val PREFIX = "cosky"
    }
}
