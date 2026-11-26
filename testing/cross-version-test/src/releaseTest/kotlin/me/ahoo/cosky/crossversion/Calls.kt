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

package me.ahoo.cosky.crossversion

import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import io.grpc.Channel
import io.grpc.ClientInterceptors
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.Metadata
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import me.ahoo.cosky.api.v1.CreateTokenResponse
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.DeleteResponse
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.HistoryRequest
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.KeepAliveResponse
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.PutResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.v1.RevokeTokenResponse
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Calls to one server as one subject, through the release's generated stubs and through REST, which the release's
 * protos map as well. What answers is parsed with the release's messages: fields a later server adds are skipped, as
 * the release's client skips them.
 */
class Calls(private val server: CoSkyServer, private val token: String) : AutoCloseable {
    private val channel: ManagedChannel = ManagedChannelBuilder.forTarget(server.endpoint).usePlaintext().build()
    private val signedIn: Channel = ClientInterceptors.intercept(
        channel,
        MetadataUtils.newAttachHeadersInterceptor(
            Metadata().apply { put(AUTHORIZATION, "Bearer $token") },
        ),
    )

    val grpc: Channel
        get() = signedIn

    val resources = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(signedIn).withWaitForReady()
    val leases = LeaseServiceGrpcKt.LeaseServiceCoroutineStub(signedIn).withWaitForReady()

    suspend fun put(resource: Resource): PutResponse =
        resources.put(PutRequest.newBuilder().setResource(resource).build())

    suspend fun get(key: ResourceKey, minRevision: Long = 0): Resource =
        resources.get(GetRequest.newBuilder().setKey(key).setMinRevision(minRevision).build())

    suspend fun delete(key: ResourceKey): DeleteResponse =
        resources.delete(DeleteRequest.newBuilder().setKey(key).build())

    /**
     * Every page.
     */
    suspend fun list(namespace: String, kind: Kind, minRevision: Long = 0): List<Resource> {
        val items = mutableListOf<Resource>()
        var token = ""
        do {
            val page = resources.list(
                ListRequest.newBuilder()
                    .setNamespace(namespace)
                    .setKind(kind)
                    .setMinRevision(minRevision)
                    .setPageToken(token)
                    .build(),
            )
            items += page.itemsList
            token = page.nextPageToken
        } while (token.isNotEmpty())
        return items
    }

    suspend fun history(key: ResourceKey): List<Resource> =
        resources.history(HistoryRequest.newBuilder().setKey(key).build()).revisionsList

    suspend fun grant(namespace: String, ttl: Duration): Long = leases.grant(
        GrantRequest.newBuilder().setNamespace(namespace).setTtlSeconds(ttl.inWholeSeconds.toInt()).build(),
    ).lease

    /**
     * Renews [lease] [times] times, [every] apart, on one stream, and answers what the server answered.
     */
    suspend fun renew(
        namespace: String,
        lease: Long,
        times: Int,
        every: Duration = 1.seconds,
    ): List<KeepAliveResponse> {
        val request = KeepAliveRequest.newBuilder().setNamespace(namespace).setLease(lease).build()
        val requests = flow {
            repeat(times) {
                emit(request)
                delay(every)
            }
            awaitCancellation()
        }
        return leases.keepAlive(requests).take(times).toList()
    }

    suspend fun renewOnce(namespace: String, lease: Long): KeepAliveResponse {
        val request = KeepAliveRequest.newBuilder().setNamespace(namespace).setLease(lease).build()
        return leases.keepAlive(
            flow {
                emit(request)
                awaitCancellation()
            },
        ).first()
    }

    /** `PUT /v1/namespaces/{ns}/configs/{name}`. */
    fun putConfigOverRest(config: Resource): PutResponse {
        val path = "/v1/namespaces/${config.meta.namespace}/configs/${segment(config.meta.name)}"
        return parse(send("PUT", path, PRINTER.print(config)), PutResponse.newBuilder())
    }

    /** `POST /v1/serviceaccounts/{name}/tokens`. */
    fun createToken(account: String): CreateTokenResponse =
        parse(send("POST", "/v1/serviceaccounts/${segment(account)}/tokens", "{}"), CreateTokenResponse.newBuilder())

    /** `DELETE /v1/serviceaccounts/{name}/tokens/{id}`. */
    fun revokeToken(account: String, id: String): RevokeTokenResponse = parse(
        send("DELETE", "/v1/serviceaccounts/${segment(account)}/tokens/${segment(id)}", null),
        RevokeTokenResponse.newBuilder(),
    )

    private fun send(method: String, path: String, body: String?): HttpResponse<String> {
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        val request = HttpRequest.newBuilder(URI("${server.url}$path"))
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .method(method, publisher)
            .build()
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString())
    }

    override fun close() {
        channel.shutdownNow()
    }

    private companion object {
        val AUTHORIZATION: Metadata.Key<String> = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        val HTTP: HttpClient = HttpClient.newHttpClient()
        val PRINTER: JsonFormat.Printer = JsonFormat.printer()
        val PARSER: JsonFormat.Parser = JsonFormat.parser().ignoringUnknownFields()
        const val HTTP_OK = 200

        fun segment(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

        @Suppress("UNCHECKED_CAST")
        fun <T : Message> parse(response: HttpResponse<String>, builder: Message.Builder): T {
            check(response.statusCode() == HTTP_OK) {
                "${response.request().method()} ${response.uri()} answered ${response.statusCode()}: ${response.body()}"
            }
            PARSER.merge(response.body(), builder)
            return builder.build() as T
        }
    }
}
