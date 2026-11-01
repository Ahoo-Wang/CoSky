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

import com.google.gson.JsonParser
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.sync.RedisCommands
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.server.CoSkyServerApplication
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.builder.SpringApplicationBuilder
import org.testcontainers.containers.GenericContainer
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * The whole tool, from its command line, between a real Redis and a real server.
 */
class MigrationTest {
    @TempDir
    lateinit var directory: Path

    private val six = SixRedis(REDIS)
    private val target = RestTarget(URL, TOKEN)
    private val namespace = "ns-${UUID.randomUUID().toString().take(8)}"
    private val source = "cosky-{$namespace}"
    private val output = ByteArrayOutputStream()

    private val report: String
        get() = Files.readString(directory.resolve("report.md"))

    @BeforeEach
    fun start() {
        REDIS.flushall()
        six.listNamespace(source)
    }

    private fun run(
        vararg arguments: String,
        token: String? = TOKEN,
        url: String = URL,
        redis: String = REDIS_URI,
    ): Int {
        val required = listOf("--source", redis, "--target", url, "--report", directory.resolve("report.md").toString())
        return migrate(
            required + arguments,
            token?.let { mapOf(Options.TOKEN_VARIABLE to it) }.orEmpty(),
            PrintStream(output),
        )
    }

    private fun contentOf(name: String): String? = target.config(namespace, name)?.config?.content

    private fun historyOf(name: String): List<String> {
        val request = HttpRequest.newBuilder(URI.create("$URL/v1/namespaces/$namespace/configs/$name/history"))
            .header("Authorization", "Bearer $TOKEN")
            .build()
        val body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
        return JsonParser.parseString(body).asJsonObject["revisions"].asJsonArray
            .map { it.asJsonObject["config"].asJsonObject["content"].asString }
    }

    private fun text(content: String): Resource = resource {
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_TEXT
            this.content = content
        }
    }

    @Test
    fun `imports the current version of every Config, and a removed one not at all`() {
        six.setConfig(source, "application.yaml", "a: 1")
        six.setConfig(source, "application.yaml", "a: 2")
        six.setConfig(source, "dir/app.properties", "b=1")
        six.setConfig(source, "gone.txt", "x")
        six.removeConfig(source, "gone.txt")

        run().assert().isEqualTo(EXIT_DONE)

        val yaml = checkNotNull(target.config(namespace, "application.yaml"))
        yaml.config.content.assert().isEqualTo("a: 2")
        yaml.config.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_YAML)
        historyOf("application.yaml").assert().containsExactly("a: 2")
        val properties = checkNotNull(target.config(namespace, "dir/app.properties"))
        properties.config.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_PROPERTIES)
        target.config(namespace, "gone.txt").assert().isNull()
    }

    @Test
    fun `a name without an extension is read as 6x read it`() {
        six.setConfig(source, "order-service", "a: 1")
        six.setConfig(source, "dir/legacy.xml", "<a/>")

        run().assert().isEqualTo(EXIT_DONE)

        target.config(namespace, "order-service")?.config?.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_YAML)
        target.config(namespace, "dir/legacy.xml")?.config?.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_TEXT)
    }

    @Test
    fun `the format of a name without an extension can be chosen`() {
        six.setConfig(source, "order-service", "a=1")

        run("--default-format", "properties").assert().isEqualTo(EXIT_DONE)

        target.config(namespace, "order-service")?.config?.format
            .assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_PROPERTIES)
    }

    @Test
    fun `the history arrives oldest first, without a content repeated`() {
        six.setConfig(source, "app.txt", "1")
        six.setConfig(source, "app.txt", "2")
        six.removeConfig(source, "app.txt")
        six.setConfig(source, "app.txt", "2")
        six.setConfig(source, "app.txt", "3")

        run("--history").assert().isEqualTo(EXIT_DONE)

        historyOf("app.txt").assert().containsExactly("3", "2", "1")
    }

    @Test
    fun `a namespace without Configs still arrives, for its applications to register in`() {
        six.register(source, "order", "i-1")

        run().assert().isEqualTo(EXIT_DONE)

        target.namespaceExists(namespace).assert().isTrue()
    }

    @Test
    fun `running it again changes nothing`() {
        six.setConfig(source, "app.txt", "1")
        run("--history").assert().isEqualTo(EXIT_DONE)
        val first = checkNotNull(target.config(namespace, "app.txt")).meta.revision

        run("--history").assert().isEqualTo(EXIT_DONE)

        checkNotNull(target.config(namespace, "app.txt")).meta.revision.assert().isEqualTo(first)
        report.assert().contains("| 0 | 0 | 1 | 0 |")
    }

    @Test
    fun `an interrupted run continues where it stopped`() {
        listOf("1", "2", "3").forEach { six.setConfig(source, "app.txt", it) }
        target.createNamespace(namespace, "")
        target.putConfig(namespace, "app.txt", text("1"), null)
        val first = checkNotNull(target.config(namespace, "app.txt")).meta.revision
        target.putConfig(namespace, "app.txt", text("2"), first)

        run("--history").assert().isEqualTo(EXIT_DONE)

        historyOf("app.txt").assert().containsExactly("3", "2", "1")
    }

    @Test
    fun `a change made in 6x before the switch still arrives`() {
        six.setConfig(source, "app.txt", "1")
        run().assert().isEqualTo(EXIT_DONE)
        six.setConfig(source, "app.txt", "2")

        run().assert().isEqualTo(EXIT_DONE)

        contentOf("app.txt").assert().isEqualTo("2")
    }

    @Test
    fun `a Config edited on 8_0 is left alone, and reported`() {
        six.setConfig(source, "app.txt", "1")
        run().assert().isEqualTo(EXIT_DONE)
        val edited = checkNotNull(target.config(namespace, "app.txt")).meta.revision
        target.putConfig(namespace, "app.txt", text("edited"), edited)

        run().assert().isEqualTo(EXIT_ATTENTION)

        contentOf("app.txt").assert().isEqualTo("edited")
        report.assert().contains("`app.txt` | CONFLICT")
    }

    @Test
    fun `what 8_0 does not accept is reported, and the rest still arrives`() {
        val tooLarge = "x".repeat(ONE_MIB + 1)
        six.setConfig(source, "bad:name", "1")
        six.setConfig(source, "large.txt", tooLarge)
        six.setConfig(source, "fine.txt", tooLarge)
        six.setConfig(source, "fine.txt", "1")

        run("--history").assert().isEqualTo(EXIT_ATTENTION)

        contentOf("fine.txt").assert().isEqualTo("1")
        target.config(namespace, "large.txt").assert().isNull()
        report.assert().contains("`bad:name` | INVALID").contains("`large.txt` | INVALID")
            .contains("`fine.txt` | 1 old versions 8.0 refuses were left out")
    }

    @Test
    fun `a dry run says what would happen, and writes nothing`() {
        six.setConfig(source, "app.txt", "1")

        run("--dry-run").assert().isEqualTo(EXIT_DONE)

        target.namespaceExists(namespace).assert().isFalse()
        report.assert().contains("Dry run").contains("| 1 | 0 | 0 | 0 |")
    }

    @Test
    fun `a namespace 8_0 cannot name stops the run before anything is written`() {
        six.setConfig(source, "app.txt", "1")
        six.listNamespace("Not_Legal")

        run().assert().isEqualTo(EXIT_UNABLE)

        target.namespaceExists(namespace).assert().isFalse()
        output.toString().assert().contains("Not_Legal")
        run("--namespace", "Not_Legal=").assert().isEqualTo(EXIT_DONE)
        target.namespaceExists(namespace).assert().isTrue()
        report.assert().contains("`Not_Legal` | left out, 0 Configs")
    }

    @Test
    fun `two namespaces cannot land on one`() {
        six.listNamespace("{$namespace}")

        run().assert().isEqualTo(EXIT_UNABLE)

        output.toString().assert().contains("would land on one")
    }

    @Test
    fun `the default namespace of 6x arrives even if nobody listed it`() {
        six.setConfig(NamespaceMapping.DEFAULT, "$namespace.txt", "1")

        run().assert().isEqualTo(EXIT_DONE)

        target.config("default", "$namespace.txt")?.config?.content.assert().isEqualTo("1")
    }

    @Test
    fun `a namespace 6x never listed can be named, and renamed`() {
        six.setConfig("unlisted", "app.txt", "1")

        run("--namespace", "unlisted=$namespace", "--namespace", "$source=").assert().isEqualTo(EXIT_DONE)

        contentOf("app.txt").assert().isEqualTo("1")
    }

    @Test
    fun `the report names what to rebuild by hand, and holds no secret`() {
        six.setConfig(source, "app.txt", "secret-content")
        six.register(source, "order", "i-1")
        six.register(source, "database", "db-1", ephemeral = false, metadata = mapOf("zone" to "secret-metadata"))
        six.register(source, "Not_Legal", "i-2")
        six.saveRole("developer", "Writes the Configs", mapOf(source to "rw", "gone" to "r", "*" to "w"))
        six.saveRole("reader", "", mapOf(source to "r"))
        six.saveRole("Not_Legal", "", emptyMap())
        six.addUser("ada", "secret-hash", "developer", "removed")
        six.addUser("root", "secret-hash")

        six.listNamespace(NamespaceMapping.SYSTEM)

        run().assert().isEqualTo(EXIT_DONE)

        target.namespaceExists("system").assert().isFalse()
        report.assert()
            .contains("| `cosky-{system}` | left out, 0 Configs |")
            .contains("| `$namespace` | 3 | 1 |")
            .contains("| `$namespace` | `database` | `db-1` | `http://10.0.0.1:8080` | 2 | zone |")
            .contains("| `$source` | `Not_Legal` |")
            .contains("`$source`: rw | namespaces `$namespace`, verbs GET, WATCH, PUT, DELETE")
            .contains("`$source`: r | namespaces `$namespace`, verbs GET, WATCH")
            .contains("`*`: w | namespaces `*`, verbs PUT, DELETE")
            .contains("`gone`: r |  | This namespace did not migrate.")
            .contains("Needs a new name")
            .contains("| `ada` | `developer`, `removed` | Granted nothing by removed: no such role. |")
            .contains("| `root` |  | No roles.")
            .doesNotContain("secret")
    }

    @Test
    fun `a token the server refuses stops the run, and says why`() {
        six.setConfig(source, "app.txt", "1")

        run(token = "not-a-token").assert().isEqualTo(EXIT_UNABLE)
        report.assert().contains("stopped early").contains("refused this token")

        run(token = null).assert().isEqualTo(EXIT_UNABLE)
        contentOf("app.txt").assert().isNull()
    }

    @Test
    fun `a write that lost a race says so, rather than replacing what it did not see`() {
        target.createNamespace(namespace, "")
        target.createNamespace(namespace, "created twice is just as good")
        val stale = target.putConfig(namespace, "app.txt", text("1"), null)
        target.putConfig(namespace, "app.txt", text("2"), stale)

        assertThrows<RevisionConflictException> { target.putConfig(namespace, "app.txt", text("3"), null) }
        assertThrows<RevisionConflictException> { target.putConfig(namespace, "app.txt", text("3"), stale) }

        contentOf("app.txt").assert().isEqualTo("2")
    }

    @Test
    fun `what the server refuses is told apart from a server that cannot go on`() {
        assertThrows<TargetRejectedException> { target.putConfig("no-such-namespace", "app.txt", text("1"), null) }
        assertThrows<TargetStoppedException> { target.createNamespace("Not_Legal", "") }
    }

    @Test
    fun `a server that cannot be reached stops the run`() {
        val closed = ServerSocket(0).use { it.localPort }

        run(url = "http://localhost:$closed").assert().isEqualTo(EXIT_UNABLE)

        report.assert().contains("Cannot reach")
    }

    @Test
    fun `a Redis that cannot be reached says so`() {
        val closed = ServerSocket(0).use { it.localPort }

        run(redis = "redis://localhost:$closed").assert().isEqualTo(EXIT_UNABLE)

        output.toString().assert().contains("Cannot read the 6.x Redis")
    }

    @Test
    fun `the command line says what is wrong`() {
        migrate(listOf("--help"), emptyMap(), PrintStream(output)).assert().isEqualTo(EXIT_DONE)
        output.toString().assert().contains("Usage:")
        listOf(
            listOf("--target", URL) to "--source",
            listOf("--source", REDIS_URI) to "--target is required",
            listOf("--unknown") to "Unknown argument",
            listOf("--source") to "needs a value",
            listOf("--source", REDIS_URI, "--target", URL, "--namespace", "no-separator") to "--namespace looks like",
            listOf("--source", REDIS_URI, "--target", "localhost:8080") to "must be an http",
            listOf("--source", "not-a-uri", "--target", URL) to "not a Redis URI",
        ).forEach { (arguments, message) ->
            output.reset()
            migrate(arguments, emptyMap(), PrintStream(output)).assert().isEqualTo(EXIT_UNABLE)
            output.toString().assert().contains(message)
        }
    }

    @Test
    fun `the source can come from the environment, where ps cannot see a password`() {
        val environment = mapOf(Options.SOURCE_VARIABLE to REDIS_URI)
        val arguments = listOf("--target", URL, "--dry-run", "--report", directory.resolve("r.md").toString())

        migrate(arguments, environment + (Options.TOKEN_VARIABLE to TOKEN), PrintStream(output))
            .assert().isEqualTo(EXIT_DONE)
    }

    private class Redis : GenericContainer<Redis>("redis:7.4-alpine")

    private companion object {
        const val REDIS_PORT = 6379
        const val ONE_MIB = 1024 * 1024
        const val READY_ATTEMPTS = 100
        const val READY_DELAY = 100L
        val TOKEN = Tokens.generate().plaintext

        val CONTAINER: Redis = Redis().withExposedPorts(REDIS_PORT).also { it.start() }
        val REDIS_URI = "redis://${CONTAINER.host}:${CONTAINER.getMappedPort(REDIS_PORT)}"
        val REDIS: RedisCommands<String, String> = RedisClient.create(RedisURI.create(REDIS_URI)).connect().sync()

        val URL: String = startServer()

        private fun freePort() = ServerSocket(0).use { it.localPort }

        private fun startServer(): String {
            val port = freePort()
            SpringApplicationBuilder(CoSkyServerApplication::class.java).run(
                "--server.port=$port",
                "--spring.grpc.server.port=${freePort()}",
                "--cosky.store.type=memory",
                "--cosky.auth.enabled=true",
                "--cosky.auth.bootstrap-token=$TOKEN",
            )
            val url = "http://localhost:$port"
            // A server just started refuses requests until its access rules have loaded.
            val target = RestTarget(url, TOKEN)
            repeat(READY_ATTEMPTS) {
                if (runCatching { target.namespaceExists("default") }.getOrDefault(false)) {
                    return url
                }
                Thread.sleep(READY_DELAY)
            }
            error("The server did not become ready.")
        }
    }
}
