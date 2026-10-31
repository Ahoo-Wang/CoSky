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
package me.ahoo.cosky.api

import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.validation.Fixtures
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class ResourceKindsTest {

    @Test
    fun `kind follows the spec`() {
        Fixtures.all.map { it.kind }.assert().containsExactly(
            Kind.KIND_CONFIG,
            Kind.KIND_INSTANCE,
            Kind.KIND_NAMESPACE,
            Kind.KIND_SERVICE_ACCOUNT,
            Kind.KIND_ROLE,
            Kind.KIND_ROLE_BINDING,
        )
        Resource.getDefaultInstance().kind.assert().isEqualTo(Kind.KIND_UNSPECIFIED)
    }

    @Test
    fun `global kinds`() {
        Kind.entries.filter { it.isGlobal }.assert()
            .containsExactly(Kind.KIND_NAMESPACE, Kind.KIND_SERVICE_ACCOUNT, Kind.KIND_ROLE, Kind.KIND_ROLE_BINDING)
    }
}
