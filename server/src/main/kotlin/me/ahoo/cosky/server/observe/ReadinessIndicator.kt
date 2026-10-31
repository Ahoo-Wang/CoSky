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

package me.ahoo.cosky.server.observe

import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.watch.WatchCache
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator

/**
 * Ready once every namespace has loaded (design §7.9): a replica taking traffic before that could only answer that it
 * is loading. Asking also starts the loading of namespaces nobody has asked for yet.
 */
class ReadinessIndicator(private val caches: WatchCache) : HealthIndicator {
    override fun health(): Health {
        val system = caches.of(NamingRules.SYSTEM_NAMESPACE).views.value
            ?: return Health.outOfService().withDetail(LOADING, listOf(NamingRules.SYSTEM_NAMESPACE)).build()
        val loading = system.objects.values
            .filter { it.kind == Kind.KIND_NAMESPACE }
            .map { it.meta.name }
            .filter { caches.of(it).views.value == null }
        return if (loading.isEmpty()) {
            Health.up().build()
        } else {
            Health.outOfService().withDetail(LOADING, loading).build()
        }
    }

    private companion object {
        const val LOADING = "loading"
    }
}
