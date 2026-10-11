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

import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

class ServiceInstanceCodecTest {
    @Test
    fun encodeMetadataKey() {
        ServiceInstanceCodec.encodeMetadataKey("version").assert().isEqualTo("_version")
        assertThrownBy<IllegalArgumentException> {
            ServiceInstanceCodec.encodeMetadataKey("_reserved")
        }.hasMessage("metadata key:[_reserved] must not start with reserved prefix [_].")
    }

    private val fixedInstance = listOf(
        "instanceId", "svc@http#127.0.0.1#8080",
        "serviceId", "svc",
        "schema", "http",
        "host", "127.0.0.1",
        "port", "8080",
        "weight", "2",
        "ephemeral", "false",
        "_zone", "a",
        "__last_renew_pub_ttl_at", "1",
    )

    @Test
    fun decodeKeepsUserMetadataOnly() {
        val instance = ServiceInstanceCodec.decode(fixedInstance)
        instance.instanceId.assert().isEqualTo("svc@http#127.0.0.1#8080")
        instance.port.assert().isEqualTo(8080)
        instance.weight.assert().isEqualTo(2)
        instance.isEphemeral.assert().isFalse()
        instance.ttlAt.assert().isEqualTo(ServiceInstance.TTL_AT_FOREVER)
        instance.metadata.assert().isEqualTo(mapOf("zone" to "a"))
    }

    @Test
    fun decodeEphemeralInstanceWithTtlAt() {
        val data = fixedInstance.toMutableList().apply {
            this[indexOf("ephemeral") + 1] = "true"
            addAll(listOf("ttl_at", "1700000000"))
        }
        ServiceInstanceCodec.decode(data).ttlAt.assert().isEqualTo(1700000000L)
    }

    @Test
    fun decodeRejectsMissingRequiredFields() {
        val required = mapOf(
            "serviceId" to "serviceId",
            "schema" to "schema",
            "host" to "host",
            "port" to "port",
            "instanceId" to "instanceId",
            "weight" to "weight",
            "ephemeral" to "isEphemeral",
        )
        required.forEach { (field, name) ->
            val index = fixedInstance.indexOf(field)
            val data = fixedInstance.filterIndexed { i, _ -> i != index && i != index + 1 }
            assertThrownBy<IllegalArgumentException> { ServiceInstanceCodec.decode(data) }
                .hasMessage("$name is null")
        }
        val ephemeralWithoutTtlAt = fixedInstance.toMutableList().apply { this[indexOf("ephemeral") + 1] = "true" }
        assertThrownBy<IllegalArgumentException> { ServiceInstanceCodec.decode(ephemeralWithoutTtlAt) }
            .hasMessage("ttlAt is null")
    }
}
