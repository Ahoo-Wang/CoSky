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

package me.ahoo.cosky.migration

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import me.ahoo.cosky.migration.MigrationEnvironment.REDIS
import me.ahoo.cosky.migration.MigrationEnvironment.REDIS_URI
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

/**
 * A target that answers with success, but not as CoSky does, such as a gateway that shows its sign-in page: nothing
 * else will work either, so the run stops, says what came back, and still writes its report.
 */
class UnusableTargetTest {
    @TempDir
    lateinit var directory: Path

    private val six = SixRedis(REDIS)
    private val output = ByteArrayOutputStream()
    private val gateway = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)

    private val report: String
        get() = Files.readString(directory.resolve("report.md"))

    @BeforeEach
    fun start() {
        REDIS.flushall()
        six.listNamespace(SOURCE)
        gateway.start()
    }

    @AfterEach
    fun stop() {
        gateway.stop(0)
    }

    private fun answer(respond: (HttpExchange) -> Answer) {
        gateway.createContext("/") { exchange ->
            val answer = respond(exchange)
            val body = answer.body.encodeToByteArray()
            answer.type?.let { exchange.responseHeaders.add("Content-Type", it) }
            exchange.sendResponseHeaders(answer.status, if (body.isEmpty()) -1 else body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    private fun run(vararg arguments: String): Int = migrate(
        listOf(
            "--source",
            REDIS_URI,
            "--target",
            "http://localhost:${gateway.address.port}",
            "--report",
            directory.resolve("report.md").toString(),
        ) + arguments,
        emptyMap(),
        PrintStream(output),
    )

    @Test
    fun `a sign-in page in place of a Config stops the run, and the report says what came back`() {
        six.setConfig(SOURCE, "app.txt", "1")
        answer { Answer(OK, SIGN_IN_PAGE, "text/html; charset=utf-8") }

        run().assert().isEqualTo(EXIT_UNABLE)

        report.assert().contains("The run stopped early")
            .contains("answered GET /v1/namespaces/$NAMESPACE/configs/app.txt with HTTP 200, but not as CoSky does")
            .contains("text/html; charset=utf-8, ${SIGN_IN_PAGE.length} characters beginning")
            .contains("`<!DOCTYPE html> <html><head><title>Sign in</title></head><body>")
            .doesNotContain("END-OF-PAGE")
        output.toString().assert().contains("Stopped early").doesNotContain("Cannot write the report")
    }

    @Test
    fun `an empty answer to a write stops the run`() {
        six.setConfig(SOURCE, "app.txt", "1")
        answer { exchange -> if (exchange.requestMethod == "GET") Answer(NOT_FOUND) else Answer(OK) }

        run().assert().isEqualTo(EXIT_UNABLE)

        report.assert().contains("answered PUT /v1/namespaces/$NAMESPACE/configs/app.txt with HTTP 200")
            .contains("an empty body, no Content-Type")
    }

    @Test
    fun `a sign-in page in place of a history stops the run`() {
        listOf("A", "B", "A").forEach { six.setConfig(SOURCE, "app.txt", it) }
        answer { exchange ->
            if (exchange.requestURI.path.endsWith("/history")) {
                Answer(OK, SIGN_IN_PAGE, "text/html")
            } else {
                Answer(OK, """{"config":{"format":"CONFIG_FORMAT_TEXT","content":"A"}}""", "application/json")
            }
        }

        run("--history").assert().isEqualTo(EXIT_UNABLE)

        report.assert().contains("answered GET /v1/namespaces/$NAMESPACE/configs/app.txt/history with HTTP 200")
    }

    private class Answer(val status: Int, val body: String = "", val type: String? = null)

    private companion object {
        const val OK = 200
        const val NOT_FOUND = 404
        const val NAMESPACE = "orders"
        const val SOURCE = "cosky-{$NAMESPACE}"
        val SIGN_IN_PAGE = "<!DOCTYPE html>\n<html><head><title>Sign in</title></head><body>\n" +
            "<form>".repeat(1000) + "END-OF-PAGE</body></html>"
    }
}
