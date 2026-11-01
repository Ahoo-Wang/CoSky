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

package me.ahoo.cosky.server.resource

import kotlinx.coroutines.flow.firstOrNull
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.server.CoSkyProperties
import me.ahoo.cosky.server.auth.AuthBootstrap
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store

/**
 * What an empty store starts with: the system namespace, `default` and the first admin. This runs at every start, and
 * again if the store turns up empty while the server runs (design §7.2): nothing else would bring it back.
 */
class StoreBootstrap(private val store: Store, private val auth: CoSkyProperties.Auth) {
    suspend fun run() {
        createInitialNamespaces()
        if (auth.enabled) {
            AuthBootstrap(store).run(auth.bootstrapToken)
        }
    }

    /**
     * `default` exists from the first start on, but it stays deleted if someone deletes it.
     */
    private suspend fun createInitialNamespaces() {
        store.createNamespace(NamingRules.SYSTEM_NAMESPACE)
        if (store.readAll(NamingRules.SYSTEM_NAMESPACE).firstOrNull { it.hasNamespace() } != null) {
            return
        }
        store.createNamespace(DEFAULT_NAMESPACE)
        val namespace = resource {
            meta = objectMeta {
                namespace = NamingRules.SYSTEM_NAMESPACE
                name = DEFAULT_NAMESPACE
            }
            this.namespace = namespaceSpec {}
        }
        store.apply(NamingRules.SYSTEM_NAMESPACE, listOf(Mutation.Put(namespace)), ACTOR)
    }

    private companion object {
        const val DEFAULT_NAMESPACE = "default"
        const val ACTOR = "system"
    }
}
