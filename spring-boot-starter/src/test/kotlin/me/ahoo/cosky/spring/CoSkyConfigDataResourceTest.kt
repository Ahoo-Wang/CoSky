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

package me.ahoo.cosky.spring

import me.ahoo.cosky.spring.config.CoSkyConfigDataResource
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

/**
 * Spring loads a resource once in a run of the imports, however many imports name it, by its equality.
 */
class CoSkyConfigDataResourceTest {
    private fun resource(config: String = "a.yaml", namespace: String = "prod", optional: Boolean = false) =
        CoSkyConfigDataResource(config, CoSkyProperties().apply { this.namespace = namespace }, "order", optional)

    @Test
    fun `two imports of one Config, of one namespace, named the same way, are one resource`() {
        resource().assert().isEqualTo(resource()).hasSameHashCodeAs(resource())
        listOf(resource(config = "b.yaml"), resource(namespace = "test"), resource(optional = true)).forEach {
            it.assert().isNotEqualTo(resource())
        }
        resource().assert().isNotEqualTo("cosky:a.yaml")
        resource().toString().assert().isEqualTo("cosky:a.yaml")
    }
}
