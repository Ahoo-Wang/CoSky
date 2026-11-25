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
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.TokenService
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.view.ViewService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.server.watch.WatchService
import me.ahoo.cosky.store.memory.MemoryStore
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * How REST reads a request (design §4.4, §4.5): strictly, with conditions that exclude one another, a label selector
 * of one condition to each parameter, and violations named by the request's proto fields; and what it answers for
 * writes that change nothing, views in pages and the tokens of an account.
 */
class RestRequestsTest {
    private val store = MemoryStore()
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)
    private val caches = WatchCache(store, 64, scope)

    private fun client(
        limits: Limits = Limits.DEFAULT,
        maxArchiveBytes: Int = ConfigArchive.MAX_ARCHIVE_BYTES,
    ): WebTestClient {
        val routes = RestRoutes(
            ResourceService(store, caches),
            ViewService(store, caches),
            TokenService(store, caches),
            WatchService(caches, CoSkyProperties()),
            null,
            AuditLogger(),
            limits = limits,
            maxArchiveBytes = maxArchiveBytes,
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

    private fun WebTestClient.putAccount(name: String) = put()
        .uri("/v1/serviceaccounts/$name")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("""{"serviceAccount":{}}""")
        .exchange()

    private fun WebTestClient.ResponseSpec.json(): JsonObject =
        JsonParser.parseString(expectBody(String::class.java).returnResult().responseBody).asJsonObject

    private fun WebTestClient.ResponseSpec.reason(): String = json()["reason"].asString

    /** The fields of a 400's violations, with their codes. */
    private fun WebTestClient.ResponseSpec.violations(): List<Pair<String, String>> =
        expectStatus().isBadRequest.json().getAsJsonObject("badRequest").getAsJsonArray("violations").map {
            it.asJsonObject["field"].asString to it.asJsonObject["code"].asString
        }

    private fun revisionOf(put: WebTestClient.ResponseSpec): Long = put.expectStatus().isOk.json()["revision"].asLong

    private fun get(client: WebTestClient, name: String, revision: Long) =
        client.get().uri(URI("/v1/namespaces/$NAMESPACE/configs/$name?minRevision=$revision")).exchange()

    @Test
    fun `a body may leave out where it goes, but not name another place than the path`() {
        val client = client()
        client.putConfig(
            "a",
            """{"meta":{"namespace":"elsewhere","name":"b"},"config":{"format":"CONFIG_FORMAT_TEXT","content":"a"}}"""
        ).violations().assert().containsExactly(
            "resource.meta.namespace" to NOT_ALLOWED,
            "resource.meta.name" to NOT_ALLOWED,
        )
        client.get().uri("/v1/namespaces/$NAMESPACE/configs/a").exchange().expectStatus().isNotFound
        client.putConfig(
            "a",
            """{"meta":{"namespace":"$NAMESPACE","name":"a"},"config":{"format":"CONFIG_FORMAT_TEXT","content":"a"}}"""
        ).expectStatus().isOk
        val instance = """{"instance":{"service":"order"}}"""
        client.putConfig("c", instance).violations().assert().containsExactly("resource" to INVALID_FORMAT)
    }

    @Test
    fun `a member the body does not have is refused, where a misspelt one would clear its field`() {
        val client = client()
        val labeled = """{"meta":{"labels":{"team":"x"}},"config":{"format":"CONFIG_FORMAT_TEXT","content":"a"}}"""
        val revision = revisionOf(client.putConfig("a", labeled))
        val misspelt = """{"meta":{"lables":{}},"config":{"format":"CONFIG_FORMAT_TEXT","content":"b","colour":1}}"""
        client.putConfig("a", misspelt).violations().assert().containsExactly(
            "resource.meta.lables" to NOT_ALLOWED,
            "resource.config.colour" to NOT_ALLOWED,
        )
        val role = """{"role":{"rules":[{"kinds":["KIND_CONFIG"]},{"verbz":[]}]}}"""
        client.put().uri("/v1/roles/reader").contentType(MediaType.APPLICATION_JSON).bodyValue(role).exchange()
            .violations().assert().containsExactly("resource.role.rules[1].verbz" to NOT_ALLOWED)
        // Proto names are as good as JSON names, and read-only fields are known, if ignored.
        val roundTrip = """{"meta":{"create_revision":"1","updatedAt":"2026-01-01T00:00:00Z"},""" +
            """"config":{"format":"CONFIG_FORMAT_TEXT","content":"c"}}"""
        client.putConfig("a", roundTrip).expectStatus().isOk
        get(client, "a", revision).expectStatus().isOk.json().getAsJsonObject("meta").has("labels").assert().isFalse()
        client.putConfig("a", "{").violations().assert().containsExactly("resource" to INVALID_FORMAT)
    }

    @Test
    fun `if-none-match takes only a star, and not together with if-match`() {
        val client = client()
        val uri = "/v1/namespaces/$NAMESPACE/configs/a"
        val revision = revisionOf(client.putConfig("a"))
        client.put().uri(uri).contentType(MediaType.APPLICATION_JSON).header("If-None-Match", "\"$revision\"")
            .bodyValue(config).exchange()
            .violations().assert().containsExactly("expected_revision" to INVALID_FORMAT)
        client.put().uri(uri).contentType(MediaType.APPLICATION_JSON)
            .header("If-Match", "\"$revision\"").header("If-None-Match", "*")
            .bodyValue(config).exchange()
            .violations().assert().containsExactly("expected_revision" to NOT_ALLOWED)
        client.delete().uri(uri).header("If-None-Match", "*").header("If-Match", "\"$revision\"").exchange()
            .violations().assert().containsExactly("expected_revision" to NOT_ALLOWED)
    }

    @Test
    fun `writing the same content reports the object's own entity tag, though the namespace has moved on`() {
        val client = client()
        val first = revisionOf(client.putConfig("a"))
        client.putConfig("b").expectStatus().isOk
        val same = client.putConfig("a").expectStatus().isOk.expectHeader().valueEquals("ETag", "\"$first\"")
        val body = same.json()
        body["changed"].let { it == null || !it.asBoolean }.assert().isTrue()
        body["revision"].asLong.assert().isEqualTo(first)
        client.put().uri("/v1/namespaces/$NAMESPACE/configs/a").contentType(MediaType.APPLICATION_JSON)
            .header("If-Match", "\"$first\"").bodyValue(config.replace("\"a\"", "\"b\"")).exchange()
            .expectStatus().isOk
    }

    @Test
    fun `a label selector is one condition to a parameter, which repeats, and a value may hold a comma`() {
        val client = client()
        fun labeled(name: String, labels: String) = client.putConfig(
            name,
            """{"meta":{"labels":{$labels}},"config":{"format":"CONFIG_FORMAT_TEXT","content":"a"}}""",
        )
        labeled("a", """"team":"x,y","zone":"east"""")
        labeled("b", """"team":"x","zone":"east"""")
        val revision = revisionOf(labeled("c", """"team":"x,y","zone":"west""""))
        fun names(query: String) = client.get()
            .uri(URI("/v1/namespaces/$NAMESPACE/configs?minRevision=$revision&$query"))
            .exchange().expectStatus().isOk.json().getAsJsonArray("items")
            .map { it.asJsonObject.getAsJsonObject("meta")["name"].asString }
        names("labelSelector=team%3Dx%2Cy").assert().containsExactly("a", "c")
        names("labelSelector=team%3Dx%2Cy&labelSelector=zone%3Deast").assert().containsExactly("a")
        names("labelSelector=team%3Dx").assert().containsExactly("b")
        listOf("labelSelector=team%3Dx&labelSelector=team%3Dy", "labelSelector=", "labelSelector=%3Dx").forEach {
            client.get().uri(URI("/v1/namespaces/$NAMESPACE/configs?$it")).exchange()
                .violations().assert().containsExactly("label_selector" to INVALID_FORMAT)
        }
    }

    @Test
    fun `views come in pages when asked to`() {
        val client = client()
        listOf("order", "payment").forEach { service ->
            client.put().uri("/v1/namespaces/$NAMESPACE/instances/$service-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                    """{"instance":{"service":"$service",""" +
                        """"endpoint":{"scheme":"http","host":"localhost","port":8080}}}""",
                )
                .exchange()
                .expectStatus().isOk
        }
        runBlocking { caches.of(NAMESPACE).views.first { it != null && it.revision >= 2 } }
        val uri = "/v1/namespaces/$NAMESPACE/views/services"
        val first = client.get().uri("$uri?pageSize=1").exchange().expectStatus().isOk.json()
        first.getAsJsonArray("services").single().asJsonObject["service"].asString.assert().isEqualTo("order")
        val second = client.get().uri("$uri?pageSize=1&pageToken=${first["nextPageToken"].asString}").exchange()
            .expectStatus().isOk.json()
        second.getAsJsonArray("services").single().asJsonObject["service"].asString.assert().isEqualTo("payment")
        second.has("nextPageToken").assert().isFalse()
        client.get().uri("/v1/namespaces/$NAMESPACE/views/topology?pageSize=1&pageToken=nonsense").exchange()
            .violations().assert().containsExactly("page_token" to INVALID_FORMAT)
    }

    @Test
    fun `the global kinds are watched in _system`() {
        val client = client()
        val revision = revisionOf(client.putAccount("app"))
        runBlocking { caches.of(NamingRules.SYSTEM_NAMESPACE).views.first { it != null && it.revision >= revision } }
        val first = client.get()
            .uri("/v1/namespaces/_system/watch?kind=KIND_SERVICE_ACCOUNT")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .returnResult(String::class.java)
            .responseBody.blockFirst()
        JsonParser.parseString(first).asJsonObject.getAsJsonObject("snapshot").getAsJsonArray("items")
            .single().asJsonObject.getAsJsonObject("meta")["name"].asString.assert().isEqualTo("app")
    }

    @Test
    fun `token changes answer the account's revision, and take it back as if-match`() {
        val client = client()
        client.putAccount("app").expectStatus().isOk
        val uri = "/v1/serviceaccounts/app/tokens"
        val created = client.post().uri(uri).exchange().expectStatus().isOk.json()
        val revision = created["revision"].asLong
        client.get().uri("/v1/serviceaccounts/app?minRevision=$revision")
            .exchange().expectStatus().isOk.expectHeader().valueEquals("ETag", "\"$revision\"")

        client.post().uri(uri)
            .header("If-Match", "\"${revision - 1}\"").exchange()
            .expectStatus().isEqualTo(409).reason().assert().isEqualTo("ERROR_REASON_REVISION_CONFLICT")
        client.post().uri(uri).header("If-None-Match", "*").exchange()
            .violations().assert().containsExactly("expected_revision" to NOT_ALLOWED)

        val unknown = client.delete().uri("$uri/unknown")
            .header("If-Match", "\"$revision\"").exchange().expectStatus().isOk.json()
        unknown.has("deleted").assert().isFalse()
        unknown["revision"].asLong.assert().isEqualTo(revision)
        client.delete().uri("$uri/${created["id"].asString}")
            .header("If-Match", "\"${revision + 1}\"").exchange()
            .expectStatus().isEqualTo(409)
        client.delete().uri("$uri/${created["id"].asString}")
            .header("If-Match", "\"$revision\"").exchange()
            .expectStatus().isOk.json()["deleted"].asBoolean.assert().isTrue()
    }

    @Test
    fun `a body larger than the largest resource can take in JSON is refused, also when it does not say how large`() {
        val client = client(SMALL)
        // Each character escaped, as a printer that escapes for HTML writes it: six times the Config, and it fits.
        val escaped = "\\u003c".repeat(SMALL.maxConfigContentBytes)
        client.putConfig("a", """{"config":{"format":"CONFIG_FORMAT_TEXT","content":"$escaped"}}""").expectStatus().isOk
        client.putConfig("b", config.padEnd(MAX_BODY_BYTES)).expectStatus().isOk

        client.putConfig("c", config.padEnd(MAX_BODY_BYTES + 1))
            .violations().assert().containsExactly("resource" to TOO_LONG)
        val streamed = Flux.just(config, " ".repeat(MAX_BODY_BYTES))
        client.put().uri("/v1/namespaces/$NAMESPACE/configs/d").contentType(MediaType.APPLICATION_JSON)
            .body(streamed, String::class.java).exchange()
            .violations().assert().containsExactly("resource" to TOO_LONG)
        client.post().uri("/v1/namespaces/$NAMESPACE/configs/a:rollback").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"toRevision":"1"}""".padEnd(MAX_BODY_BYTES + 1)).exchange()
            .violations().assert().containsExactly("to_revision" to TOO_LONG)
        client.putAccount("app").expectStatus().isOk
        client.post().uri("/v1/serviceaccounts/app/tokens").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{}".padEnd(MAX_BODY_BYTES + 1)).exchange()
            .violations().assert().containsExactly("expires_at" to TOO_LONG)
    }

    @Test
    fun `an archive to import may be larger than any other body, up to its own limit`() {
        val client = client(SMALL, maxArchiveBytes = MAX_ARCHIVE_BYTES)
        val archive = archiveOf(ARCHIVED)
        (archive.size in MAX_BODY_BYTES + 1..MAX_ARCHIVE_BYTES).assert().isTrue()
        client.import(archive).expectStatus().isOk.json().getAsJsonArray("items").size().assert().isEqualTo(ARCHIVED)

        val larger = archiveOf(ARCHIVED * 4)
        (larger.size > MAX_ARCHIVE_BYTES).assert().isTrue()
        client.import(larger).violations().assert().containsExactly("resource" to TOO_LONG)
    }

    private fun WebTestClient.import(archive: ByteArray) = post().uri("/v1/namespaces/$NAMESPACE/configs:import")
        .contentType(MediaType.parseMediaType("application/zip"))
        .bodyValue(archive)
        .exchange()

    /**
     * Configs of [SMALL]'s largest size, of letters and digits that do not compress much.
     */
    private fun archiveOf(configs: Int): ByteArray {
        val random = Random(configs)
        val letters = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            repeat(configs) { index ->
                zip.putNextEntry(ZipEntry("c$index.txt"))
                zip.write(String(CharArray(SMALL.maxConfigContentBytes) { letters.random(random) }).encodeToByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private companion object {
        const val NAMESPACE = "prod"
        const val INVALID_FORMAT = "VIOLATION_CODE_INVALID_FORMAT"
        const val NOT_ALLOWED = "VIOLATION_CODE_NOT_ALLOWED"
        const val TOO_LONG = "VIOLATION_CODE_TOO_LONG"

        /** Resources of 2 KiB at most, so that a body that exceeds what they take is quick to send. */
        val SMALL = Limits(maxConfigContentBytes = 1024, maxMessageBytes = 2048)
        val MAX_BODY_BYTES = SMALL.maxMessageBytes * 6
        const val MAX_ARCHIVE_BYTES = 64 * 1024
        const val ARCHIVED = 20
    }
}
