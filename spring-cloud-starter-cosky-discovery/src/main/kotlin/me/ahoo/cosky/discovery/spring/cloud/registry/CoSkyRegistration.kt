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

import me.ahoo.cosky.discovery.Instance
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.ServiceInstance.Companion.asServiceInstance
import org.springframework.cloud.client.serviceregistry.Registration
import java.net.URI

/**
 * CoSky Registration.
 *
 * @author ahoo wang
 */
data class CoSkyRegistration(
    private val serviceId: String,
    private var scheme: String,
    private val host: String,
    private var port: Int,
    val weight: Int = 1,
    val isEphemeral: Boolean = true,
    private val metadata: MutableMap<String, String> = mutableMapOf(),
) : Registration {

    fun asServiceInstance(): ServiceInstance {
        return Instance.asInstance(serviceId, scheme, host, port)
            .asServiceInstance(
                weight = weight,
                isEphemeral = isEphemeral,
                metadata = metadata,
            )
    }

    override fun getServiceId(): String = serviceId

    override fun getHost(): String = host

    override fun getPort(): Int = port

    fun setPort(port: Int) {
        this.port = port
    }

    override fun isSecure(): Boolean {
        return Instance.isSecure(scheme)
    }

    override fun getUri(): URI = Instance.asUri(scheme, host, port)

    override fun getMetadata(): MutableMap<String, String> = metadata

    override fun getScheme(): String = scheme

    fun setSchema(scheme: String) {
        this.scheme = scheme
    }

    override fun getInstanceId(): String {
        return Instance.asInstanceId(serviceId, scheme, host, port)
    }
}
