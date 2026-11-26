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

package me.ahoo.cosky.examples.e2e

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.ahoo.cosky.examples.e2e.Processes.Companion.CONSUMER
import me.ahoo.cosky.examples.e2e.Processes.Companion.HTTP_OK
import me.ahoo.cosky.examples.e2e.Processes.Companion.PROVIDER
import me.ahoo.cosky.examples.e2e.Processes.Companion.SERVER
import me.ahoo.cosky.examples.e2e.Processes.Companion.WAIT_SECONDS
import me.ahoo.cosky.examples.e2e.Processes.Companion.freePort
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * The production path: the server on Redis with sign-in on, given its first token as its image is, and the examples on
 * the starter with the token of their namespace's own account (design D45), each a process of its own.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecuredEndToEndTest {
    private val server = Processes()
    private val examples = Processes()
    private val redis = RedisContainer().withExposedPorts(REDIS_PORT)
    private val serverPort = freePort()
    private val grpcPort = freePort()
    private val consumerPort = freePort()
    private val admin = "cosky_${base62(ID_LENGTH)}_${base62(SECRET_LENGTH)}"
    private val api = "http://localhost:$serverPort/v1"
    private lateinit var shopToken: String
    private lateinit var otherToken: String

    private class RedisContainer : GenericContainer<RedisContainer>("redis:7.4-alpine")

    @BeforeAll
    fun startServer() {
        redis.start()
        server.start(
            "server",
            "secured-server",
            SERVER,
            listOf(
                "--server.port=$serverPort",
                "--spring.grpc.server.port=$grpcPort",
                "--cosky.store.redis.uri=redis://${redis.host}:${redis.getMappedPort(REDIS_PORT)}",
            ),
            mapOf("COSKY_BOOTSTRAP_TOKEN" to admin),
        )
        server.eventually("the server to be ready") {
            server.get("http://localhost:$serverPort/actuator/health/readiness").statusCode() == HTTP_OK
        }
        createNamespace(SHOP)
        createNamespace(OTHER)
        shopToken = tokenOf(SHOP)
        otherToken = tokenOf(OTHER)
    }

    @AfterAll
    fun stopServer() {
        server.close()
        redis.stop()
    }

    @AfterEach
    fun stopExamples() {
        examples.close()
    }

    private fun createNamespace(name: String) = server.eventually("namespace $name to be created") {
        server.put("$api/namespaces/$name", """{"namespace":{}}""", admin).statusCode() == HTTP_OK
    }

    /**
     * A token of the account the namespace comes with, which the server creates with it.
     */
    private fun tokenOf(namespace: String): String {
        var token: String? = null
        server.eventually("a token for the account of namespace $namespace") {
            val response = server.post("$api/serviceaccounts/$namespace/tokens", "{}", admin)
            token = response.takeIf { it.statusCode() == HTTP_OK }?.let { json(it.body())["token"].asString }
            token != null
        }
        return checkNotNull(token)
    }

    private fun json(body: String): JsonObject = JsonParser.parseString(body).asJsonObject

    private fun putConfig(greeting: String) {
        val body = """{"config":{"format":"CONFIG_FORMAT_YAML","content":"example:\n  greeting: $greeting"}}"""
        server.put("$api/namespaces/$SHOP/configs/provider.yaml", body, admin).statusCode().assert().isEqualTo(HTTP_OK)
    }

    /**
     * An example in namespace [SHOP], with [token] in `COSKY_TOKEN` as a deployment would give it.
     */
    private fun startExample(
        log: String,
        mainClass: String,
        token: String?,
        vararg arguments: String,
    ): Process = examples.start(
        if (mainClass == PROVIDER) "provider" else "consumer",
        log,
        mainClass,
        listOf(
            "--cosky.endpoints=localhost:$grpcPort",
            "--cosky.namespace=$SHOP",
            "--cosky.snapshot.enabled=false",
            *arguments,
        ),
        token?.let { mapOf("COSKY_TOKEN" to it) }.orEmpty(),
    )

    private fun call(): String = examples.get("http://localhost:$consumerPort/call/World").body()

    @Test
    fun `the examples read, register, discover and watch with their namespace's token`() {
        putConfig("Bonjour")
        startExample("secured-provider", PROVIDER, shopToken, "--server.port=0")
        startExample("secured-consumer", CONSUMER, shopToken, "--server.port=$consumerPort")
        examples.eventually("the consumer to reach the provider") { call() == "Bonjour, World" }

        putConfig("Hi")
        examples.eventually("the changed Config to take effect") { call() == "Hi, World" }

        val instances = server.get("$api/namespaces/$SHOP/instances?service=provider", admin)
        val registered = json(instances.body()).getAsJsonArray("items").single().asJsonObject
        registered["meta"].asJsonObject["updatedBy"].asString.assert().isEqualTo("serviceaccount:$SHOP")
    }

    @Test
    fun `an example without a token, or with another namespace's, stops at start and says why`() {
        val config = "$api/namespaces/$SHOP/configs/provider.yaml"
        server.get(config).statusCode().assert().isEqualTo(HTTP_UNAUTHORIZED)
        server.get(config, otherToken).statusCode().assert().isEqualTo(HTTP_FORBIDDEN)

        // The provider's import is optional: required, a refused Config stops it before it registers.
        val required = "--spring.config.import=cosky:provider.yaml"
        listOf(
            startExample("provider-without-token", PROVIDER, null, "--server.port=0", required),
            startExample("consumer-without-token", CONSUMER, null, "--server.port=0"),
            startExample("provider-with-other-token", PROVIDER, otherToken, "--server.port=0"),
        ).forEach { process ->
            process.waitFor(WAIT_SECONDS, TimeUnit.SECONDS).assert().isTrue()
            process.exitValue().assert().isNotEqualTo(0)
        }
        examples.logOf("provider-without-token").readText().assert()
            .contains("CoSky refused to load Config provider.yaml of namespace $SHOP: UNAUTHENTICATED")
        examples.logOf("consumer-without-token").readText().assert()
            .contains("CoSky refused to register service consumer in namespace $SHOP: UNAUTHENTICATED")
        examples.logOf("provider-with-other-token").readText().assert()
            .contains("CoSky refused to register service provider in namespace $SHOP: PERMISSION_DENIED")
    }

    private companion object {
        const val REDIS_PORT = 6379
        const val SHOP = "shop"
        const val OTHER = "other"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val ID_LENGTH = 16
        const val SECRET_LENGTH = 43
        const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val random = SecureRandom()

        fun base62(length: Int): String = String(CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] })
    }
}
