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

import io.grpc.Status
import me.ahoo.cosky.spring.registry.RegistrationRefusedException
import me.ahoo.cosky.spring.registry.RegistrationRefusedFailureAnalyzer
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationContextException

class RegistrationRefusedTest {
    private fun analysis(status: Status) = RegistrationRefusedFailureAnalyzer().analyze(
        ApplicationContextException(
            "Failed to start bean 'webServerStartStop'",
            RegistrationRefusedException("order", "prod", status.asException()),
        ),
    )!!

    @Test
    fun `without a token, it says to set one`() {
        val analysis = analysis(Status.UNAUTHENTICATED.withDescription("A valid token is required."))

        analysis.description.assert().contains("order").contains("prod").contains("A valid token is required.")
        analysis.action.assert().contains("COSKY_TOKEN").contains("client")
    }

    @Test
    fun `without the right role, it says which to bind`() {
        val analysis = analysis(Status.PERMISSION_DENIED.withDescription("Not allowed."))

        analysis.action.assert().contains("Bind").contains("order").contains("prod").doesNotContain("COSKY_TOKEN")
    }
}
