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

import me.ahoo.cosky.discovery.RenewInstanceService
import me.ahoo.cosky.discovery.ServiceRegistry
import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants
import kotlin.String

/**
 * CoSky Service Registry.
 *
 * @author ahoo wang
 */
class CoSkyServiceRegistry(
    private val serviceRegistry: ServiceRegistry,
    private val renewInstanceService: RenewInstanceService, private val coskyRegistryProperties: CoSkyRegistryProperties
) : org.springframework.cloud.client.serviceregistry.ServiceRegistry<CoSkyRegistration> {
    override fun register(registration: CoSkyRegistration) {
        val instance = registration.delegate
        val succeeded = serviceRegistry.register(instance).block(coskyRegistryProperties.timeout)
        check(succeeded == true) { "register instance failed! $instance" }
        renewInstanceService.start()
    }

    override fun deregister(registration: CoSkyRegistration) {
        val instance = registration.delegate
        val succeeded = serviceRegistry.deregister(instance).block(coskyRegistryProperties.timeout)
        check(succeeded == true) { "deregister instance failed! $instance" }
    }

    override fun close() {
        renewInstanceService.stop()
    }

    override fun setStatus(registration: CoSkyRegistration, status: String) {
        registration.metadata[StatusConstants.INSTANCE_STATUS_KEY] = status
        val instance = registration.delegate
        serviceRegistry
            .setMetadata(instance.serviceId, instance.instanceId, StatusConstants.INSTANCE_STATUS_KEY, status)
            .block(coskyRegistryProperties.timeout)
    }

    override fun <T> getStatus(registration: CoSkyRegistration): T {
        return registration.metadata[StatusConstants.INSTANCE_STATUS_KEY] as T
    }
}
