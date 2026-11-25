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

package me.ahoo.cosky.server.view

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ServiceSummary
import me.ahoo.cosky.api.v1.ServicesRequest
import me.ahoo.cosky.api.v1.ServicesResponse
import me.ahoo.cosky.api.v1.StatsRequest
import me.ahoo.cosky.api.v1.StatsResponse
import me.ahoo.cosky.api.v1.TopologyEdge
import me.ahoo.cosky.api.v1.TopologyRequest
import me.ahoo.cosky.api.v1.TopologyResponse
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.resource.PageTokens
import me.ahoo.cosky.server.resource.ServicesPageToken
import me.ahoo.cosky.server.resource.TopologyPageToken
import me.ahoo.cosky.server.watch.NamespaceView
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.server.watch.chunkedByBytes
import me.ahoo.cosky.store.Store
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Read-only views derived from the Watch Cache, computed when asked for: they serve a dashboard, not a hot path.
 * The topology comes from the store, since each replica only sees its own watchers (design D30).
 *
 * Services and the topology page as a List does (design §4.6), by bytes always, and by count when asked to.
 */
class ViewService(
    private val store: Store,
    private val caches: WatchCache,
    private val authorizer: Authorizer = Authorizer.ALLOW_ALL,
    private val timeout: Duration = 3.seconds,
    private val limits: Limits = Limits.DEFAULT,
) {
    private val validator = Validator(limits)

    /**
     * Later pages read no earlier than the first, whose revision every page reports, as for a List.
     */
    suspend fun services(request: ServicesRequest, caller: Caller): ServicesResponse {
        requireValid(validator.validate(request))
        requireWholeNamespace(request.namespace, caller)
        val token = PageTokens.services(request.pageToken)
        val view = viewFor(request.namespace, token?.epoch, token?.revision)
        val after = token?.after.orEmpty()
        val summaries = view.instanceCounts()
            .filterKeys { it > after }
            .toSortedMap()
            .map { (service, count) -> ServiceSummary.newBuilder().setService(service).setInstanceCount(count).build() }
        val (page, more) = pageOf(summaries, request.pageSize) { it.serializedSize }
        val revision = token?.revision ?: view.revision
        return ServicesResponse.newBuilder()
            .addAllServices(page)
            .setRevision(revision)
            .setNextPageToken(
                if (more) PageTokens.encode(ServicesPageToken(view.epoch, revision, page.last().service)) else "",
            )
            .build()
    }

    /**
     * The edges are soft state, read anew for each page: a later page goes on after the last edge of the one before.
     */
    suspend fun topology(request: TopologyRequest, caller: Caller): TopologyResponse {
        requireValid(validator.validate(request))
        requireWholeNamespace(request.namespace, caller)
        val after = PageTokens.topology(request.pageToken)?.let { edgeOf(it.consumer, it.producer) }
        val edges = store.topology(request.namespace)
            .sortedWith(EDGE_ORDER)
            .filter { after == null || EDGE_ORDER.compare(it, after) > 0 }
        val (page, more) = pageOf(edges, request.pageSize) { it.serializedSize }
        return TopologyResponse.newBuilder()
            .addAllEdges(page)
            .setNextPageToken(
                if (more) PageTokens.encode(TopologyPageToken(page.last().consumer, page.last().producer)) else "",
            )
            .build()
    }

    suspend fun stats(request: StatsRequest, caller: Caller): StatsResponse {
        requireValid(validator.validate(request))
        requireWholeNamespace(request.namespace, caller)
        val view = caches.viewAt(request.namespace, 0, timeout)
        val instances = view.instanceCounts()
        return StatsResponse.newBuilder()
            .setServiceCount(instances.size)
            .setInstanceCount(instances.values.sum())
            .setConfigCount(view.objects.keys.count { it.kind == Kind.KIND_CONFIG })
            .setRevision(view.revision)
            .build()
    }

    /**
     * A view for a page: for a later one no earlier than the first, on the same timeline, or the token is stale.
     */
    private suspend fun viewFor(namespace: String, epoch: String?, revision: Long?): NamespaceView {
        val reached = caches.viewAt(namespace, 0, timeout)
        val view = if (epoch == reached.epoch && revision != null && revision > reached.revision) {
            caches.viewAt(namespace, revision, timeout)
        } else {
            reached
        }
        if (epoch != null && epoch != view.epoch) {
            throw PageTokens.invalid("The timeline changed since this page was requested; start from the first page.")
        }
        return view
    }

    /**
     * The first page of [items]: at most [pageSize] of them, any number for 0, within the size cap; and whether more
     * follow.
     */
    private fun <T> pageOf(items: List<T>, pageSize: Int, size: (T) -> Int): Pair<List<T>, Boolean> {
        val counted = if (pageSize > 0) items.take(pageSize) else items
        val page = counted.chunkedByBytes(limits.maxMessageBytes, size).firstOrNull().orEmpty()
        return page to (items.size > page.size)
    }

    /**
     * A view covers everything, so a rule limited to some names is not enough (design §7.8).
     */
    private fun requireWholeNamespace(namespace: String, caller: Caller) {
        val permission = authorizer.permission(caller.principal, Verb.VERB_GET, Kind.KIND_INSTANCE, namespace)
        if (permission != Permission.All) {
            throw CoSkyException.permissionDenied(
                "${caller.principal.id} may not read every Instance of namespace $namespace.",
            )
        }
    }

    private companion object {
        val EDGE_ORDER: Comparator<TopologyEdge> = compareBy({ it.consumer }, { it.producer })

        fun edgeOf(consumer: String, producer: String): TopologyEdge =
            TopologyEdge.newBuilder().setConsumer(consumer).setProducer(producer).build()
    }
}
