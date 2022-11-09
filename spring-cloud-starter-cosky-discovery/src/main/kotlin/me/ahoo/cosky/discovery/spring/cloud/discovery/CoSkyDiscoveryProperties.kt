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
package me.ahoo.cosky.discovery.spring.cloud.discovery

import me.ahoo.cosky.spring.cloud.CoSkyProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import java.time.Duration

/**
 * CoSky Discovery Properties.
 *
 * @author ahoo wang
 */
@ConstructorBinding
@ConfigurationProperties(CoSkyDiscoveryProperties.PREFIX)
data class CoSkyDiscoveryProperties(
    val enabled: Boolean = true,
    val order: Int = 0,
    val timeout: Duration = Duration.ofSeconds(2)
) {
    companion object {
        const val PREFIX = CoSkyProperties.PREFIX + ".discovery"
    }
}
