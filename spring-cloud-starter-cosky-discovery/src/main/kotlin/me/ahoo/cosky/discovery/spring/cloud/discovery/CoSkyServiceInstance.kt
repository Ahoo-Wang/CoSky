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

import me.ahoo.cosky.discovery.Instance.Companion.asUri
import me.ahoo.cosky.discovery.Instance.Companion.isSecure
import me.ahoo.cosky.discovery.ServiceInstance
import java.net.URI

/**
 * Cosky Service Instance.
 *
 * @author ahoo wang
 */
class CoSkyServiceInstance(val delegate: ServiceInstance) :
    org.springframework.cloud.client.ServiceInstance {
    override fun getInstanceId(): String {
        return delegate.instanceId
    }

    override fun getServiceId(): String {
        return delegate.serviceId
    }

    override fun getHost(): String {
        return delegate.host
    }

    override fun getPort(): Int {
        return delegate.port
    }

    override fun isSecure(): Boolean {
        return delegate.isSecure
    }

    override fun getUri(): URI {
        return delegate.asUri()
    }

    override fun getMetadata(): Map<String, String> {
        return delegate.metadata
    }

    override fun getScheme(): String {
        return delegate.schema
    }

}
