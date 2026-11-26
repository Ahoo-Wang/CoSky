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

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration

/**
 * The last CoSky 6.x, as its image runs, on the Redis that [network] calls `redis`. It is driven through its own REST
 * API, signed in as the super user it creates on its first start.
 */
internal class SixServer(network: Network) : AutoCloseable {
    private val container = Container()
        .withNetwork(network)
        .withEnv("SPRING_DATA_REDIS_URL", "redis://$REDIS_ALIAS:$REDIS_PORT")
        .withEnv("COSKY_SECURITY_KEY", SECURITY_KEY)
        .withExposedPorts(HTTP_PORT)
        .waitingFor(Wait.forHttp("/actuator/health").forPort(HTTP_PORT).withStartupTimeout(Duration.ofMinutes(2)))
        .also { it.start() }
    private val url = "http://${container.host}:${container.getMappedPort(HTTP_PORT)}/v1"
    private val http = HttpClient.newHttpClient()
    private val token: String = signIn()

    private class Container : GenericContainer<Container>("ahoowang/cosky:6.2.0")

    private fun signIn(): String {
        val password = checkNotNull(SUPER_USER.find(container.logs)) { "6.x printed no super user." }.groupValues[1]
        val response = send("POST", "/authenticate/cosky/login", """{"password":"$password"}""", signedIn = false)
        return JsonParser.parseString(response).asJsonObject["accessToken"].asString
    }

    private fun send(
        method: String,
        path: String,
        body: String? = null,
        type: String = JSON,
        signedIn: Boolean = true,
    ): String {
        val request = HttpRequest.newBuilder(URI("$url$path"))
            .method(method, body?.let { BodyPublishers.ofString(it) } ?: BodyPublishers.noBody())
            .header("Content-Type", type)
            .timeout(REQUEST_TIMEOUT)
        if (signedIn) {
            request.header("Authorization", "Bearer $token")
        }
        val response: HttpResponse<String> = http.send(request.build(), BodyHandlers.ofString())
        check(response.statusCode() == HTTP_OK) { "6.x answered $method $path with ${response.statusCode()}." }
        return response.body()
    }

    private fun array(path: String): JsonArray = JsonParser.parseString(send("GET", path)).asJsonArray

    /** A segment of a path: 6.x advises names such as `cosky-{shop}`. */
    private fun segment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    private fun configPath(namespace: String, name: String) =
        "/namespaces/${segment(namespace)}/configs/${segment(name)}"

    fun createNamespace(namespace: String) {
        send("PUT", "/namespaces/${segment(namespace)}")
    }

    fun setConfig(namespace: String, name: String, data: String) {
        send("PUT", configPath(namespace, name), data, TEXT)
    }

    fun removeConfig(namespace: String, name: String) {
        send("DELETE", configPath(namespace, name))
    }

    fun rollback(namespace: String, name: String, version: Int) {
        send("PUT", "${configPath(namespace, name)}/to/$version")
    }

    fun register(namespace: String, service: String, host: String, ephemeral: Boolean, metadata: String = "{}") {
        val instance = """{"schema":"http","host":"$host","port":8080,"weight":3,"isEphemeral":$ephemeral,""" +
            """"metadata":$metadata}"""
        send("PUT", "/namespaces/${segment(namespace)}/services/${segment(service)}/instances", instance)
    }

    fun saveRole(name: String, description: String, namespace: String, action: String) {
        val role = """{"desc":"$description","resourceActionBind":[{"namespace":"$namespace","action":"$action"}]}"""
        send("PUT", "/roles/${segment(name)}", role)
    }

    fun addUser(name: String, password: String, role: String) {
        send("POST", "/users/${segment(name)}", """{"password":"$password"}""")
        send("PATCH", "/users/${segment(name)}/role", """["$role"]""")
    }

    fun configs(namespace: String): List<String> =
        array("/namespaces/${segment(namespace)}/configs").map { it.asString }.sorted()

    fun services(namespace: String): List<String> =
        array("/namespaces/${segment(namespace)}/services").map { it.asString }.sorted()

    /**
     * Every content 6.x holds for a Config, as its API tells them: the versions it keeps, oldest first, then the
     * current one.
     */
    fun versions(namespace: String, name: String): List<String> {
        val kept = array("${configPath(namespace, name)}/versions").map { it.asJsonObject["version"].asInt }.sorted()
            .map { JsonParser.parseString(send("GET", "${configPath(namespace, name)}/versions/$it")).asJsonObject }
        val current = JsonParser.parseString(send("GET", configPath(namespace, name))).asJsonObject
        return (kept + current).map { it["data"].asString }
    }

    override fun close() {
        container.stop()
    }

    companion object {
        const val REDIS_ALIAS = "redis"
        const val REDIS_PORT = 6379
        private const val HTTP_PORT = 8080
        private const val HTTP_OK = 200
        private const val JSON = "application/json"
        private const val TEXT = "text/plain"

        // Its JWT key, at least 256 bits as HMAC-SHA256 takes.
        private const val SECURITY_KEY = "c2l4LWtleS1mb3ItdGhlLW1pZ3JhdGlvbi10ZXN0LTEyMzQ1Ng=="
        private val SUPER_USER = Regex("""init super user:\[cosky] password:\[([^]]+)]""")

        /** Generous: 6.x answers slowly while a busy CI runner warms it up. */
        private val REQUEST_TIMEOUT = Duration.ofSeconds(60)
    }
}
