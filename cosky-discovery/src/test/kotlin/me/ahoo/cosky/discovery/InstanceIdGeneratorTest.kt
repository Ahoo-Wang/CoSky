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

import me.ahoo.cosky.discovery.Instance.Companion.asInstance
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

/**
 * @author ahoo wang
 */
class InstanceIdGeneratorTest {

    @Test
    fun asInstanceId() {
        Instance.asInstanceId("order_service", "http", "127.0.0.1", 8080)
            .assert().isEqualTo("order_service@http#127.0.0.1#8080")
    }

    @Test
    fun asInstance() {
        val actual = "order_service@http#127.0.0.1#8080".asInstance()
        actual.serviceId.assert().isEqualTo("order_service")
        actual.schema.assert().isEqualTo("http")
        actual.host.assert().isEqualTo("127.0.0.1")
        actual.port.assert().isEqualTo(8080)
    }
}
