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
import com.google.protobuf.util.Timestamps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.CreateTokenRequest
import me.ahoo.cosky.api.v1.CreateTokenResponse
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.HistoryRequest
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListRequest
import me.ahoo.cosky.api.v1.ObjectMeta
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.PutResponse
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.ServicesRequest
import me.ahoo.cosky.api.v1.StatsRequest
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.TopologyRequest
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchResponse
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.api.validation.Violation
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.audit.AuditLogger
import me.ahoo.cosky.server.auth.AccessControl
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.auth.Principal
import me.ahoo.cosky.server.auth.TokenService
import me.ahoo.cosky.server.auth.bearerCredentials
import me.ahoo.cosky.server.observe.AuthFailure
import me.ahoo.cosky.server.observe.CoSkyMetrics
import me.ahoo.cosky.server.resource.ResourceService
import me.ahoo.cosky.server.view.ViewService
import me.ahoo.cosky.server.watch.WatchService
import me.ahoo.cosky.server.watch.WatchSession
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.StoreUnavailableException
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
import org.springframework.web.reactive.function.server.coRouter
import org.springframework.web.reactive.function.server.sse
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

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
    private val watches: WatchService,
    private val access: AccessControl?,
    private val audit: AuditLogger,
    private val oidc: CoSkyProperties.Oidc = CoSkyProperties.Oidc(),
    private val metrics: CoSkyMetrics = CoSkyMetrics(),
    private val limits: Limits = Limits.DEFAULT,
) {
    fun routes(): RouterFunction<ServerResponse> = publicRoutes().and(signedInRoutes())

    /**
     * What the sign-in page needs before anyone has signed in (design D34).
     */
    private fun publicRoutes(): RouterFunction<ServerResponse> = coRouter {
        context { VirtualThreads.dispatcher }

        GET("/v1/auth/config") { authConfig() }
    }

    private fun signedInRoutes(): RouterFunction<ServerResponse> = coRouter {
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
        DELETE("/v1/serviceaccounts/{name}/tokens/{$TOKEN_ID}", ::revokeToken)
        global("/v1/namespaces", Kind.KIND_NAMESPACE)
        global("/v1/serviceaccounts", Kind.KIND_SERVICE_ACCOUNT)
        global("/v1/roles", Kind.KIND_ROLE)
        global("/v1/rolebindings", Kind.KIND_ROLE_BINDING)

        onError<CoSkyException> { error, request -> errorResponse(error as CoSkyException, request) }
        onError<StoreUnavailableException> { error, request ->
            errorResponse(CoSkyException.storeUnavailable("The store is unavailable.", error), request)
        }
        // Filters run outside the error handlers above, so this one has to answer its own failures.
        filter { request, next ->
            val signedIn = try {
                authenticated(request)
            } catch (e: CoSkyException) {
                return@filter errorResponse(e, request)
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
        val credentials = bearerCredentials(request.headers().firstHeader(HttpHeaders.AUTHORIZATION))
        val principal = try {
            access.authenticate(credentials)
        } catch (e: CoSkyException) {
            metrics.authFailure(AuthFailure.of(credentials, e))
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
        val get = GetRequest.newBuilder()
            .setKey(key)
            .setMinRevision(longParam(request, MIN_REVISION, MIN_REVISION_FIELD) ?: 0)
        val resource = resources.get(get.build(), callerOf(request))
        return ServerResponse.ok().eTag(eTagOf(resource.meta.revision)).json(resource)
    }

    private suspend fun list(request: ServerRequest, kind: Kind, namespace: String): ServerResponse {
        val list = ListRequest.newBuilder()
            .setNamespace(namespace)
            .setKind(kind)
            .setMinRevision(longParam(request, MIN_REVISION, MIN_REVISION_FIELD) ?: 0)
            .setPageSize(intParam(request, PAGE_SIZE, PAGE_SIZE_FIELD) ?: 0)
            .setPageToken(request.queryParam(PAGE_TOKEN).getOrNull().orEmpty())
            .setService(request.queryParam(SERVICE).getOrNull().orEmpty())
            .putAllLabelSelector(labelSelector(request))
        return ServerResponse.ok().json(resources.list(list.build(), callerOf(request)))
    }

    /**
     * One condition to a parameter, which repeats for more: a label value may hold a comma, so nothing is split.
     */
    private fun labelSelector(request: ServerRequest): Map<String, String> {
        val selector = LinkedHashMap<String, String>()
        request.queryParams()[LABEL_SELECTOR].orEmpty().forEach { condition ->
            val equals = condition?.indexOf('=') ?: -1
            if (equals <= 0) {
                throw ProtoJson.invalid(
                    LABEL_SELECTOR_FIELD,
                    "A condition looks like key=value, one to each labelSelector parameter.",
                )
            }
            val label = condition.substring(0, equals)
            if (selector.put(label, condition.substring(equals + 1)) != null) {
                throw ProtoJson.invalid(LABEL_SELECTOR_FIELD, "Label $label is selected more than once.")
            }
        }
        return selector
    }

    private suspend fun put(request: ServerRequest, key: ResourceKey): ServerResponse {
        val expected = expectedRevisionOf(request)
        val resource = ProtoJson.parse(request.awaitBody<String>(), Resource.newBuilder(), RESOURCE, RESOURCE)
        requireSamePlace(resource.meta, key)
        resource.metaBuilder.setNamespace(key.namespace).setName(key.name)
        if (resource.build().kind != key.kind) {
            throw ProtoJson.invalid(RESOURCE, "The body must hold the spec of ${key.kind.name.removePrefix("KIND_")}.")
        }
        val put = PutRequest.newBuilder().setResource(resource).setExpectedRevision(expected)
        return written(resources.put(put.build(), callerOf(request)))
    }

    /**
     * The path names the object: a body may leave its namespace and name out, but not name another.
     */
    private fun requireSamePlace(meta: ObjectMeta, key: ResourceKey) {
        val violations = buildList {
            if (meta.namespace.isNotEmpty() && meta.namespace != key.namespace) {
                add(Violation("$RESOURCE.meta.namespace", NOT_ALLOWED, "The path names namespace ${key.namespace}."))
            }
            if (meta.name.isNotEmpty() && meta.name != key.name) {
                add(Violation("$RESOURCE.meta.name", NOT_ALLOWED, "The path names ${key.name}."))
            }
        }
        requireValid(violations)
    }

    /**
     * The revision is the object's either way (design §4.5): the one a write created, or the one it has already when
     * the write changed nothing, so that it serves as the next entity tag.
     */
    private suspend fun written(response: PutResponse): ServerResponse =
        ServerResponse.ok().eTag(eTagOf(response.revision)).json(response)

    private suspend fun delete(request: ServerRequest, key: ResourceKey): ServerResponse {
        val delete = DeleteRequest.newBuilder().setKey(key).setExpectedRevision(expectedRevisionOf(request))
        return ServerResponse.ok().json(resources.delete(delete.build(), callerOf(request)))
    }

    private suspend fun history(request: ServerRequest): ServerResponse {
        val history = HistoryRequest.newBuilder()
            .setKey(keyOf(request, Kind.KIND_CONFIG, request.pathVariable(NAMESPACE)))
            .setLimit(intParam(request, LIMIT, LIMIT) ?: 0)
            .setPageToken(request.queryParam(PAGE_TOKEN).getOrNull().orEmpty())
        return ServerResponse.ok().json(resources.history(history.build(), callerOf(request)))
    }

    private suspend fun rollback(request: ServerRequest): ServerResponse {
        val expected = expectedRevisionOf(request)
        // The body is there for its revision alone: one that cannot be read is one without it.
        val body = ProtoJson.objectOf(request.awaitBodyOrNull<String>().orEmpty(), TO_REVISION)
        ProtoJson.requireOnly(body, setOf("toRevision"))
        // An int64 is a number or a string in JSON; Gson would read a one-element array as its element.
        val toRevision = body["toRevision"]
            ?.takeIf { it.isJsonPrimitive && (it.asJsonPrimitive.isNumber || it.asJsonPrimitive.isString) }
            ?.asString?.toLongOrNull()
            ?: throw ProtoJson.invalid(TO_REVISION, "A revision from the history is required.")
        val response = resources.rollback(
            keyOf(request, Kind.KIND_CONFIG, request.pathVariable(NAMESPACE)),
            toRevision,
            expected,
            callerOf(request),
        )
        // The earlier content may be what the Config holds already: like any such write, it then changes nothing.
        return written(response)
    }

    private suspend fun importConfigs(request: ServerRequest): ServerResponse {
        val namespace = request.pathVariable(NAMESPACE)
        val caller = callerOf(request)
        val archive = request.awaitBody<ByteArray>()
        // Everything that can be told before writing: an entry that cannot be read, a name, who may write it.
        ConfigArchive.read(archive, limits.maxConfigContentBytes) { entries ->
            entries.forEach { (name, _) -> resources.requireImportable(namespace, name, caller) }
        }
        val items = JsonArray()
        val imported = mutableListOf<String>()
        ConfigArchive.read(archive, limits.maxConfigContentBytes) { entries ->
            entries.forEach { (name, content) ->
                val response = try {
                    resources.importConfig(namespace, name, content, caller)
                } catch (e: CoSkyException) {
                    throw importedBefore(imported, name, e)
                } catch (e: StoreUnavailableException) {
                    throw importedBefore(
                        imported,
                        name,
                        CoSkyException.storeUnavailable("The store is unavailable.", e)
                    )
                }
                imported += name
                items.add(
                    JsonObject().apply {
                        addProperty("name", name)
                        addProperty("revision", response.revision.toString())
                        addProperty("changed", response.changed)
                    },
                )
            }
        }
        val body = JsonObject().apply { add("items", items) }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(body.toString())
    }

    /**
     * What a write that failed halfway through an archive, as one that lost a race does, left written: the client
     * cannot tell otherwise.
     */
    private fun importedBefore(imported: List<String>, name: String, failure: CoSkyException): CoSkyException =
        if (imported.isEmpty()) {
            failure
        } else {
            val names = imported.joinToString(limit = IMPORTED_NAMED) { it }
            CoSkyException(
                failure.detail,
                "Imported ${imported.size} Configs ($names) before $name, which failed: ${failure.message}",
                failure,
            )
        }

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
        val kind = request.queryParam("kind").getOrNull()
            ?.let { runCatching { Kind.valueOf(it) }.getOrNull() }
            ?: throw ProtoJson.invalid("create.kind", "A kind is required, like KIND_CONFIG.")
        val create = WatchRequest.Create.newBuilder()
            .setWatchId(0)
            .setNamespace(request.pathVariable(NAMESPACE))
            .setKind(kind)
            .setName(request.queryParam(NAME).getOrNull().orEmpty())
            .setService(request.queryParam(SERVICE).getOrNull().orEmpty())
            .setFromRevision(longParam(request, "fromRevision", "create.from_revision") ?: 0)
            .setFromEpoch(request.queryParam("fromEpoch").getOrNull().orEmpty())
            .build()
        val principal = callerOf(request).principal
        // Refused here, as a status, rather than in the first event.
        val permission = watches.permissionOf(principal, create)
        val events = responses(create, principal, permission).map { ProtoJson.print(it) }
        return ServerResponse.ok().sse().bodyAndAwait(events)
    }

    /**
     * One watch to a stream, so a change of what the subject may see simply ends the stream, after saying why.
     */
    private fun responses(
        create: WatchRequest.Create,
        principal: Principal,
        permission: Permission,
    ): Flow<WatchResponse> = channelFlow {
        val stream = watches.stream(principal, null, this) { send(it) }
        val watch = stream.start(create, permission)
        val maintaining = launch {
            try {
                stream.maintain()
            } catch (e: CoSkyException) {
                send(WatchSession.canceled(create.watchId, e))
                watch.cancel()
            }
        }
        watch.join()
        maintaining.cancel()
    }

    // --- views ---

    private suspend fun viewServices(request: ServerRequest): ServerResponse {
        val view = ServicesRequest.newBuilder()
            .setNamespace(request.pathVariable(NAMESPACE))
            .setPageSize(intParam(request, PAGE_SIZE, PAGE_SIZE_FIELD) ?: 0)
            .setPageToken(request.queryParam(PAGE_TOKEN).getOrNull().orEmpty())
            .build()
        return ServerResponse.ok().json(views.services(view, callerOf(request)))
    }

    private suspend fun viewTopology(request: ServerRequest): ServerResponse {
        val view = TopologyRequest.newBuilder()
            .setNamespace(request.pathVariable(NAMESPACE))
            .setPageSize(intParam(request, PAGE_SIZE, PAGE_SIZE_FIELD) ?: 0)
            .setPageToken(request.queryParam(PAGE_TOKEN).getOrNull().orEmpty())
            .build()
        return ServerResponse.ok().json(views.topology(view, callerOf(request)))
    }

    private suspend fun viewStats(request: ServerRequest): ServerResponse {
        val view = StatsRequest.newBuilder().setNamespace(request.pathVariable(NAMESPACE)).build()
        return ServerResponse.ok().json(views.stats(view, callerOf(request)))
    }

    // --- tokens and identity ---

    /**
     * The body may be left out, for a token that does not expire; one that cannot be read is about its expiry, the only
     * thing it holds.
     */
    private suspend fun createToken(request: ServerRequest): ServerResponse {
        val expected = accountRevisionOf(request)
        val body = request.awaitBodyOrNull<String>()?.takeIf { it.isNotBlank() }
            ?.let { ProtoJson.parse(it, CreateTokenRequest.newBuilder(), EXPIRES_AT, "").build() }
        val expiresAt = body?.takeIf { it.hasExpiresAt() }?.expiresAt
            ?.let { Instant.ofEpochSecond(it.seconds, it.nanos.toLong()) }
        val created = tokens.create(request.pathVariable(NAME), expiresAt, callerOf(request), expected)
        val response = CreateTokenResponse.newBuilder()
            .setId(created.token.id)
            .setToken(created.token.plaintext)
            .setRevision(created.revision)
        created.expiresAt?.let { response.setExpiresAt(Timestamps.fromMillis(it.toEpochMilli())) }
        return ServerResponse.ok().json(response.build())
    }

    private suspend fun revokeToken(request: ServerRequest): ServerResponse {
        val expected = accountRevisionOf(request)
        val account = request.pathVariable(NAME)
        val revoked = tokens.revoke(account, request.pathVariable(TOKEN_ID), callerOf(request), expected)
        return ServerResponse.ok().json(revoked)
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

    private suspend fun authConfig(): ServerResponse {
        val response = JsonObject()
        if (oidc.issuer != null && oidc.clientId != null) {
            response.add(
                "oidc",
                JsonObject().apply {
                    addProperty("issuer", oidc.issuer)
                    addProperty("clientId", oidc.clientId)
                    add("scopes", JsonArray().apply { oidc.scopes.forEach { add(it) } })
                },
            )
        }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(response.toString())
    }

    // --- mapping ---

    private fun eTagOf(revision: Long): String = "\"$revision\""

    /**
     * `If-Match: "<revision>"` compares, `If-None-Match: *` creates only; they exclude one another, and
     * `If-None-Match` takes nothing else (design §4.5).
     */
    private fun expectedRevisionOf(request: ServerRequest): Long {
        val ifNoneMatch = request.headers().header(HttpHeaders.IF_NONE_MATCH)
        if (ifNoneMatch.isEmpty()) {
            return ifMatchOf(request)
        }
        if (request.headers().header(HttpHeaders.IF_MATCH).isNotEmpty()) {
            throw notAllowed("If-Match and If-None-Match exclude one another: send one of them.")
        }
        if (ifNoneMatch.singleOrNull()?.trim() != "*") {
            throw ProtoJson.invalid(EXPECTED_REVISION, "If-None-Match takes * alone, to create only.")
        }
        return Mutation.MUST_NOT_EXIST
    }

    /**
     * The revision a ServiceAccount must still be at, for a change of its tokens: one to compare with, or none.
     */
    private fun accountRevisionOf(request: ServerRequest): Long {
        if (request.headers().header(HttpHeaders.IF_NONE_MATCH).isNotEmpty()) {
            throw notAllowed("The account exists already: If-Match takes its revision.")
        }
        return ifMatchOf(request)
    }

    private fun ifMatchOf(request: ServerRequest): Long {
        val ifMatch = request.headers().header(HttpHeaders.IF_MATCH)
        if (ifMatch.isEmpty()) {
            return Mutation.NO_CHECK
        }
        return ifMatch.singleOrNull()?.trim()?.removeSurrounding("\"")?.toLongOrNull()?.takeIf { it > 0 }
            ?: throw ProtoJson.invalid(EXPECTED_REVISION, "If-Match takes one entity tag, like \"42\".")
    }

    private fun notAllowed(description: String): CoSkyException =
        CoSkyException.invalidArgument(listOf(Violation(EXPECTED_REVISION, NOT_ALLOWED, description)))

    /**
     * @param field the request's field it sets, which a violation names, as over gRPC.
     */
    private fun longParam(request: ServerRequest, name: String, field: String): Long? {
        val value = request.queryParam(name).getOrNull() ?: return null
        return value.toLongOrNull() ?: throw ProtoJson.invalid(field, "$name must be a whole number.")
    }

    /**
     * For a field of 32 bits: a number beyond them is refused, rather than cut down to one that may pass for another.
     */
    private fun intParam(request: ServerRequest, name: String, field: String): Int? {
        val value = longParam(request, name, field) ?: return null
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw ProtoJson.outOfRange(field, "$name must be within [${Int.MIN_VALUE}, ${Int.MAX_VALUE}].")
        }
        return value.toInt()
    }

    private suspend fun ServerResponse.BodyBuilder.json(message: Message): ServerResponse =
        contentType(MediaType.APPLICATION_JSON).bodyValueAndAwait(ProtoJson.print(message))

    private suspend fun errorResponse(error: CoSkyException, request: ServerRequest): ServerResponse =
        ServerResponse.status(HttpStatus.valueOf(error.errorCode.httpStatus))
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyValueAndAwait(Problems.of(error, request.path()).toString())

    private companion object {
        const val NAMESPACE = "namespace"
        const val NAME = "name"
        const val SERVICE = "service"
        const val MIN_REVISION = "minRevision"
        const val PAGE_SIZE = "pageSize"
        const val PAGE_TOKEN = "pageToken"
        const val LIMIT = "limit"
        const val LABEL_SELECTOR = "labelSelector"

        const val TOKEN_ID = "tokenId"

        // What a violation calls a parameter, a header or a body: the field of the request it sets (design §4.4).
        const val MIN_REVISION_FIELD = "min_revision"
        const val PAGE_SIZE_FIELD = "page_size"
        const val LABEL_SELECTOR_FIELD = "label_selector"
        const val EXPECTED_REVISION = "expected_revision"
        const val RESOURCE = "resource"
        const val TO_REVISION = "to_revision"
        const val EXPIRES_AT = "expires_at"
        val NOT_ALLOWED = ViolationCode.VIOLATION_CODE_NOT_ALLOWED

        const val UNKNOWN_SUBJECT = "unknown"
        const val CALLER = "cosky.caller"

        /** Of the Configs an import wrote before it failed, how many the error names; it says how many there were. */
        const val IMPORTED_NAMED = 20
        val ZIP = MediaType.parseMediaType("application/zip")
    }
}
