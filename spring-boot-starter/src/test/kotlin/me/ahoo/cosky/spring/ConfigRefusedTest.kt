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

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.spring.config.CoSkyConfigDataResource
import me.ahoo.cosky.spring.config.ConfigRefusedFailureAnalyzer
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.config.ConfigDataResource
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

/**
 * A required Config the server refuses stops the application at start, which then says why and what to set, rather
 * than that the Config does not exist.
 */
@ExtendWith(OutputCaptureExtension::class)
class ConfigRefusedTest {
    private val server = TestCoSkyServer()

    @EnableAutoConfiguration
    class Application

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun resource(): CoSkyConfigDataResource =
        CoSkyConfigDataResource("order.yaml", CoSkyProperties().apply { namespace = "prod" }, "order")

    private fun analysis(refusal: CoSkyException?, resource: ConfigDataResource = resource()) =
        ConfigRefusedFailureAnalyzer().analyze(
            IllegalStateException(ConfigDataResourceNotFoundException(resource, refusal)),
        )

    @Test
    fun `what spring reports says why the server refused, and what to set`(output: CapturedOutput) {
        val failure = runCatching {
            SpringApplicationBuilder(Application::class.java).run(
                "--spring.application.name=order",
                "--spring.grpc.server.enabled=false",
                "--spring.main.web-application-type=none",
                "--cosky.endpoints=${server.endpoint}",
                "--cosky.namespace=nowhere",
                "--cosky.snapshot.enabled=false",
                "--cosky.config.load-timeout=3s",
                "--spring.config.import=cosky:order.yaml",
            )
        }.exceptionOrNull()

        failure.assert().isInstanceOf(ConfigDataResourceNotFoundException::class.java)
        // Spring's own analysis would say that the Config does not exist, and to prefix it with optional:.
        output.toString().assert()
            .contains("CoSky refused to load Config order.yaml of namespace nowhere: NOT_FOUND")
            .contains("Create namespace nowhere, or set cosky.namespace")
            .doesNotContain("does not exist")
    }

    @Test
    fun `without a token, it says to set one`() {
        val analysis = checkNotNull(analysis(CoSkyException.unauthenticated("A valid token is required.")))

        analysis.description.assert().isEqualTo(
            "CoSky refused to load Config order.yaml of namespace prod: UNAUTHENTICATED: A valid token is required.",
        )
        analysis.action.assert().contains("COSKY_TOKEN").contains("prod").contains("client")
    }

    @Test
    fun `without the right role, it says which to bind`() {
        val analysis = checkNotNull(analysis(CoSkyException.permissionDenied("Not allowed.")))

        analysis.description.assert().contains("PERMISSION_DENIED: Not allowed.")
        analysis.action.assert().contains("Bind").contains("order.yaml").contains("prod").doesNotContain("COSKY_TOKEN")
    }

    @Test
    fun `another refusal says to check the location`() {
        val analysis = checkNotNull(analysis(CoSkyException.storeUnavailable("The store is unavailable.")))

        analysis.action.assert().contains("'cosky:order.yaml'").contains("optional:")
    }

    @Test
    fun `a config that is not there, or not cosky's, is left to spring`() {
        analysis(null).assert().isNull()
        val other = object : ConfigDataResource() {}
        analysis(CoSkyException.unauthenticated("A valid token is required."), other).assert().isNull()
    }
}
