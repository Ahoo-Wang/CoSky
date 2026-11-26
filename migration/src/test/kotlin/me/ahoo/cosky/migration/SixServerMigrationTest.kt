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
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.server.CoSkyServerApplication
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.ConfigurableApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path

/**
 * What the real CoSky 6.x wrote, through its own REST API, arrives in 8.0: the tool runs from its command line into an
 * 8.0 server on the same Redis, as the upgrade guide allows, while 6.x is still running there.
 *
 * Every hand-made fixture of [MigrationTest] follows what its authors read in 6.x's scripts. This one follows 6.x.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SixServerMigrationTest {
    private val network = Network.newNetwork()
    private val redis = RedisContainer()
        .withNetwork(network)
        .withNetworkAliases(SixServer.REDIS_ALIAS)
        .withExposedPorts(SixServer.REDIS_PORT)
    private val token = Tokens.generate().plaintext
    private val directory: Path = Files.createTempDirectory("cosky-migration")
    private val output = ByteArrayOutputStream()
    private lateinit var six: SixServer
    private lateinit var eight: ConfigurableApplicationContext
    private lateinit var target: RestTarget
    private lateinit var url: String
    private lateinit var before: Map<String, List<String>>
    private var exitCode = -1

    private class RedisContainer : GenericContainer<RedisContainer>("redis:7.4-alpine")

    private val redisUri: String
        get() = "redis://${redis.host}:${redis.getMappedPort(SixServer.REDIS_PORT)}"

    private val report: String
        get() = Files.readString(directory.resolve("report.md"))

    @BeforeAll
    fun migrate() {
        redis.start()
        six = SixServer(network)
        writeWithSix()
        before = everythingSixHolds()
        startEight()
        exitCode = migrate(
            listOf("--source", redisUri, "--target", url, "--report", "${directory.resolve("report.md")}", "--history"),
            mapOf(Options.TOKEN_VARIABLE to token),
            PrintStream(output),
        )
    }

    @AfterAll
    fun stop() {
        if (::eight.isInitialized) {
            eight.close()
        }
        if (::six.isInitialized) {
            six.close()
        }
        redis.stop()
        network.close()
        directory.toFile().deleteRecursively()
    }

    /**
     * Namespaces as 6.x advised them, with a hash tag, and as some named them, without; Configs with versions, a
     * rollback and a removal; instances that renew and one that nothing renews; a role and a user.
     */
    private fun writeWithSix() {
        six.createNamespace(SHOP)
        six.createNamespace(STAGING)
        six.setConfig(SHOP, "application.yaml", "greeting: Hello")
        six.setConfig(SHOP, "application.yaml", "greeting: Bonjour")
        six.rollback(SHOP, "application.yaml", 1)
        six.setConfig(SHOP, "order-service", "order:\n  limit: 10")
        six.setConfig(SHOP, "db.properties", "url=jdbc:h2:mem:shop")
        six.removeConfig(SHOP, "db.properties")
        six.setConfig(STAGING, "application.yaml", "greeting: Staging")
        six.setConfig(DEFAULT, "shared.json", """{"timeout": 3}""")
        six.register(SHOP, "order", "10.0.0.1", ephemeral = false, metadata = """{"zone":"az1"}""")
        six.register(SHOP, "cart", "10.0.0.2", ephemeral = true)
        six.saveRole("shop-dev", "Shop developers", SHOP, "rw")
        six.addUser("dana", "dana-password", "shop-dev")
    }

    /** Each Config of each namespace, as 6.x's API tells its contents. */
    private fun everythingSixHolds(): Map<String, List<String>> = listOf(SHOP, STAGING, DEFAULT).flatMap { namespace ->
        six.configs(namespace).map { name -> "$namespace/$name" to six.versions(namespace, name) }
    }.toMap()

    private fun startEight() {
        eight = SpringApplicationBuilder(CoSkyServerApplication::class.java).run(
            "--server.port=0",
            "--spring.grpc.server.port=0",
            "--cosky.store.redis.uri=$redisUri",
            "--cosky.auth.bootstrap-token=$token",
        )
        url = "http://localhost:${eight.environment.getRequiredProperty("local.server.port")}"
        target = RestTarget(url, token)
        // A server just started refuses requests until its access rules have loaded.
        repeat(READY_ATTEMPTS) {
            if (runCatching { target.namespaceExists("default") }.getOrDefault(false)) {
                return
            }
            Thread.sleep(READY_DELAY)
        }
        error("The 8.0 server did not become ready.")
    }

    private fun configsOf(namespace: String): List<String> {
        val request = HttpRequest.newBuilder(URI("$url/v1/namespaces/$namespace/configs"))
            .header("Authorization", "Bearer $token")
            .build()
        val body = HttpClient.newHttpClient().send(request, BodyHandlers.ofString()).body()
        val items = JsonParser.parseString(body).asJsonObject["items"]?.asJsonArray ?: return emptyList()
        return items.map { it.asJsonObject["meta"].asJsonObject["name"].asString }
            .sorted()
    }

    @Test
    fun `every Config arrives as 6x holds it, with its history`() {
        exitCode.assert().isEqualTo(EXIT_DONE)
        before.keys.assert().containsExactlyInAnyOrder(
            "$SHOP/application.yaml",
            "$SHOP/order-service",
            "$STAGING/application.yaml",
            "$DEFAULT/shared.json",
        )
        configsOf("shop").assert().containsExactly("application.yaml", "order-service")
        configsOf("staging").assert().containsExactly("application.yaml")
        configsOf("default").assert().containsExactly("shared.json")
        mapOf(SHOP to "shop", STAGING to "staging", DEFAULT to "default").forEach { (from, to) ->
            six.configs(from).forEach { name ->
                val versions = checkNotNull(before["$from/$name"])
                target.config(to, name)?.config?.content.assert().isEqualTo(versions.last())
                target.history(to, name).map { it.config.content }.assert().isEqualTo(versions)
            }
        }
        // The rollback is a version of its own in 6.x, and a revision of its own in 8.0.
        target.history("shop", "application.yaml").map { it.config.content }
            .assert().containsExactly("greeting: Hello", "greeting: Bonjour", "greeting: Hello")
        target.config("shop", "application.yaml")?.config?.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_YAML)
        target.config("shop", "order-service")?.config?.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_YAML)
        target.config("default", "shared.json")?.config?.format.assert().isEqualTo(ConfigFormat.CONFIG_FORMAT_JSON)
    }

    @Test
    fun `6x keeps all it had, beside 8 on its Redis`() {
        everythingSixHolds().assert().isEqualTo(before)
        six.services(SHOP).assert().containsExactly("cart", "order")
    }

    @Test
    fun `the report names what has to be rebuilt by hand`() {
        val summary = output.toString()
        summary.assert().contains(
            "cosky-{shop} -> shop: 2 created, 0 updated, 0 unchanged, 0 need attention, 1 fixed instances to rebuild",
        )
        summary.assert().contains("staging -> staging: 1 created")
        summary.assert().contains("cosky-{default} -> default: 1 created")
        summary.assert().contains("cosky-{system}: left out, 0 Configs")
        report.assert().contains("| `shop` | 2 | 1 |")
        report.assert().contains(
            "| `shop` | `order` | `order@http#10.0.0.1#8080` | `http://10.0.0.1:8080` | 3 | zone |",
        )
        report.assert().contains(
            "| `shop-dev` | Shop developers | `cosky-{shop}`: rw | " +
                "namespaces `shop`, verbs GET, WATCH, PUT, DELETE |  |",
        )
        report.assert().contains("| `dana` | `shop-dev` |  |")
        report.assert().contains("| `cosky` |  | No roles. The root user of 6.x is replaced by the bootstrap token. |")
        report.assert().doesNotContain("dana-password")
    }

    private companion object {
        const val SHOP = "cosky-{shop}"
        const val STAGING = "staging"
        const val DEFAULT = NamespaceMapping.DEFAULT
        const val READY_ATTEMPTS = 100
        const val READY_DELAY = 100L
    }
}
