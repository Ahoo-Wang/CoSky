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

package me.ahoo.cosky.server.auth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.micrometer.core.instrument.MeterRegistry
import me.ahoo.cosky.server.RunningServer
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.MountableFile
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.Base64

/**
 * OIDC against a real provider (design §6.3, §7.8): Keycloak, with the realm of `keycloak/cosky-realm.json` set up
 * as `wiki/guide/security.md` says, and a server that knows it by nothing but its issuer and client.
 *
 * The tests share the provider and one server, in order: the first needs a server that has never asked the provider.
 * An expired token is left to [OidcAuthenticatorTest]: one from Keycloak would take a minute past its expiry to be
 * refused, since a server allows a minute of clock skew.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KeycloakTest {
    private val keycloak = KeycloakContainer()
    private val http = HttpClient.newHttpClient()
    private val bootstrap = Tokens.generate().plaintext
    private lateinit var issuer: String
    private lateinit var server: RunningServer

    private class KeycloakContainer : GenericContainer<KeycloakContainer>("quay.io/keycloak/keycloak:26.8.2") {
        init {
            withExposedPorts(HTTP_PORT)
            withCopyFileToContainer(
                MountableFile.forClasspathResource("keycloak/cosky-realm.json"),
                "/opt/keycloak/data/import/cosky-realm.json",
            )
            withCommand("start-dev", "--import-realm")
            waitingFor(
                Wait.forHttp("/realms/cosky/.well-known/openid-configuration")
                    .forPort(HTTP_PORT)
                    .withStartupTimeout(Duration.ofMinutes(3)),
            )
        }
    }

    @BeforeAll
    fun start() {
        keycloak.start()
        issuer = "http://${keycloak.host}:${keycloak.getMappedPort(HTTP_PORT)}/realms/cosky"
        server = startServer()
        bind("cosky-admins", "admin")
        bind("cosky-viewers", "viewer")
    }

    @AfterAll
    fun stop() {
        if (::server.isInitialized) {
            server.close()
        }
        keycloak.stop()
    }

    private fun startServer(): RunningServer {
        // One server for all: a second one closed in this process takes the web server of the first down with it, as
        // they share Reactor Netty's resources.
        val started = RunningServer(
            mapOf(
                "spring.main.web-application-type" to "reactive",
                "cosky.store.type" to "memory",
                "cosky.auth.bootstrap-token" to bootstrap,
                "cosky.auth.oidc.issuer" to issuer,
                "cosky.auth.oidc.client-id" to CLIENT,
            ),
        )
        // A server just started refuses tokens until its access rules have loaded, which readiness waits for.
        eventually("the server to be ready") { started.call("GET", "/actuator/health/readiness").status == HTTP_OK }
        return started
    }

    private fun bind(group: String, role: String) {
        val binding = """{"roleBinding":{"role":"$role","subjects":[{"type":"TYPE_GROUP","name":"$group"}],""" +
            """"namespaces":["*"]}}"""
        eventually("group $group to be bound to $role") {
            server.call("PUT", "/v1/rolebindings/$group", bootstrap, binding).status == HTTP_OK
        }
    }

    /**
     * An ID token of [user] for [client], by the password grant, which the realm allows its test clients.
     */
    private fun idToken(client: String, user: String): String {
        val form = mapOf(
            "grant_type" to "password",
            "client_id" to client,
            "username" to user,
            "password" to "$user-password",
            "scope" to "openid",
        ).entries.joinToString("&") { (name, value) -> "$name=${URLEncoder.encode(value, Charsets.UTF_8)}" }
        val request = HttpRequest.newBuilder(URI("$issuer/protocol/openid-connect/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(form))
            .build()
        val response = http.send(request, BodyHandlers.ofString())
        check(response.statusCode() == HTTP_OK) { "Keycloak refused the sign-in: ${response.body()}" }
        return JsonParser.parseString(response.body()).asJsonObject["id_token"].asString
    }

    private fun claimsOf(token: String): JsonObject =
        JsonParser.parseString(String(Base64.getUrlDecoder().decode(token.split('.')[1]))).asJsonObject

    private class Answer(val status: Int, val body: JsonObject)

    private fun RunningServer.call(method: String, path: String, token: String? = null, body: String? = null): Answer {
        val request = HttpRequest.newBuilder(URI("http://localhost:$httpPort$path"))
            .method(method, body?.let { BodyPublishers.ofString(it) } ?: BodyPublishers.noBody())
            .timeout(Duration.ofSeconds(REQUEST_SECONDS))
            .header("Content-Type", "application/json")
        token?.let { request.header("Authorization", "Bearer $it") }
        val response = http.send(request.build(), BodyHandlers.ofString())
        val json = response.body().takeIf { it.startsWith("{") }?.let { JsonParser.parseString(it).asJsonObject }
        return Answer(response.statusCode(), json ?: JsonObject())
    }

    private fun RunningServer.failures(reason: String): Double = bean(MeterRegistry::class.java)
        .find("cosky_auth_failures_total").tag("reason", reason).counter()?.count() ?: 0.0

    private fun eventually(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + Duration.ofSeconds(WAIT_SECONDS).toNanos()
        while (!runCatching(condition).getOrDefault(false)) {
            check(System.nanoTime() < deadline) { "Timed out waiting for $what." }
            Thread.sleep(POLL_MILLIS)
        }
    }

    /**
     * First, while the server has never needed the provider: a token it cannot check is not a bad token, and once the
     * provider is back the same token gets in, without a restart. The tests after it run on that same server.
     */
    @Test
    @Order(1)
    fun `the provider being away is no bad token, and its coming back needs no restart`() {
        val ann = idToken(CLIENT, ANN)
        val docker = keycloak.dockerClient
        docker.pauseContainerCmd(keycloak.containerId).exec()
        val away = try {
            server.call("GET", "/v1/me", ann)
        } finally {
            docker.unpauseContainerCmd(keycloak.containerId).exec()
        }
        away.status.assert().isEqualTo(HTTP_UNAVAILABLE)
        away.body["reason"].asString.assert().isEqualTo("ERROR_REASON_IDENTITY_PROVIDER_UNAVAILABLE")
        server.failures("unavailable").assert().isEqualTo(1.0)
        server.failures("invalid").assert().isEqualTo(0.0)

        server.call("GET", "/v1/me", ann).status.assert().isEqualTo(HTTP_OK)
    }

    @Test
    @Order(2)
    fun `a person signs in with an ID token, and their groups give them their roles`() {
        val ann = idToken(CLIENT, ANN)
        val sub = claimsOf(ann)["sub"].asString
        eventually("ann to hold admin through her group") {
            server.call("PUT", "/v1/namespaces/oidc", ann, """{"namespace":{}}""").status == HTTP_OK
        }
        val me = server.call("GET", "/v1/me", ann).body
        me["subject"].asJsonObject["type"].asString.assert().isEqualTo("TYPE_USER")
        me["subject"].asJsonObject["name"].asString.assert().isEqualTo(sub)
        me["groups"].asJsonArray.map { it.asString }.assert().containsExactly("cosky-admins")
        val namespace = server.call("GET", "/v1/namespaces/oidc", ann).body
        namespace["meta"].asJsonObject["updatedBy"].asString.assert().isEqualTo("user:$sub")

        val bob = idToken(CLIENT, BOB)
        server.call("GET", "/v1/me", bob).body["groups"].asJsonArray.map { it.asString }
            .assert().containsExactly("cosky-viewers")
        server.call("GET", "/v1/namespaces/oidc", bob).status.assert().isEqualTo(HTTP_OK)
        val denied = server.call("PUT", "/v1/namespaces/bobs", bob, """{"namespace":{}}""")
        denied.status.assert().isEqualTo(HTTP_FORBIDDEN)
        denied.body["reason"].asString.assert().isEqualTo("ERROR_REASON_PERMISSION_DENIED")
    }

    @Test
    @Order(3)
    fun `a token for another client is refused as invalid`() {
        val before = server.failures("invalid")
        val refused = server.call("GET", "/v1/me", idToken("other-app", ANN))
        refused.status.assert().isEqualTo(HTTP_UNAUTHORIZED)
        refused.body["reason"].asString.assert().isEqualTo("ERROR_REASON_UNAUTHENTICATED")
        refused.body["detail"].asString.assert().contains("another audience")
        server.failures("invalid").assert().isEqualTo(before + 1)
    }

    private companion object {
        const val HTTP_PORT = 8080
        const val CLIENT = "cosky-dashboard"
        const val ANN = "ann"
        const val BOB = "bob"
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_UNAVAILABLE = 503
        const val REQUEST_SECONDS = 15L
        const val WAIT_SECONDS = 60L
        const val POLL_MILLIS = 500L
    }
}
