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

package me.ahoo.cosky.server

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.CoSkyClientOptions
import me.ahoo.cosky.client.ConnectionState
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * Both ports over TLS, with a certificate made for this test: a token should not cross a network in the clear.
 */
class TlsEndToEndTest {
    private fun options(tls: Boolean, trusted: Path? = null) = CoSkyClientOptions(
        endpoints = listOf("localhost:$GRPC_PORT"),
        snapshotDir = null,
        tls = tls,
        tlsTrustedCertificates = trusted,
    )

    private fun syncs(options: CoSkyClientOptions): Boolean = runBlocking {
        CoSkyClient(options).use { client ->
            client.namespace("default").configs.watch("absent")
            withTimeoutOrNull(FAILURE_WINDOW) {
                client.connectionState.first { it == ConnectionState.SYNCED }
            } != null
        }
    }

    @Test
    fun `a client that trusts the certificate connects`() = runBlocking<Unit> {
        CoSkyClient(options(tls = true, trusted = CERTIFICATE)).use { client ->
            withTimeout(SUCCESS_WINDOW) {
                client.namespace("default").configs.load("absent").assert().isNull()
                client.connectionState.first { it == ConnectionState.SYNCED }
            }
        }
    }

    @Test
    fun `a client that does not trust the certificate does not`() {
        syncs(options(tls = true)).assert().isFalse()
    }

    @Test
    fun `a client without TLS does not`() {
        syncs(options(tls = false)).assert().isFalse()
    }

    @Test
    fun `the REST port answers over TLS, to whoever trusts the certificate`() {
        val request = HttpRequest.newBuilder(URI("https://localhost:$HTTP_PORT/v1/auth/config")).build()

        val response = HttpClient.newBuilder().sslContext(trusting(CERTIFICATE)).build()
            .send(request, HttpResponse.BodyHandlers.ofString())

        response.statusCode().assert().isEqualTo(HTTP_OK)
    }

    private fun trusting(certificate: Path): SSLContext {
        val trusted = Files.newInputStream(certificate).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("cosky", trusted)
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(store) }
            .trustManagers
        return SSLContext.getInstance("TLS").apply { init(null, trustManagers, null) }
    }

    companion object {
        private const val PASSWORD = "changeit"
        private const val HTTP_OK = 200
        private val SUCCESS_WINDOW = 30.seconds
        private val FAILURE_WINDOW = 3.seconds
        private val HTTP_PORT = ServerSocket(0).use { it.localPort }
        private val GRPC_PORT = ServerSocket(0).use { it.localPort }
        private val DIRECTORY: Path = Files.createTempDirectory("cosky-tls")
        private val KEYSTORE: Path = DIRECTORY.resolve("server.p12")
        private val CERTIFICATE: Path = DIRECTORY.resolve("server.pem").also { makeCertificate(it) }

        private fun keytool(vararg arguments: String) {
            val tool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString()
            val process = ProcessBuilder(tool, *arguments).redirectErrorStream(true).start()
            process.inputStream.readAllBytes()
            check(process.waitFor() == 0) { "keytool ${arguments.first()} failed." }
        }

        private fun makeCertificate(pem: Path) {
            keytool(
                "-genkeypair", "-alias", "cosky", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost",
                "-storetype", "PKCS12", "-keystore", KEYSTORE.toString(), "-storepass", PASSWORD,
            )
            keytool(
                "-exportcert", "-rfc", "-alias", "cosky", "-file", pem.toString(),
                "-keystore", KEYSTORE.toString(), "-storepass", PASSWORD,
            )
        }

        private val SERVER = RunningServer(
            mapOf(
                "server.port" to HTTP_PORT,
                "cosky.store.type" to CoSkyProperties.MEMORY,
                "cosky.auth.enabled" to "false",
                "spring.ssl.bundle.jks.cosky.keystore.location" to "file:$KEYSTORE",
                "spring.ssl.bundle.jks.cosky.keystore.password" to PASSWORD,
                "spring.ssl.bundle.jks.cosky.keystore.type" to "PKCS12",
                "server.ssl.bundle" to "cosky",
                "spring.grpc.server.ssl.bundle" to "cosky",
            ),
            port = GRPC_PORT,
        )

        @JvmStatic
        @AfterAll
        fun stop() {
            SERVER.close()
        }
    }
}
