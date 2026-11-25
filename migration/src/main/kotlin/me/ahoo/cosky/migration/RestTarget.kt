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
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import me.ahoo.cosky.api.v1.HistoryResponse
import me.ahoo.cosky.api.v1.PutResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.resource
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Nothing else can succeed either: the server is away, refuses this token, or is not what answers. Nothing retries,
 * because running the tool again continues from here.
 */
internal class TargetStoppedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * The server refused this one object.
 */
internal class TargetRejectedException(message: String) : RuntimeException(message)

/**
 * The object changed on the server since it was read.
 */
internal class RevisionConflictException(message: String) : RuntimeException(message)

/**
 * The REST API of the 8.0 server, used as anyone else would (design §4.5).
 */
internal class RestTarget(private val baseUrl: String, private val token: String?) {
    private val client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(CONNECT_TIMEOUT)
        .build()
    private val printer = JsonFormat.printer().omittingInsignificantWhitespace()
    private val parser = JsonFormat.parser().ignoringUnknownFields()

    init {
        val scheme = runCatching { URI.create(baseUrl).scheme }.getOrNull()
        if (scheme != "http" && scheme != "https") {
            throw UsageException("--target must be an http or https address, not $baseUrl.")
        }
    }

    fun namespaceExists(name: String): Boolean = send(get(namespacePath(name))).statusCode() != NOT_FOUND

    fun createNamespace(name: String, description: String) {
        val namespace = resource { this.namespace = namespaceSpec { this.description = description } }
        val response = send(put(namespacePath(name), namespace).header(IF_NONE_MATCH, ANY))
        // A conflict means someone else created it just now, which is just as good.
        if (response.statusCode() != CONFLICT && !isSuccess(response)) {
            throw TargetStoppedException("$baseUrl refused namespace $name: ${messageOf(response)}")
        }
    }

    /**
     * @return null when it does not exist, nor perhaps its namespace.
     */
    fun config(namespace: String, name: String): Resource? {
        val response = send(get(configPath(namespace, name)))
        if (response.statusCode() == NOT_FOUND) {
            return null
        }
        if (!isSuccess(response)) {
            throw TargetRejectedException(messageOf(response))
        }
        return read(response, Resource.newBuilder()).build()
    }

    /**
     * Every version 8.0 still keeps, oldest first.
     */
    fun history(namespace: String, name: String): List<Resource> {
        val newestFirst = mutableListOf<Resource>()
        var pageToken = ""
        do {
            val response = send(get("${configPath(namespace, name)}/history?pageToken=$pageToken"))
            if (!isSuccess(response)) {
                throw TargetRejectedException(messageOf(response))
            }
            val page = read(response, HistoryResponse.newBuilder())
            newestFirst += page.revisionsList
            pageToken = page.nextPageToken
        } while (pageToken.isNotEmpty())
        return newestFirst.asReversed()
    }

    /**
     * @param expectedRevision the revision it must still have, or null if it must not exist yet.
     * @return the revision it has now.
     */
    fun putConfig(namespace: String, name: String, config: Resource, expectedRevision: Long?): Long {
        val request = put(configPath(namespace, name), config)
        if (expectedRevision == null) {
            request.header(IF_NONE_MATCH, ANY)
        } else {
            request.header(IF_MATCH, "\"$expectedRevision\"")
        }
        val response = send(request)
        if (response.statusCode() == CONFLICT) {
            throw RevisionConflictException(messageOf(response))
        }
        if (!isSuccess(response)) {
            throw TargetRejectedException(messageOf(response))
        }
        return read(response, PutResponse.newBuilder()).revision
    }

    private fun namespacePath(name: String) = "/v1/namespaces/$name"

    // A name is one path segment (design §4.5). A validated name holds nothing else that needs encoding.
    private fun configPath(namespace: String, name: String) =
        "/v1/namespaces/$namespace/configs/${name.replace("/", "%2F")}"

    private fun get(path: String) = request(path).GET()

    private fun put(path: String, message: Message) = request(path)
        .header("Content-Type", "application/json")
        .PUT(HttpRequest.BodyPublishers.ofString(printer.print(message)))

    private fun request(path: String) = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(REQUEST_TIMEOUT)

    private fun send(request: HttpRequest.Builder): HttpResponse<String> {
        token?.let { request.header("Authorization", "Bearer $it") }
        val response = try {
            client.send(request.build(), HttpResponse.BodyHandlers.ofString())
        } catch (@Suppress("SwallowedException") e: IOException) {
            throw TargetStoppedException("Cannot reach $baseUrl: ${e.message ?: e.javaClass.simpleName}")
        }
        val status = response.statusCode()
        val problem = when {
            status == UNAUTHORIZED || status == FORBIDDEN -> "refused this token"
            status >= SERVER_ERROR -> "cannot serve this now"
            else -> return response
        }
        throw TargetStoppedException("$baseUrl $problem: ${messageOf(response)}")
    }

    private fun isSuccess(response: HttpResponse<String>) = response.statusCode() in FIRST_SUCCESS..LAST_SUCCESS

    /**
     * A successful answer that is not the JSON of a CoSky message, such as the sign-in page of a gateway in front of
     * the server, means that nothing else will work either.
     */
    private fun <B : Message.Builder> read(response: HttpResponse<String>, builder: B): B {
        try {
            parser.merge(response.body(), builder)
        } catch (e: InvalidProtocolBufferException) {
            throw TargetStoppedException(
                "$baseUrl answered ${response.request().method()} ${response.uri().rawPath} with HTTP " +
                    "${response.statusCode()}, but not as CoSky does: ${bodyOf(response)}",
                e,
            )
        }
        return builder
    }

    /** What a body is and how it begins, never a page of it. */
    private fun bodyOf(response: HttpResponse<String>): String {
        val body = response.body()
        val type = response.headers().firstValue("Content-Type").orElse("no Content-Type")
        if (body.isBlank()) {
            return "an empty body, $type"
        }
        // A code span, so that the report shows markup as it came; its own backticks would end the span.
        val beginning = body.take(EXCERPT_LENGTH).replace(WHITESPACE, " ").filterNot { it == '`' || it.isISOControl() }
        val more = if (body.length > EXCERPT_LENGTH) "..." else ""
        return "$type, ${body.length} characters beginning `${beginning.trim()}$more`"
    }

    /** What the problem details (RFC 9457) say happened, else their title. */
    private fun messageOf(response: HttpResponse<String>): String =
        runCatching {
            val problem = JsonParser.parseString(response.body()).asJsonObject
            (problem["detail"] ?: problem["title"]).asString
        }.getOrDefault("HTTP ${response.statusCode()}")

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(30)
        const val IF_MATCH = "If-Match"
        const val IF_NONE_MATCH = "If-None-Match"
        const val ANY = "*"
        const val FIRST_SUCCESS = 200
        const val LAST_SUCCESS = 299
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val CONFLICT = 409
        const val SERVER_ERROR = 500
        const val EXCERPT_LENGTH = 80
        val WHITESPACE = Regex("\\s+")
    }
}
