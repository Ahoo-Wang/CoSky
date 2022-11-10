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

import java.util.concurrent.TimeUnit

/**
 * Service Instance.
 *
 * @author ahoo wang
 */
data class ServiceInstance(
    val delegate: Instance,
    val weight: Int = 1,
    val isEphemeral: Boolean = true,
    val ttlAt: Long = -1,
    val metadata: Map<String, String> = mapOf()
) : Instance by delegate {

    val isExpired: Boolean
        get() {
            if (!isEphemeral) {
                return false
            }
            val nowTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
            return ttlAt < nowTimeSeconds
        }

    companion object {
        @JvmField
        val NOT_FOUND =
            ServiceInstance(delegate = Instance.asInstance(serviceId = "", schema = "", host = "", port = 0))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServiceInstance) return false

        if (delegate != other.delegate) return false

        return true
    }

    override fun hashCode(): Int {
        return delegate.hashCode()
    }
}
