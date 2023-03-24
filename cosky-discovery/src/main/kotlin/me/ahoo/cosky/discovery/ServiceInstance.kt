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
package me.ahoo.cosky.discovery

import me.ahoo.cosky.discovery.ServiceInstance.Companion.TTL_AT_FOREVER
import java.util.concurrent.TimeUnit

/**
 * Service Instance.
 *
 * @author ahoo wang
 */
interface ServiceInstance : Instance {

    val weight: Int
        get() = 1
    val isEphemeral: Boolean
        get() = true
    val ttlAt: Long
        get() = TTL_AT_FOREVER
    val metadata: Map<String, String>
        get() = mapOf()
    val isExpired: Boolean
        get() {
            if (!isEphemeral) {
                return false
            }
            val nowTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
            return ttlAt < nowTimeSeconds
        }

    companion object {
        val NOT_FOUND: ServiceInstance =
            ServiceInstanceData(delegate = Instance.asInstance(serviceId = "", schema = "", host = "", port = 0))

        const val TTL_AT_FOREVER = -1L

        fun Instance.asServiceInstance(
            weight: Int = 1,
            isEphemeral: Boolean = true,
            ttlAt: Long = TTL_AT_FOREVER,
            metadata: Map<String, String> = mapOf(),
        ): ServiceInstance {
            return ServiceInstanceData(
                delegate = if (this is ServiceInstanceData) this.delegate else this,
                weight = weight,
                isEphemeral = isEphemeral,
                ttlAt = ttlAt,
                metadata = metadata,
            )
        }

        fun ServiceInstance.withTtlAt(ttlAt: Long): ServiceInstance {
            return asServiceInstance(
                weight = weight,
                isEphemeral = isEphemeral,
                ttlAt = ttlAt,
                metadata = metadata,
            )
        }

        fun ServiceInstance.withWeight(
            weight: Int,
        ): ServiceInstance {
            return asServiceInstance(
                weight = weight,
                isEphemeral = isEphemeral,
                ttlAt = ttlAt,
                metadata = metadata,
            )
        }

        fun ServiceInstance.withIsEphemeral(
            isEphemeral: Boolean,
        ): ServiceInstance {
            return asServiceInstance(
                weight = weight,
                isEphemeral = isEphemeral,
                ttlAt = ttlAt,
                metadata = metadata,
            )
        }
    }
}

private data class ServiceInstanceData(
    val delegate: Instance,
    override val weight: Int = 1,
    override val isEphemeral: Boolean = true,
    override val ttlAt: Long = TTL_AT_FOREVER,
    override val metadata: Map<String, String> = mapOf(),
) : ServiceInstance, Instance by delegate {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServiceInstanceData) return false

        if (delegate != other.delegate) return false

        return true
    }

    override fun hashCode(): Int {
        return delegate.hashCode()
    }
}
