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

import me.ahoo.cosky.discovery.RenewProperties
import me.ahoo.cosky.discovery.spring.cloud.discovery.CoSkyDiscoveryProperties
import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants
import org.apache.logging.log4j.util.Strings
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.cloud.commons.util.InetUtils
import java.time.Duration

/**
 * CoSky Registry Properties.
 *
 * @author ahoo wang
 */
@ConstructorBinding
@ConfigurationProperties(CoSkyRegistryProperties.PREFIX)
data class CoSkyRegistryProperties(inetUtils: InetUtils) {
    companion object {
        const val PREFIX = CoSkyDiscoveryProperties.PREFIX + ".registry"
    }

    private val hostInfo: InetUtils.HostInfo
    var serviceId: String? = null
    var schema = "http"
    var host: String
    var port = 0
    var weight = 1
    var isEphemeral = true
    var initialStatus = StatusConstants.STATUS_UP
    private var metadata: MutableMap<String, String> = HashMap()
    var ttl = Duration.ofSeconds(60)
    var secure: Boolean? = null
        private set
    var renew = RenewProperties()
    var timeout = Duration.ofSeconds(2)

    init {
        hostInfo = inetUtils.findFirstNonLoopbackHostInfo()
        host = hostInfo.ipAddress
        metadata[StatusConstants.INSTANCE_STATUS_KEY] = initialStatus
    }

    fun getMetadata(): Map<String, String> {
        return metadata
    }

    fun setMetadata(metadata: MutableMap<String, String>) {
        this.metadata = metadata
    }

    fun setSecure(secure: Boolean) {
        this.secure = secure
        if (Strings.isBlank(schema)) {
            schema = if (secure) "http" else "https"
        }
    }

}
