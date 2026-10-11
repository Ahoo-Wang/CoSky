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
package me.ahoo.cosky.discovery.spring.cloud.registry

import me.ahoo.cosky.discovery.RegistryProperties
import me.ahoo.cosky.discovery.RenewProperties
import me.ahoo.cosky.discovery.spring.cloud.discovery.CoSkyDiscoveryProperties
import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * CoSky Registry Properties.
 *
 * A JavaBean (no-arg constructor, mutable properties) on purpose: Spring Cloud's ConfigurationPropertiesRebinder
 * re-binds properties beans in place, which constructor-bound beans do not survive.
 *
 * @author ahoo wang
 */
@ConfigurationProperties(CoSkyRegistryProperties.PREFIX)
class CoSkyRegistryProperties {
    companion object {
        const val PREFIX = CoSkyDiscoveryProperties.PREFIX + ".registry"
    }

    var serviceId: String = ""
    var schema: String = "http"
    var host: String = ""
    var port: Int = 0
    var weight: Int = 1

    /**
     * Bound as `is-ephemeral`: the JavaBean accessors are named after `isEphemeral`, not `ephemeral`.
     */
    @get:JvmName("getIsEphemeral")
    @set:JvmName("setIsEphemeral")
    var isEphemeral: Boolean = true
    var ttl: Duration = Duration.ofSeconds(60)
    var timeout: Duration = Duration.ofSeconds(2)
    var metadata: MutableMap<String, String> = mutableMapOf()

    /**
     * The `instance_status` metadata the instance registers with.
     */
    var initialStatus: String = StatusConstants.STATUS_UP
    var renew: Renew = Renew()

    /**
     * @see RegistryProperties.renewEventCarriesTtlAt
     */
    var renewEventCarriesTtlAt: Boolean = false

    /**
     * @see RenewProperties
     */
    class Renew {
        var initialDelay: Duration = RenewProperties().initialDelay
        var period: Duration = RenewProperties().period

        fun toRenewProperties(): RenewProperties = RenewProperties(initialDelay = initialDelay, period = period)
    }
}
