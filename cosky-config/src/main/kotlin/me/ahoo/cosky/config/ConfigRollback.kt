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
package me.ahoo.cosky.config

import me.ahoo.cosky.core.NamespacedContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Config Rollback.
 *
 * @author ahoo wang
 */
interface ConfigRollback {
    fun rollback(configId: String, targetVersion: Int): Mono<Boolean> {
        return rollback(NamespacedContext.namespace, configId, targetVersion)
    }

    fun rollback(namespace: String, configId: String, targetVersion: Int): Mono<Boolean>
    fun getConfigVersions(configId: String): Flux<ConfigVersion> {
        return getConfigVersions(NamespacedContext.namespace, configId)
    }

    fun getConfigVersions(namespace: String, configId: String): Flux<ConfigVersion>
    fun getConfigHistory(configId: String, version: Int): Mono<ConfigHistory> {
        return getConfigHistory(NamespacedContext.namespace, configId, version)
    }

    fun getConfigHistory(namespace: String, configId: String, version: Int): Mono<ConfigHistory>

    companion object {
        const val HISTORY_SIZE = 10
    }
}
