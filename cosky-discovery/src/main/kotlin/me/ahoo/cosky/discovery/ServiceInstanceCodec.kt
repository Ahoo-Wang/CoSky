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

    @Deprecated("")
    fun encode(serviceInstance: ServiceInstance): Map<String, String> {
        return buildMap {
            this[INSTANCE_ID] = serviceInstance.instanceId
            this[SERVICE_ID] = serviceInstance.serviceId
            this[SCHEMA] = serviceInstance.schema
            this[HOST] = serviceInstance.host
            this[PORT] = serviceInstance.port.toString()
            this[WEIGHT] = serviceInstance.weight.toString()
            this[EPHEMERAL] = serviceInstance.isEphemeral.toString()
            serviceInstance.metadata.forEach { (key: String, value: String) ->
                val metadataKey = METADATA_PREFIX + key
                this[metadataKey] = value
            }
        }
    }

    @JvmStatic
    fun encodeMetadata(preArgs: MutableList<String>, instanceMetadata: Map<String, String>): MutableList<String> {
        if (instanceMetadata.isEmpty()) {
            return preArgs
        }
        instanceMetadata.forEach {
            preArgs.add(METADATA_PREFIX + it.key)
            preArgs.add(it.value)
        }
        return preArgs
    }

    fun decode(instanceData: List<String>): ServiceInstance {
        var instanceId: String? = null
        var serviceId: String? = null
        var schema: String? = null
        var host: String? = null
        var port: Int? = null
        var weight: Int? = null
        var isEphemeral: Boolean? = null
        var ttlAt: Long? = null
        val metadata = mutableMapOf<String, String>()
        var i = 0
        while (i < instanceData.size) {
            val key = instanceData[i]
            val value = instanceData[i + 1]
            when (key) {
                INSTANCE_ID -> {
                    instanceId = value
                }

                SERVICE_ID -> {
                    serviceId = value
                }

                SCHEMA -> {
                    schema = value
                }

                HOST -> {
                    host = value
                }

                PORT -> {
                    port = value.toInt()
                }

                WEIGHT -> {
                    weight = value.toInt()
                }

                EPHEMERAL -> {
                    isEphemeral = value.toBoolean()
                }

                TTL_AT -> {
                    ttlAt = value.toLong()
                }

                else -> {
                    if (key.startsWith(METADATA_PREFIX) &&
                        !key.startsWith(SYSTEM_METADATA_PREFIX)
                    ) {
                        val metadataKey = key.substring(METADATA_PREFIX_LENGTH)
                        metadata[metadataKey] = value
                    }
                }
            }
            i += 2
        }
        requireNotNull(serviceId) { "serviceId is null" }
        requireNotNull(schema) { "schema is null" }
        requireNotNull(host) { "host is null" }
        requireNotNull(port) { "port is null" }
        requireNotNull(instanceId) { "instanceId is null" }

        val instance = Instance.asInstance(
            serviceId = serviceId,
            schema = schema,
            host = host,
            port = port,
            instanceId = instanceId
        )
        requireNotNull(weight) { "weight is null" }
        requireNotNull(isEphemeral) { "isEphemeral is null" }
        requireNotNull(ttlAt) { "ttlAt is null" }
        return ServiceInstance(
            delegate = instance,
            weight = weight,
            isEphemeral = isEphemeral,
            ttlAt = ttlAt,
            metadata = metadata
        )
    }
}
