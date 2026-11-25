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

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySource
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.env.SystemEnvironmentPropertySource
import java.nio.file.Path
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class CoSkyPropertiesTest {
    @Test
    fun `a token file is bound from a property, and reaches the client`() {
        val properties = Binder(MapConfigurationPropertySource(mapOf("cosky.token-file" to "/run/cosky/token")))
            .bind(CoSkyProperties.PREFIX, CoSkyProperties::class.java).get()

        properties.tokenFile.assert().isEqualTo(Path.of("/run/cosky/token"))
        properties.toOptions().tokenFile.assert().isEqualTo(Path.of("/run/cosky/token"))
        properties.toOptions().token.assert().isNull()
    }

    @Test
    fun `a duration without a unit is in seconds`() {
        val environment = mapOf("COSKY_CONFIG_LOAD_TIMEOUT" to "7", "COSKY_DISCOVERY_LOAD_TIMEOUT" to "30")
        val variables =
            SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, environment)
        val properties = Binder(checkNotNull(ConfigurationPropertySource.from(variables)))
            .bind(CoSkyProperties.PREFIX, CoSkyProperties::class.java).get()

        properties.config.loadTimeout.assert().isEqualTo(Duration.ofSeconds(7))
        properties.discovery.loadTimeout.assert().isEqualTo(Duration.ofSeconds(30))
        properties.configLoadTimeout.assert().isEqualTo(7.seconds)
    }

    @Test
    fun `a blank token, as an unset variable gives, counts as none beside a token file`() {
        val properties = Binder(
            MapConfigurationPropertySource(mapOf("cosky.token" to "", "cosky.token-file" to "/run/cosky/token")),
        ).bind(CoSkyProperties.PREFIX, CoSkyProperties::class.java).get()

        val options = properties.toOptions()
        options.token.assert().isNull()
        options.tokenFile.assert().isEqualTo(Path.of("/run/cosky/token"))
    }
}
