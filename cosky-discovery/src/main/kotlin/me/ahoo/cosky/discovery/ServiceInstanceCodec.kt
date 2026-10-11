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
import me.ahoo.cosky.discovery.ServiceInstance.Companion.asServiceInstance

/**
 * Service Instance Codec.
 *
 * @author ahoo wang
 */
object ServiceInstanceCodec {
    private const val SYSTEM_METADATA_PREFIX = "__"
    private const val METADATA_PREFIX = "_"
    private const val METADATA_PREFIX_LENGTH = METADATA_PREFIX.length
    private const val INSTANCE_ID = "instanceId"
    private const val SERVICE_ID = "serviceId"
    private const val SCHEMA = "schema"
    private const val HOST = "host"
    private const val PORT = "port"
    private const val WEIGHT = "weight"
    private const val EPHEMERAL = "ephemeral"
    private const val TTL_AT = "ttl_at"

    fun encodeMetadataKey(key: String): String {
        require(!key.startsWith(METADATA_PREFIX)) {
            "metadata key:[$key] must not start with reserved prefix [$METADATA_PREFIX]."
        }
        return METADATA_PREFIX + key
    }

    fun decodeMetadataKey(key: String): String {
        return key.substring(METADATA_PREFIX_LENGTH)
    }

    @JvmStatic
    fun encodeMetadata(preArgs: MutableList<String>, instanceMetadata: Map<String, String>): MutableList<String> {
        if (instanceMetadata.isEmpty()) {
            return preArgs
        }
        instanceMetadata.forEach {
            preArgs.add(encodeMetadataKey(it.key))
            preArgs.add(it.value)
        }
        return preArgs
    }

    fun decode(instanceData: List<String>): ServiceInstance {
        val fields = HashMap<String, String>(instanceData.size)
        val metadata = mutableMapOf<String, String>()
        for (i in instanceData.indices step 2) {
            val key = instanceData[i]
            val value = instanceData[i + 1]
            if (!key.startsWith(METADATA_PREFIX)) {
                fields[key] = value
            } else if (!key.startsWith(SYSTEM_METADATA_PREFIX)) {
                metadata[decodeMetadataKey(key)] = value
            }
        }
        val serviceId = requireNotNull(fields[SERVICE_ID]) { "serviceId is null" }
        val schema = requireNotNull(fields[SCHEMA]) { "schema is null" }
        val host = requireNotNull(fields[HOST]) { "host is null" }
        val port = requireNotNull(fields[PORT]) { "port is null" }.toInt()
        val instanceId = requireNotNull(fields[INSTANCE_ID]) { "instanceId is null" }

        val instance = Instance.asInstance(
            serviceId = serviceId,
            schema = schema,
            host = host,
            port = port,
            instanceId = instanceId,
        )
        val weight = requireNotNull(fields[WEIGHT]) { "weight is null" }.toInt()
        val isEphemeral = requireNotNull(fields[EPHEMERAL]) { "isEphemeral is null" }.toBoolean()
        val ttlAt = fields[TTL_AT]?.toLong()
        if (isEphemeral) {
            requireNotNull(ttlAt) { "ttlAt is null" }
        }
        return instance.asServiceInstance(
            weight = weight,
            isEphemeral = isEphemeral,
            ttlAt = ttlAt ?: TTL_AT_FOREVER,
            metadata = metadata,
        )
    }
}
