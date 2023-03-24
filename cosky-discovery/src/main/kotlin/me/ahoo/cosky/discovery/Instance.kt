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

import me.ahoo.cosky.discovery.Instance.Companion.asUri
import me.ahoo.cosky.discovery.Instance.Companion.isSecure
import java.net.URI

/**
 * Instance.
 *
 * @author ahoo wang
 */
interface Instance {
    val instanceId: String
    val serviceId: String
    val schema: String
    val host: String
    val port: Int
    val uri: URI
    val isSecure: Boolean

    companion object {
        private val secureSchemas: Set<String> = setOf("https", "wss")
        private const val SERVICE_ID_DELIMITER = "@"
        private const val HOST_DELIMITER = "#"

        /**
         * Creates a URI from the given Instance's host:port.
         *
         * @return URI of the form [schema]://[host]:[port]".
         */
        fun Instance.asUri(): URI {
            return asUri(schema, host, port)
        }

        fun asUri(schema: String, host: String, port: Int): URI {
            return URI.create("$schema://$host:$port")
        }

        fun isSecure(schema: String): Boolean = secureSchemas.contains(schema)

        /**
         * [Instance.serviceId]@[Instance.schema]#[Instance.host]#[Instance.port]}
         * order_service@http#127.0.0.1#8088
         */
        fun asInstanceId(serviceId: String, schema: String, host: String, port: Int): String {
            return "$serviceId$SERVICE_ID_DELIMITER$schema$HOST_DELIMITER$host$HOST_DELIMITER$port"
        }

        fun asInstance(serviceId: String, schema: String, host: String, port: Int): Instance {
            return asInstance(serviceId, schema, host, port, asInstanceId(serviceId, schema, host, port))
        }

        fun asInstance(serviceId: String, schema: String, host: String, port: Int, instanceId: String): Instance {
            return InstanceData(
                serviceId = serviceId,
                schema = schema,
                host = host,
                port = port,
                instanceId = instanceId,
            )
        }

        fun String.asInstance(): Instance {
            val instanceId = this
            val serviceSpits = instanceId
                .split(SERVICE_ID_DELIMITER)
                .dropWhile { it.isEmpty() }
                .toTypedArray()
            require(serviceSpits.size == 2) { "Invalid instanceId: $instanceId" }
            val serviceId = serviceSpits[0]
            val instanceSpits =
                serviceSpits[1]
                    .split(HOST_DELIMITER)
                    .dropWhile { it.isEmpty() }
                    .toTypedArray()
            require(instanceSpits.size == 3) { "Invalid instanceId: $instanceId" }
            val schema = instanceSpits[0]
            val host = instanceSpits[1]
            val port = instanceSpits[2].toInt()
            return asInstance(serviceId, schema, host, port)
        }
    }
}

private data class InstanceData(
    override val serviceId: String,
    override val schema: String,
    override val host: String,
    override val port: Int,
    override val instanceId: String,
) : Instance {
    override val uri: URI by lazy { asUri() }
    override val isSecure: Boolean by lazy { isSecure(schema) }
}
