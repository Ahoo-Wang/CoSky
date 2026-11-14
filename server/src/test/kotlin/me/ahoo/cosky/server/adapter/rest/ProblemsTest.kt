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

package me.ahoo.cosky.server.adapter.rest

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

class ProblemsTest {
    @Test
    fun `a failure is problem details, with the reason and its member beside them`() {
        val key = ResourceKey.newBuilder().setNamespace("prod").setKind(Kind.KIND_CONFIG).setName("app.yaml").build()
        val error = CoSkyException.revisionConflict(key, expectedRevision = 42, currentRevision = 43)

        val problem = Problems.of(error, "/v1/namespaces/prod/configs/app.yaml")

        problem["type"].asString.assert().isEqualTo("https://cosky.ahoo.me/guide/rest-api#revision-conflict")
        problem["title"].asString.assert().isEqualTo("Revision conflict")
        problem["status"].asInt.assert().isEqualTo(409)
        problem["detail"].asString.assert().isEqualTo(error.message)
        problem["instance"].asString.assert().isEqualTo("/v1/namespaces/prod/configs/app.yaml")
        problem["code"].asString.assert().isEqualTo("ABORTED")
        problem["reason"].asString.assert().isEqualTo("ERROR_REASON_REVISION_CONFLICT")
        val conflict = problem.getAsJsonObject("revisionConflict")
        conflict["expectedRevision"].asString.assert().isEqualTo("42")
        conflict["currentRevision"].asString.assert().isEqualTo("43")
    }

    @Test
    fun `every reason has a type and a title of its own`() {
        val reasons = ErrorReason.entries.filter {
            it != ErrorReason.ERROR_REASON_UNSPECIFIED && it != ErrorReason.UNRECOGNIZED
        }

        reasons.map(Problems::typeOf).toSet().size.assert().isEqualTo(reasons.size)
        reasons.map(Problems::titleOf).toSet().size.assert().isEqualTo(reasons.size)
        Problems.titleOf(ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT).assert().isEqualTo("Min revision timeout")
    }
}
