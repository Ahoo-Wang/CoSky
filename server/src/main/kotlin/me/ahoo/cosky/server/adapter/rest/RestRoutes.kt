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

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.protobuf.Message
import io.lettuce.core.RedisException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.HistoryRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.ServicesRequest
import me.ahoo.cosky.api.v1.StatsRequest
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TopologyRequest
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.auth.TokenService
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.view.ViewService
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.server.watch.WatchSession
import me.ahoo.cosky.store.Mutation
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.CoRouterFunctionDsl
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.awaitBody
import org.springframework.web.reactive.function.server.awaitBodyOrNull
import org.springframework.web.reactive.function.server.bodyAndAwait
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.buildAndAwait
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.reactive.function.server.sse
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.jvm.optionals.getOrNull
import kotlin.time.toKotlinDuration

/**
 * The REST API (design §4.5, `api/src/main/openapi/paths.yaml`), protocol mapping only: everything of substance lives
 * in the same services the gRPC adapter uses.
 *
 * @param access null where authentication is off.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One function per operation of the API.
class RestRoutes(
    private val resources: ResourceService,
    private val views: ViewService,
    private val tokens: TokenService,
    private val caches: WatchCache,
    private val authorizer: Authorizer,
    private val access: AccessControl?,
    private val audit: AuditLogger,
    private val properties: CoSkyProperties,
    private val limits: Limits = Limits.DEFAULT,
) {
    fun routes(): RouterFunction<ServerResponse> = coRouter {
        context { VirtualThreads.dispatcher }

        GET("/v1/me", ::me)

        // Colons are part of a segment, so these go before the routes that would take them as a name.
        POST("/v1/namespaces/{namespace}/configs:import", ::importConfigs)
        GET("/v1/namespaces/{namespace}/configs:export", ::exportConfigs)
        POST("/v1/namespaces/{namespace}/configs/{name}:rollback", ::rollback)
        GET("/v1/namespaces/{namespace}/configs/{name}/history", ::history)
        scoped("/v1/namespaces/{namespace}/configs", Kind.KIND_CONFIG)
        scoped("/v1/namespaces/{namespace}/instances", Kind.KIND_INSTANCE)

        GET("/v1/namespaces/{namespace}/watch", ::watch)
        GET("/v1/namespaces/{namespace}/views/services", ::viewServices)
        GET("/v1/namespaces/{namespace}/views/topology", ::viewTopology)
        GET("/v1/namespaces/{namespace}/views/stats", ::viewStats)

        POST("/v1/serviceaccounts/{name}/tokens", ::createToken)
        DELETE("/v1/serviceaccounts/{name}/tokens/{tokenId}", ::revokeToken)
        global("/v1/namespaces", Kind.KIND_NAMESPACE)
        global("/v1/serviceaccounts", Kind.KIND_SERVICE_ACCOUNT)
        global("/v1/roles", Kind.KIND_ROLE)
        global("/v1/rolebindings", Kind.KIND_ROLE_BINDING)

        onError<CoSkyException> { error, _ -> errorResponse(error as CoSkyException) }
        onError<RedisException> { error, _ ->
            errorResponse(CoSkyException.storeUnavailable("The store is unavailable.", error))
        }
        // Filters run outside the error handlers above, so this one has to answer its own failures.
        filter { request, next ->
            val signedIn = try {
                authenticated(request)
            } catch (e: CoSkyException) {
                return@filter errorResponse(e)
            }
            next(signedIn)
        }
    }

    /**
     * The objects of a kind in a user namespace.
     */
    private fun CoRouterFunctionDsl.scoped(path: String, kind: Kind) {
        crud(path, kind) { it.pathVariable(NAMESPACE) }
    }

    /**
     * The objects of a kind that lives in `_system`.
     */
    private fun CoRouterFunctionDsl.global(path: String, kind: Kind) {
        crud(path, kind) { NamingRules.SYSTEM_NAMESPACE }
    }

    private fun CoRouterFunctionDsl.crud(path: String, kind: Kind, namespaceOf: (ServerRequest) -> String) {
        GET(path) { list(it, kind, namespaceOf(it)) }
        GET("$path/{name}") { get(it, keyOf(it, kind, namespaceOf(it))) }
        PUT("$path/{name}") { put(it, keyOf(it, kind, namespaceOf(it))) }
        DELETE("$path/{name}") { delete(it, keyOf(it, kind, namespaceOf(it))) }
    }

    private fun keyOf(request: ServerRequest, kind: Kind, namespace: String): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(namespace)
        .setKind(kind)
        .setName(request.pathVariable(NAME))
        .build()

    private fun authenticated(request: ServerRequest): ServerRequest {
        if (access == null) {
            return request
        }
        val remote = request.remoteAddress().getOrNull()?.toString()
        val principal = try {
            access.authenticate(request.headers().firstHeader(HttpHeaders.AUTHORIZATION)?.removePrefix(BEARER))
        } catch (e: CoSkyException) {
            val path = request.path()
            audit.record(UNKNOWN_SUBJECT, null, null, null, path, null, e.errorCode.grpcCode.name, remote)
            throw e
        }
        request.attributes()[CALLER] = Caller(principal, remote)
        return request
    }

    private fun callerOf(request: ServerRequest): Caller = request.attributes()[CALLER] as Caller? ?: Caller.ANONYMOUS

    // --- resources ---

    private suspend fun get(request: ServerRequest, key: ResourceKey): ServerResponse {
        val get = GetRequest.newBuilder().setKey(key).setMinRevision(longParam(request, MIN_REVISION) ?: 0)
        val resource = resources.get(get.build(), callerOf(request))
        return ServerResponse.ok().eTag(eTagOf(resource.meta.revision)).json(resource)
    }

    private suspend fun list(request: ServerRequest, kind: Kind, namespace: String): ServerResponse {
        val list = ListRequest.newBuilder()
            .setNamespace(namespace)
            .setKind(kind)
            .setMinRevision(longParam(request, MIN_REVISION) ?: 0)
            .setPageSize((longParam(request, "pageSize") ?: 0).toInt())
            .setPageToken(request.queryParam("pageToken").getOrNull().orEmpty())
            .setService(request.queryParam("service").getOrNull().orEmpty())
            .putAllLabelSelector(labelSelector(request))
        return ServerResponse.ok().json(resources.list(list.build(), callerOf(request)))
    }

    private fun labelSelector(request: ServerRequest): Map<String, String> =
        request.queryParam("labelSelector").getOrNull().orEmpty()
            .split(',')
            .filter { it.isNotEmpty() }
            .associate { condition ->
                if ('=' !in condition) {
                    throw ProtoJson.invalid("labelSelector", "Conditions look like key=value.")
                }
                condition.substringBefore('=') to condition.substringAfter('=')
            }

    private suspend fun put(request: ServerRequest, key: ResourceKey): ServerResponse {
        val resource = ProtoJson.parse(request.awaitBody<String>(), Resource.newBuilder())
        resource.metaBuilder.setNamespace(key.namespace).setName(key.name)
        if (resource.build().kind != key.kind) {
            throw ProtoJson.invalidBody("The body must hold the spec of ${key.kind.name.removePrefix("KIND_")}.")
        }
        val put = PutRequest.newBuilder().setResource(resource).setExpectedRevision(expectedRevisionOf(request))
        val response = resources.put(put.build(), callerOf(request))
        val builder = ServerResponse.ok()
        // Writing the same content creates no revision: there is no new entity tag to report.
        if (response.changed) {
            builder.eTag(eTagOf(response.revision))
        }
        return builder.json(response)
    }

    private suspend fun delete(request: ServerRequest, key: ResourceKey): ServerResponse {
        val delete = DeleteRequest.newBuilder().setKey(key).setExpectedRevision(expectedRevisionOf(request))
        return ServerResponse.ok().json(resources.delete(delete.build(), callerOf(request)))
    }

    private suspend fun history(request: ServerRequest): ServerResponse {
        val history = HistoryRequest.newBuilder()
            .setKey(keyOf(request, Kind.KIND_CONFIG, request.pathVariable(NAMESPACE)))
            .setLimit((longParam(request, "limit") ?: 0).toInt())
            .setPageToken(request.queryParam("pageToken").getOrNull().orEmpty())
        return ServerResponse.ok().json(resources.history(history.build(), callerOf(request)))
    }

    private suspend fun rollback(request: ServerRequest): ServerResponse {
        val body = ProtoJson.objectOf(request.awaitBody<String>())
        val toRevision = body["toRevision"]?.asString?.toLongOrNull()
            ?: throw ProtoJson.invalid("toRevision", "A revision from the history is required.")
        val response = resources.rollback(
            keyOf(request, Kind.KIND_CONFIG, request.pathVariable(NAMESPACE)),
            toRevision,
            expectedRevisionOf(request),
            callerOf(request),
        )
        return ServerResponse.ok().eTag(eTagOf(response.revision)).json(response)
    }

    private suspend fun importConfigs(request: ServerRequest): ServerResponse {
        val namespace = request.pathVariable(NAMESPACE)
        val items = JsonArray()
        ConfigArchive.read(request.awaitBody<ByteArray>(), limits.maxConfigContentBytes).forEach { (name, content) ->
            val put = PutRequest.newBuilder().setResource(configOf(namespace, name, content)).build()
            val response = resources.put(put, callerOf(request))
            items.add(
                JsonObject().apply {
                    addProperty("name", name)
                    addProperty("revision", response.revision.toString())
                    addProperty("changed", response.changed)
                },
            )
        }
        val body = JsonObject().apply { add("items", items) }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(body.toString())
    }

    private fun configOf(namespace: String, name: String, content: ByteArray): Resource =
        Resource.newBuilder().apply {
            metaBuilder.setNamespace(namespace).setName(name)
            config = configSpec {
                format = ConfigArchive.formatOf(name)
                this.content = content.decodeToString()
            }
        }.build()

    private suspend fun exportConfigs(request: ServerRequest): ServerResponse {
        val namespace = request.pathVariable(NAMESPACE)
        val caller = callerOf(request)
        // Fails here, as a status, rather than halfway through the archive.
        resources.list(listOfConfigs(namespace, "").build(), caller)
        return ServerResponse.ok()
            .contentType(ZIP)
            .bodyAndAwait(ConfigArchive.write(allConfigs(namespace, caller)))
    }

    private fun listOfConfigs(namespace: String, pageToken: String): ListRequest.Builder =
        ListRequest.newBuilder().setNamespace(namespace).setKind(Kind.KIND_CONFIG).setPageToken(pageToken)

    private fun allConfigs(namespace: String, caller: Caller): Flow<Resource> = channelFlow {
        var token = ""
        do {
            val page = resources.list(listOfConfigs(namespace, token).build(), caller)
            page.itemsList.forEach { send(it) }
            token = page.nextPageToken
        } while (token.isNotEmpty())
    }

    // --- watch ---

    private suspend fun watch(request: ServerRequest): ServerResponse {
        val namespace = request.pathVariable(NAMESPACE)
        val kind = request.queryParam("kind").getOrNull()
            ?.let { runCatching { Kind.valueOf(it) }.getOrNull() }
            ?: throw ProtoJson.invalid("kind", "A kind is required, like KIND_CONFIG.")
        val create = WatchRequest.Create.newBuilder()
            .setWatchId(0)
            .setNamespace(namespace)
            .setKind(kind)
            .setName(request.queryParam(NAME).getOrNull().orEmpty())
            .setService(request.queryParam("service").getOrNull().orEmpty())
            .setFromRevision(longParam(request, "fromRevision") ?: 0)
            .setFromEpoch(request.queryParam("fromEpoch").getOrNull().orEmpty())
            .build()
        requireValid(Validator(limits).validate(WatchRequest.newBuilder().setCreate(create).build()))
        val principal = callerOf(request).principal
        val permission = authorizer.permission(principal, Verb.VERB_WATCH, kind, namespace)
        if (permission == Permission.None) {
            throw CoSkyException.permissionDenied("${principal.id} may not watch this.")
        }
        val events = responses(create, principal, permission).map { ProtoJson.print(it) }
        return ServerResponse.ok().sse().bodyAndAwait(events)
    }

    private fun responses(
        create: WatchRequest.Create,
        principal: Principal,
        permission: Permission,
    ): Flow<WatchResponse> = channelFlow {
        val session = WatchSession(
            create,
            caches.of(create.namespace),
            limits.maxMessageBytes,
            properties.read.minRevisionTimeout.toKotlinDuration(),
            permission,
        ) { send(it) }
        val bookmarks = launch {
            while (true) {
                delay(properties.watch.progressInterval.toKotlinDuration())
                send(WatchSession.progress(create.watchId, session.cursor.get()))
            }
        }
        val reevaluating = launch { reevaluate(create, principal, permission) }
        session.run()
        bookmarks.cancel()
        reevaluating.cancel()
    }

    /**
     * As for a gRPC watch, but with only one watch a change simply ends the stream, after saying why.
     */
    private suspend fun ProducerScope<WatchResponse>.reevaluate(
        create: WatchRequest.Create,
        principal: Principal,
        permission: Permission,
    ) {
        authorizer.changes.collect {
            val now = authorizer.permission(principal, Verb.VERB_WATCH, create.kind, create.namespace)
            if (!authorizer.isValid(principal) || now != permission) {
                send(
                    if (now == Permission.None || !authorizer.isValid(principal)) {
                        WatchSession.canceled(create.watchId, CoSkyException.permissionDenied("Access was withdrawn."))
                    } else {
                        WatchSession.gone(create.watchId, 0)
                    },
                )
                close()
            }
        }
    }

    // --- views ---

    private suspend fun viewServices(request: ServerRequest): ServerResponse {
        val view = ServicesRequest.newBuilder().setNamespace(request.pathVariable(NAMESPACE)).build()
        return ServerResponse.ok().json(views.services(view, callerOf(request)))
    }

    private suspend fun viewTopology(request: ServerRequest): ServerResponse {
        val view = TopologyRequest.newBuilder().setNamespace(request.pathVariable(NAMESPACE)).build()
        return ServerResponse.ok().json(views.topology(view, callerOf(request)))
    }

    private suspend fun viewStats(request: ServerRequest): ServerResponse {
        val view = StatsRequest.newBuilder().setNamespace(request.pathVariable(NAMESPACE)).build()
        return ServerResponse.ok().json(views.stats(view, callerOf(request)))
    }

    // --- tokens and identity ---

    private suspend fun createToken(request: ServerRequest): ServerResponse {
        val body = request.awaitBodyOrNull<String>()?.takeIf { it.isNotBlank() }?.let { ProtoJson.objectOf(it) }
        val expiresAt = body?.get("expiresAt")?.asString?.let { value ->
            try {
                Instant.parse(value)
            } catch (e: DateTimeParseException) {
                throw ProtoJson.invalid("expiresAt", e.message.orEmpty())
            }
        }
        val created = tokens.create(request.pathVariable(NAME), expiresAt, callerOf(request))
        val response = JsonObject().apply {
            addProperty("id", created.token.id)
            addProperty("token", created.token.plaintext)
            created.expiresAt?.let { addProperty("expiresAt", it.toString()) }
        }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(response.toString())
    }

    private suspend fun revokeToken(request: ServerRequest): ServerResponse {
        tokens.revoke(request.pathVariable(NAME), request.pathVariable("tokenId"), callerOf(request))
        return ServerResponse.noContent().buildAndAwait()
    }

    private suspend fun me(request: ServerRequest): ServerResponse {
        val principal = callerOf(request).principal
        val subject = Subject.newBuilder().setType(principal.type).setName(principal.name).build()
        val response = JsonObject().apply {
            add("subject", ProtoJson.tree(subject))
            add("groups", JsonArray().apply { principal.groups.forEach { add(it) } })
            add(
                "rules",
                JsonArray().apply { access?.rulesOf(principal).orEmpty().forEach { add(ProtoJson.tree(it)) } },
            )
        }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(response.toString())
    }

    // --- mapping ---

    private fun eTagOf(revision: Long): String = "\"$revision\""

    private fun expectedRevisionOf(request: ServerRequest): Long {
        if (request.headers().firstHeader(HttpHeaders.IF_NONE_MATCH) == "*") {
            return Mutation.MUST_NOT_EXIST
        }
        val ifMatch = request.headers().firstHeader(HttpHeaders.IF_MATCH) ?: return Mutation.NO_CHECK
        return ifMatch.trim('"').toLongOrNull()?.takeIf { it > 0 }
            ?: throw ProtoJson.invalid("If-Match", "An entity tag looks like \"42\".")
    }

    private fun longParam(request: ServerRequest, name: String): Long? {
        val value = request.queryParam(name).getOrNull() ?: return null
        return value.toLongOrNull() ?: throw ProtoJson.invalid(name, "This must be a whole number.")
    }

    private suspend fun ServerResponse.BodyBuilder.json(message: Message): ServerResponse =
        contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(ProtoJson.print(message))

    private suspend fun errorResponse(error: CoSkyException): ServerResponse {
        val body = JsonObject().apply {
            addProperty("status", error.errorCode.grpcCode.name)
            addProperty("message", error.message)
            add("detail", ProtoJson.tree(error.detail))
        }
        return ServerResponse.status(HttpStatus.valueOf(error.errorCode.httpStatus))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(body.toString())
    }

    private companion object {
        const val NAMESPACE = "namespace"
        const val NAME = "name"
        const val MIN_REVISION = "minRevision"
        const val BEARER = "Bearer "
        const val UNKNOWN_SUBJECT = "unknown"
        const val CALLER = "cosky.caller"
        val ZIP = MediaType.parseMediaType("application/zip")
    }
}
