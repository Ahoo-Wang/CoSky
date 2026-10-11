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
package me.ahoo.cosky.rest

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.rest.security.rbac.Role
import me.ahoo.cosky.rest.security.user.UserService
import me.ahoo.cosky.rest.util.Zips
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import java.time.Duration

/**
 * Exercises the REST API over HTTP, end to end: a real server, Redis, JWT authentication, the security policy, RBAC
 * and the audit log.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.cloud.bootstrap.enabled=true",
        "cosky.security.key=test-only-jwt-secret-0123456789abcdef",
    ],
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RestApiIntegrationTest {
    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var userService: UserService

    private lateinit var client: WebTestClient
    private val suffix = MockIdGenerator.INSTANCE.generateAsString()
    private val namespace = "it-$suffix"
    private val admin = "admin-$suffix"
    private val reader = "reader-$suffix"
    private val readerRole = "reader-role-$suffix"
    private val password = "password-$suffix"
    private lateinit var adminToken: String

    @BeforeAll
    fun setUp() {
        client = WebTestClient.bindToServer()
            .baseUrl("http://localhost:$port")
            .responseTimeout(Duration.ofSeconds(10))
            .build()
        userService.addUser(admin, password).block()
        userService.bindRole(admin, setOf(Role.ADMIN_ROLE)).block()
        adminToken = login(admin, password)
        put("/v1/namespaces/$namespace", adminToken).expectStatus().isOk
    }

    @AfterAll
    fun tearDown() {
        delete("/v1/users/$reader", adminToken)
        delete("/v1/roles/$readerRole", adminToken)
        delete("/v1/namespaces/$namespace", adminToken)
        userService.removeUser(admin).block()
    }

    private fun login(username: String, password: String): String {
        val token = client.post().uri("/v1/authenticate/$username/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("password" to password))
            .exchange()
            .expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<Map<String, Any>>() {})
            .returnResult().responseBody!!
        return token.getValue("accessToken").toString()
    }

    private fun <S : WebTestClient.RequestHeadersSpec<*>> S.authorized(token: String?): S = apply {
        if (token != null) {
            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }
    }

    private fun get(uri: String, token: String?, vararg uriVariables: Any) =
        client.get().uri(uri, *uriVariables).authorized(token).exchange()

    private fun delete(uri: String, token: String, vararg uriVariables: Any) =
        client.delete().uri(uri, *uriVariables).authorized(token).exchange()

    private fun put(
        uri: String,
        token: String,
        body: Any? = null,
        vararg uriVariables: Any
    ): WebTestClient.ResponseSpec {
        val request = client.put().uri(uri, *uriVariables)
        if (body != null) {
            val contentType = if (body is String) MediaType.TEXT_PLAIN else MediaType.APPLICATION_JSON
            request.contentType(contentType).bodyValue(body)
        }
        return request.authorized(token).exchange()
    }

    @Test
    fun requestsWithoutTokenAreRejected() {
        get("/v1/namespaces/$namespace/configs", null).expectStatus().isUnauthorized
    }

    @Test
    fun unknownRouteKeepsItsStatus() {
        get("/v1/namespaces/$namespace/configs/app.yaml/unknown", adminToken).expectStatus().isNotFound
    }

    @Test
    fun configLifecycle() {
        val configs = "/v1/namespaces/$namespace/configs"
        put("$configs/app.yaml", adminToken, "k: v1").expectStatus().isOk
        put("$configs/app.yaml", adminToken, "k: v2").expectStatus().isOk

        get("$configs/app.yaml", adminToken).expectStatus().isOk
            .expectBody().jsonPath("$.data").isEqualTo("k: v2").jsonPath("$.version").isEqualTo(2)
        get(configs, adminToken).expectStatus().isOk.expectBody().jsonPath("$[0]").isEqualTo("app.yaml")
        get("$configs/app.yaml/versions", adminToken).expectStatus().isOk
            .expectBody().jsonPath("$[0].version").isEqualTo(1)
        get("$configs/app.yaml/versions/1", adminToken).expectStatus().isOk
            .expectBody().jsonPath("$.data").isEqualTo("k: v1")

        put("$configs/app.yaml/to/1", adminToken).expectStatus().isOk
        get("$configs/app.yaml", adminToken).expectStatus().isOk.expectBody().jsonPath("$.data").isEqualTo("k: v1")

        val exported = get("$configs/export", adminToken).expectStatus().isOk
            .expectBody(ByteArray::class.java).returnResult().responseBody!!
        Zips.unzip(exported).map { it.name }.assert().containsExactly("app.yaml")

        val zip = Zips.zip(listOf(Zips.ZipItem.of("imported.yaml", "imported: true")))
        val multipart = MultipartBodyBuilder().apply {
            part(
                "importZip",
                object : ByteArrayResource(zip) {
                    override fun getFilename() = "configs.zip"
                }
            )
        }.build()
        client.post().uri("$configs?policy=overwrite")
            .authorized(adminToken)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipart))
            .exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.total").isEqualTo(1).jsonPath("$.succeeded").isEqualTo(1)

        delete("$configs/imported.yaml", adminToken).expectStatus().isOk
        delete("$configs/app.yaml", adminToken).expectStatus().isOk
        get(configs, adminToken).expectStatus().isOk.expectBody().jsonPath("$.length()").isEqualTo(0)
    }

    @Test
    fun serviceLifecycle() {
        val services = "/v1/namespaces/$namespace/services"
        put("$services/order-service", adminToken).expectStatus().isOk
        put(
            "$services/order-service/instances",
            adminToken,
            mapOf("schema" to "http", "host" to "127.0.0.1", "port" to 8080, "isEphemeral" to false),
        ).expectStatus().isOk

        val instanceId = get("$services/order-service/instances", adminToken).expectStatus().isOk
            .expectBody(object : ParameterizedTypeReference<List<Map<String, Any>>>() {})
            .returnResult().responseBody!!
            .single()["instanceId"].toString()
        put("$services/order-service/instances/{instanceId}/metadata", adminToken, mapOf("zone" to "a"), instanceId)
            .expectStatus().isOk
        get("$services/order-service/instances", adminToken).expectStatus().isOk
            .expectBody().jsonPath("$[0].metadata.zone").isEqualTo("a")

        get(services, adminToken).expectStatus().isOk.expectBody().jsonPath("$[0]").isEqualTo("order-service")
        get("$services/stats", adminToken).expectStatus().isOk
            .expectBody().jsonPath("$[0].serviceId").isEqualTo("order-service")
        get("/v1/namespaces/$namespace/stat", adminToken).expectStatus().isOk
            .expectBody().jsonPath("$.services.total").isEqualTo(1)
        get("/v1/namespaces/$namespace/stat/topology", adminToken).expectStatus().isOk

        delete("$services/order-service/instances/{instanceId}", adminToken, instanceId).expectStatus().isOk
        delete("$services/order-service", adminToken).expectStatus().isOk
    }

    @Test
    fun namespaceRoleGrantsOnlyItsNamespace() {
        put(
            "/v1/roles/$readerRole",
            adminToken,
            mapOf(
                "desc" to "read $namespace",
                "resourceActionBind" to listOf(mapOf("namespace" to namespace, "action" to "r"))
            ),
        ).expectStatus().isOk
        get("/v1/roles/$readerRole/bind", adminToken).expectStatus().isOk
            .expectBody().jsonPath("$[0].namespace").isEqualTo(namespace)
        client.post().uri("/v1/users/$reader")
            .authorized(adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("password" to password))
            .exchange()
            .expectStatus().isOk
        client.patch().uri("/v1/users/$reader/role")
            .authorized(adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(listOf(readerRole))
            .exchange()
            .expectStatus().isOk

        val readerToken = login(reader, password)
        get("/v1/namespaces", readerToken).expectStatus().isOk
            .expectBody().jsonPath("$.length()").isEqualTo(1).jsonPath("$[0]").isEqualTo(namespace)
        get("/v1/namespaces/$namespace/configs", readerToken).expectStatus().isOk
        put("/v1/namespaces/$namespace/configs/denied.yaml", readerToken, "k: v").expectStatus().isForbidden
        get("/v1/namespaces/other-$suffix/configs", readerToken).expectStatus().isForbidden
        get("/v1/users", readerToken).expectStatus().isForbidden
    }

    @Test
    fun usersAndAuditLog() {
        val username = "user-$suffix"
        client.post().uri("/v1/users/$username")
            .authorized(adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("password" to password))
            .exchange()
            .expectStatus().isOk
        get("/v1/users", adminToken).expectStatus().isOk
            .expectBody().jsonPath("$[?(@.id == '$username')]").exists()
        put("/v1/users/$username/lock", adminToken).expectStatus().isOk
        client.post().uri("/v1/authenticate/$username/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("password" to password))
            .exchange()
            .expectStatus().value { it.assert().isNotEqualTo(HttpStatus.OK.value()) }
        delete("/v1/users/$username/unlock", adminToken).expectStatus().isOk
        login(username, password)
        delete("/v1/users/$username", adminToken).expectStatus().isOk

        get("/v1/audit-log?query=$username&limit=100", adminToken).expectStatus().isOk
            .expectBody().jsonPath("$.list[0].operator").isEqualTo(admin)
        get("/v1/audit-log/export?query=$username", adminToken).expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith("text/csv")
    }
}
