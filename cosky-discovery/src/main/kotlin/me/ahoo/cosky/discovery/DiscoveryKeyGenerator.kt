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

import me.ahoo.cosky.core.CoSky

/**
 * Discovery Key Generator.
 *
 * @author ahoo wang
 */
object DiscoveryKeyGenerator {
    private const val SERVICE_IDX = "svc_idx"
    private const val SERVICE_STAT = "svc_stat"
    private const val SERVICE_INSTANCE_IDX = "svc_itc_idx"
    private const val SERVICE_INSTANCE = "svc_itc"

    /**
     * [namespace]:[SERVICE_IDX] .
     */
    @JvmStatic
    fun getServiceIdxKey(namespace: String): String {
        return "$namespace:$SERVICE_IDX"
    }

    /**
     * [namespace]:[SERVICE_STAT] .
     */
    @JvmStatic
    fun getServiceStatKey(namespace: String): String {
        return "$namespace:$SERVICE_STAT"
    }

    @JvmStatic
    fun getNamespaceOfKey(key: String): String {
        val firstSplitIdx: Int = key.indexOf(CoSky.KEY_SEPARATOR)
        return key.substring(0, firstSplitIdx)
    }

    /**
     * [namespace]:[SERVICE_INSTANCE_IDX]:[serviceId] .
     */
    @JvmStatic
    fun getInstanceIdxKey(namespace: String, serviceId: String): String {
        return "$namespace:$SERVICE_INSTANCE_IDX:$serviceId"
    }

    /**
     * [namespace]:[SERVICE_INSTANCE]:[instanceId] .
     */
    @JvmStatic
    fun getInstanceKey(namespace: String, instanceId: String): String {
        return "$namespace:$SERVICE_INSTANCE:$instanceId"
    }

    /**
     * [namespace]:[SERVICE_INSTANCE]:* .
     */
    @JvmStatic
    fun getInstanceKeyPatternOfNamespace(namespace: String): String {
        return "$namespace:$SERVICE_INSTANCE:*"
    }

    /**
     * [namespace]:[SERVICE_INSTANCE]:[serviceId]@* .
     */
    @JvmStatic
    fun getInstanceKeyPatternOfService(namespace: String, serviceId: String): String {
        return "$namespace:$SERVICE_INSTANCE:$serviceId@*"
    }

    /**
     * [namespace]:[SERVICE_INSTANCE]: .
     */
    @JvmStatic
    fun getInstanceIdOfKey(namespace: String, instanceKey: String): String {
        val instanceKeyPrefix = "$namespace:$SERVICE_INSTANCE:"
        return instanceKey.substring(instanceKeyPrefix.length)
    }
}
