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
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ServiceSummary
import me.ahoo.cosky.api.v1.ServicesRequest
import me.ahoo.cosky.api.v1.ServicesResponse
import me.ahoo.cosky.api.v1.StatsRequest
import me.ahoo.cosky.api.v1.StatsResponse
import me.ahoo.cosky.api.v1.TopologyRequest
import me.ahoo.cosky.api.v1.TopologyResponse
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.validation.Validator
import me.ahoo.cosky.api.validation.requireValid
import me.ahoo.cosky.server.auth.Authorizer
import me.ahoo.cosky.server.auth.Caller
import me.ahoo.cosky.server.auth.Permission
import me.ahoo.cosky.server.watch.WatchCache
import me.ahoo.cosky.store.Store
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Read-only views derived from the Watch Cache, computed when asked for: they serve a dashboard, not a hot path.
 * The topology comes from the store, since each replica only sees its own watchers (design D30).
 */
class ViewService(
    private val store: Store,
    private val caches: WatchCache,
    private val authorizer: Authorizer = Authorizer.ALLOW_ALL,
    private val timeout: Duration = 3.seconds,
) {
    suspend fun services(request: ServicesRequest, caller: Caller): ServicesResponse {
        requireValid(Validator.DEFAULT.validate(request))
        requireWholeNamespace(request.namespace, caller)
        val view = caches.viewAt(request.namespace, 0, timeout)
        val summaries = instancesOf(view.objects.values).groupingBy { it.instance.service }.eachCount()
            .toSortedMap()
            .map { (service, count) -> ServiceSummary.newBuilder().setService(service).setInstanceCount(count).build() }
        return ServicesResponse.newBuilder().addAllServices(summaries).setRevision(view.revision).build()
    }

    suspend fun topology(request: TopologyRequest, caller: Caller): TopologyResponse {
        requireValid(Validator.DEFAULT.validate(request))
        requireWholeNamespace(request.namespace, caller)
        val edges = store.topology(request.namespace).sortedWith(compareBy({ it.consumer }, { it.producer }))
        return TopologyResponse.newBuilder().addAllEdges(edges).build()
    }

    suspend fun stats(request: StatsRequest, caller: Caller): StatsResponse {
        requireValid(Validator.DEFAULT.validate(request))
        requireWholeNamespace(request.namespace, caller)
        val view = caches.viewAt(request.namespace, 0, timeout)
        val instances = instancesOf(view.objects.values)
        return StatsResponse.newBuilder()
            .setServiceCount(instances.distinctBy { it.instance.service }.size)
            .setInstanceCount(instances.size)
            .setConfigCount(view.objects.keys.count { it.kind == Kind.KIND_CONFIG })
            .setRevision(view.revision)
            .build()
    }

    private fun instancesOf(objects: Collection<Resource>): List<Resource> = objects.filter { it.hasInstance() }

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
}
