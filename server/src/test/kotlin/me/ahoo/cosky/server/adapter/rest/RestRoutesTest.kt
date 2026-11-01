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

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TokenRef
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.roleBindingSpec
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.TokenService
import me.ahoo.cosky.server.auth.Tokens
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.view.ViewService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class RestRoutesTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, 64, scope)
    private val access = AccessControl(caches)
    private val adminToken = Tokens.generate()

    private fun client(authenticated: Boolean = false, properties: CoSkyProperties = CoSkyProperties()): WebTestClient {
        val authorizer: Authorizer = if (authenticated) access else Authorizer.ALLOW_ALL
        val routes = RestRoutes(
            ResourceService(store, caches, authorizer = authorizer),
            ViewService(store, caches, authorizer),
            TokenService(store, caches, authorizer),
            caches,
            authorizer,
            access.takeIf { authenticated },
            AuditLogger(),
            properties,
        ).routes()
        return WebTestClient.bindToRouterFunction(routes).build()
    }

    @BeforeEach
    fun start(): Unit = runBlocking {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        store.createNamespace(NAMESPACE)
    }

    @AfterEach
    fun stop() {
        scope.cancel()
    }

    private val config = """{"config":{"format":"CONFIG_FORMAT_TEXT","content":"a"}}"""

    private fun WebTestClient.putConfig(name: String, body: String = config) = put()
        .uri(URI("/v1/namespaces/$NAMESPACE/configs/$name"))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()

    private fun WebTestClient.ResponseSpec.json(): JsonObject =
        JsonParser.parseString(expectBody(String::class.java).returnResult().responseBody).asJsonObject

    private fun WebTestClient.ResponseSpec.reason(): String =
        json().getAsJsonObject("detail")["reason"].asString

    private fun revisionOf(put: WebTestClient.ResponseSpec): Long = put.expectStatus().isOk.json()["revision"].asLong

    private fun get(client: WebTestClient, name: String, revision: Long) =
        client.get().uri(URI("/v1/namespaces/$NAMESPACE/configs/$name?minRevision=$revision")).exchange()

    @Test
    fun `an object comes back as it was put, with its revision as the entity tag`() {
        val client = client()
        val revision = revisionOf(client.putConfig("a"))
        val body = get(client, "a", revision)
            .expectStatus().isOk
            .expectHeader().valueEquals("ETag", "\"$revision\"")
            .json()
        body.getAsJsonObject("config")["content"].asString.assert().isEqualTo("a")
        body.getAsJsonObject("meta")["name"].asString.assert().isEqualTo("a")
    }

    @Test
    fun `a name in the path may hold an encoded slash`() {
        val client = client()
        val revision = revisionOf(client.putConfig("shared%2Fdb.yaml"))
        get(client, "shared%2Fdb.yaml", revision).expectStatus().isOk.json()
            .getAsJsonObject("meta")["name"].asString.assert().isEqualTo("shared/db.yaml")
    }

    @Test
    fun `the path decides what a body means`() {
        val client = client()
        client.putConfig(
            "a",
            """{"meta":{"namespace":"elsewhere","name":"b"},"config":{"format":"CONFIG_FORMAT_TEXT","content":"a"}}"""
        )
            .expectStatus().isOk
        val instance = """{"instance":{"service":"order"}}"""
        client.putConfig("c", instance).expectStatus().isBadRequest
    }

    @Test
    fun `if-none-match creates only, and if-match compares`() {
        val client = client()
        val uri = "/v1/namespaces/$NAMESPACE/configs/a"
        val body = config
        fun put(header: String, value: String) = client.put().uri(uri)
            .contentType(MediaType.APPLICATION_JSON).header(header, value).bodyValue(body).exchange()
        val revision = revisionOf(put("If-None-Match", "*"))
        put("If-None-Match", "*").expectStatus().isEqualTo(409).reason().assert()
            .isEqualTo("ERROR_REASON_REVISION_CONFLICT")
        put("If-Match", "\"${revision + 1}\"").expectStatus().isEqualTo(409)
        put("If-Match", "nonsense").expectStatus().isBadRequest
        client.delete().uri(uri).header("If-Match", "\"$revision\"").exchange().expectStatus().isOk
    }

    @Test
    fun `writing the same content reports no new entity tag`() {
        val client = client()
        client.putConfig("a").expectStatus().isOk.expectHeader().exists("ETag")
        val same = client.putConfig("a").expectStatus().isOk.expectHeader().doesNotExist("ETag")
        same.json()["changed"].let { it == null || !it.asBoolean }.assert().isTrue()
    }

    @Test
    fun `lists filter and page`() {
        val client = client()
        client.putConfig(
            "a",
            """{"meta":{"labels":{"team":"x"}},"config":{"format":"CONFIG_FORMAT_TEXT","content":"a"}}"""
        )
        val revision = revisionOf(client.putConfig("b"))
        val uri = "/v1/namespaces/$NAMESPACE/configs?minRevision=$revision"
        val page = client.get().uri("$uri&pageSize=1").exchange().expectStatus().isOk.json()
        page.getAsJsonArray("items").size().assert().isEqualTo(1)
        val token = page["nextPageToken"].asString
        client.get().uri("$uri&pageToken=$token").exchange().expectStatus().isOk.json()
            .getAsJsonArray("items").first().asJsonObject.getAsJsonObject("meta")["name"].asString
            .assert().isEqualTo("b")
        client.get().uri("$uri&labelSelector=team=x").exchange().expectStatus().isOk.json()
            .getAsJsonArray("items").size().assert().isEqualTo(1)
        client.get().uri("$uri&labelSelector=nonsense").exchange().expectStatus().isBadRequest
        client.get().uri("$uri&pageSize=lots").exchange().expectStatus().isBadRequest
    }

    @Test
    fun `an unknown object is 404 and a stale read is 504`() {
        val client = client()
        get(client, "missing", 0).expectStatus().isNotFound.reason().assert()
            .isEqualTo("ERROR_REASON_RESOURCE_NOT_FOUND")
        val short = RestRoutes(
            ResourceService(store, caches, minRevisionTimeout = kotlin.time.Duration.ZERO),
            ViewService(store, caches),
            TokenService(store, caches),
            caches,
            Authorizer.ALLOW_ALL,
            null,
            AuditLogger(),
            CoSkyProperties(),
        ).routes()
        client.putConfig("a").expectStatus().isOk
        get(WebTestClient.bindToRouterFunction(short).build(), "a", 99).expectStatus().isEqualTo(504)
    }

    @Test
    fun `a rollback restores an earlier revision, even after a delete`() {
        val client = client()
        val first = revisionOf(client.putConfig("a"))
        client.putConfig("a", """{"config":{"format":"CONFIG_FORMAT_TEXT","content":"b"}}""")
        client.delete().uri("/v1/namespaces/$NAMESPACE/configs/a").exchange().expectStatus().isOk
        val restored = client.post().uri("/v1/namespaces/$NAMESPACE/configs/a:rollback")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"toRevision":"$first"}""")
            .exchange()
        val revision = revisionOf(restored)
        get(client, "a", revision).expectStatus().isOk.json().getAsJsonObject("config")["content"].asString
            .assert().isEqualTo("a")
        client.post().uri("/v1/namespaces/$NAMESPACE/configs/a:rollback")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"toRevision":"12345"}""")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `history is reachable`() {
        val client = client()
        client.putConfig("a")
        client.get().uri("/v1/namespaces/$NAMESPACE/configs/a/history?limit=1").exchange()
            .expectStatus().isOk.json().getAsJsonArray("revisions").size().assert().isEqualTo(1)
    }

    @Test
    fun `an archive goes in, and comes out the same`() {
        val client = client()
        val archive = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                listOf("a.yaml" to "x: 1", "b/c.json" to "{}").forEach { (name, content) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.encodeToByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray()
        val imported = client.post().uri("/v1/namespaces/$NAMESPACE/configs:import")
            .contentType(MediaType.parseMediaType("application/zip"))
            .bodyValue(archive)
            .exchange()
            .expectStatus().isOk
            .json().getAsJsonArray("items")
        imported.map { it.asJsonObject["name"].asString }.assert().containsExactly("a.yaml", "b/c.json")
        val revision = imported.maxOf { it.asJsonObject["revision"].asLong }
        val yaml = get(client, "a.yaml", revision).expectStatus().isOk.json()
        yaml.getAsJsonObject("config")["format"].asString.assert().isEqualTo("CONFIG_FORMAT_YAML")

        val exported = client.get().uri("/v1/namespaces/$NAMESPACE/configs:export").exchange()
            .expectStatus().isOk
            .expectHeader().contentType("application/zip")
            .expectBody().returnResult().responseBody
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(exported)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entries[it.name] = zip.readBytes().decodeToString() }
        }
        entries.assert().isEqualTo(mapOf("a.yaml" to "x: 1", "b/c.json" to "{}"))
    }

    private fun archiveOf(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun WebTestClient.import(archive: ByteArray) = post().uri("/v1/namespaces/$NAMESPACE/configs:import")
        .contentType(MediaType.parseMediaType("application/zip"))
        .bodyValue(archive)
        .exchange()

    @Test
    fun `garbage is not an archive, and a huge entry is refused`() {
        val client = client()
        client.import("this is no zip".encodeToByteArray()).expectStatus().isBadRequest
        client.import(archiveOf("huge.txt" to ByteArray(2 * 1024 * 1024))).expectStatus().isBadRequest
    }

    @Test
    fun `an empty archive imports nothing`() {
        client().import(archiveOf()).expectStatus().isOk.json().getAsJsonArray("items").size().assert().isEqualTo(0)
    }

    @Test
    fun `an entry that is not utf-8 is refused rather than damaged`() {
        val gbk = "你好".toByteArray(charset("GBK"))
        client().import(archiveOf("a.txt" to gbk)).expectStatus().isBadRequest
    }

    @Test
    fun `an archive is unpacked as far as it is used`() {
        val archive = archiveOf("small.txt" to "a".encodeToByteArray(), "huge.txt" to ByteArray(1024))
        ConfigArchive.read(archive, 16) { it.first().first }.assert().isEqualTo("small.txt")
    }

    @Test
    fun `importing changes the content, and leaves the rest of a config as it is`() {
        val client = client()
        val labeled = """{"meta":{"labels":{"team":"core"}},"config":{"format":"CONFIG_FORMAT_YAML","content":"a"}}"""
        client.putConfig("a", labeled).expectStatus().isOk
        val revision = client.import(archiveOf("a" to "b: 1".encodeToByteArray())).expectStatus().isOk
            .json().getAsJsonArray("items").single().asJsonObject["revision"].asLong
        val config = get(client, "a", revision).expectStatus().isOk.json()
        config.getAsJsonObject("config")["content"].asString.assert().isEqualTo("b: 1")
        config.getAsJsonObject("config")["format"].asString.assert().isEqualTo("CONFIG_FORMAT_YAML")
        config.getAsJsonObject("meta").getAsJsonObject("labels")["team"].asString.assert().isEqualTo("core")
    }

    @Test
    fun `views are reachable`() {
        val client = client()
        client.put().uri("/v1/namespaces/$NAMESPACE/instances/o1")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"instance":{"service":"order","endpoint":{"scheme":"http","host":"localhost","port":8080}}}""",
            )
            .exchange()
            .expectStatus().isOk
        runBlocking { caches.of(NAMESPACE).views.first { it != null && it.revision >= 1 } }
        client.get().uri("/v1/namespaces/$NAMESPACE/views/services").exchange().expectStatus().isOk.json()
            .getAsJsonArray("services").size().assert().isEqualTo(1)
        val stats = client.get().uri("/v1/namespaces/$NAMESPACE/views/stats").exchange().expectStatus().isOk.json()
        stats["instanceCount"].asInt.assert().isEqualTo(1)
        client.get().uri("/v1/namespaces/$NAMESPACE/views/topology").exchange().expectStatus().isOk
    }

    @Test
    fun `a watch streams events, and bad parameters are refused before it starts`() {
        val client = client()
        val revision = revisionOf(client.putConfig("a"))
        runBlocking { caches.of(NAMESPACE).views.first { it != null && it.revision >= revision } }
        val result = client.get()
            .uri("/v1/namespaces/$NAMESPACE/watch?kind=KIND_CONFIG")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .returnResult(String::class.java)
        val first = result.responseBody.blockFirst()
        JsonParser.parseString(first).asJsonObject.getAsJsonObject("snapshot").getAsJsonArray("items").size()
            .assert().isEqualTo(1)
        client.get().uri("/v1/namespaces/$NAMESPACE/watch").exchange().expectStatus().isBadRequest
        client.get().uri("/v1/namespaces/$NAMESPACE/watch?kind=KIND_CONFIG&fromRevision=1").exchange()
            .expectStatus().isBadRequest
    }

    // --- authentication ---

    private suspend fun bootstrapAdmin() {
        val admin = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                name = "root"
            }
            serviceAccount = serviceAccountSpec {
                tokens += TokenRef.newBuilder()
                    .setId(adminToken.id)
                    .setSha256(Tokens.sha256Hex(adminToken.secret))
                    .build()
            }
        }
        val binding = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                name = "root"
            }
            roleBinding = roleBindingSpec {
                role = "admin"
                subjects += Subject.newBuilder().setType(Subject.Type.TYPE_SERVICE_ACCOUNT).setName("root").build()
                namespaces += "*"
            }
        }
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(admin), Mutation.Put(binding)), "test")
        caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null && it.revision >= 1 }
    }

    private fun bearer(token: String = adminToken.plaintext): String = "Bearer $token"

    @Test
    fun `the sign-in page learns how people sign in, before anyone has`() {
        val oidc = CoSkyProperties.Oidc(issuer = "https://idp.example", clientId = "dashboard", audience = "private")
        val client = client(authenticated = true, CoSkyProperties(auth = CoSkyProperties.Auth(oidc = oidc)))

        val config = client.get().uri("/v1/auth/config").exchange().expectStatus().isOk.json().getAsJsonObject("oidc")

        config["issuer"].asString.assert().isEqualTo("https://idp.example")
        config["clientId"].asString.assert().isEqualTo("dashboard")
        config.getAsJsonArray("scopes").map { it.asString }.assert().containsExactly("openid", "profile")
        config.keySet().assert().containsExactlyInAnyOrder("issuer", "clientId", "scopes")
    }

    @Test
    fun `without a whole OIDC client there is nothing to sign in with`() {
        listOf(
            CoSkyProperties.Oidc(),
            CoSkyProperties.Oidc(issuer = "https://idp.example"),
            CoSkyProperties.Oidc(clientId = "dashboard"),
        ).forEach { oidc ->
            val config = client(authenticated = true, CoSkyProperties(auth = CoSkyProperties.Auth(oidc = oidc)))
                .get().uri("/v1/auth/config").exchange().expectStatus().isOk.json()
            config.keySet().assert().isEmpty()
        }
    }

    @Test
    fun `without a token there is no answer, and with one there is`() = runBlocking<Unit> {
        bootstrapAdmin()
        val client = client(authenticated = true)
        client.get().uri("/v1/me").exchange().expectStatus().isUnauthorized
        client.get().uri(
            "/v1/me"
        ).header("Authorization", bearer(Tokens.generate().plaintext)).exchange().expectStatus().isUnauthorized
        val me = client.get().uri("/v1/me").header("Authorization", bearer()).exchange().expectStatus().isOk.json()
        me.getAsJsonObject("subject")["name"].asString.assert().isEqualTo("root")
        me.getAsJsonArray("rules").size().assert().isGreaterThan(0)
    }

    @Test
    fun `a token is shown once, works, and stops working when revoked`() = runBlocking<Unit> {
        bootstrapAdmin()
        val client = client(authenticated = true)
        val created = client.post().uri("/v1/serviceaccounts/root/tokens").header("Authorization", bearer()).exchange()
            .expectStatus().isOk.json()
        val token = created["token"].asString
        val id = created["id"].asString
        token.assert().startsWith("cosky_$id")

        caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null && it.revision >= 2 }
        client.get().uri("/v1/me").header("Authorization", bearer(token)).exchange().expectStatus().isOk

        client.delete().uri(
            "/v1/serviceaccounts/root/tokens/$id"
        ).header("Authorization", bearer()).exchange().expectStatus().isNoContent
        caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null && it.revision >= 3 }
        client.get().uri("/v1/me").header("Authorization", bearer(token)).exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `an account holds at most two valid tokens`() = runBlocking<Unit> {
        bootstrapAdmin()
        val client = client(authenticated = true)
        val uri = "/v1/serviceaccounts/root/tokens"
        client.post().uri(uri).header("Authorization", bearer()).exchange().expectStatus().isOk
        caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null && it.revision >= 2 }
        client.post().uri(uri).header("Authorization", bearer()).exchange().expectStatus().isBadRequest
    }

    @Test
    fun `an expiry must be a time, and a past one does not count`() = runBlocking<Unit> {
        bootstrapAdmin()
        val client = client(authenticated = true)
        val uri = "/v1/serviceaccounts/root/tokens"
        client.post().uri(uri).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expiresAt":"soon"}""").exchange().expectStatus().isBadRequest
        val created = client.post().uri(uri).header("Authorization", bearer()).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"expiresAt":"2000-01-01T00:00:00Z"}""").exchange().expectStatus().isOk.json()
        caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null && it.revision >= 2 }
        client.get().uri(
            "/v1/me"
        ).header("Authorization", bearer(created["token"].asString)).exchange().expectStatus().isUnauthorized
        client.post().uri(uri).header("Authorization", bearer()).exchange().expectStatus().isOk
    }

    private companion object {
        const val NAMESPACE = "prod"
    }
}
