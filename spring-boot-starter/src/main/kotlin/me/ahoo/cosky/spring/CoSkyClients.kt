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

package me.ahoo.cosky.spring

import me.ahoo.cosky.client.CoSkyClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Configuration loads before the context exists and runs again on every refresh, while the context needs the same
 * client afterwards. So the client is shared here by its settings, and closed once with the context.
 */
internal object CoSkyClients {
    private val clients = ConcurrentHashMap<CoSkyProperties, CoSkyClient>()
    private val imported = ConcurrentHashMap<CoSkyProperties, MutableSet<String>>()

    fun shared(properties: CoSkyProperties): CoSkyClient =
        clients.computeIfAbsent(properties) { CoSkyClient(it.toOptions()) }

    /**
     * Remembers a Config this application loaded, so that a change to it can trigger a refresh.
     */
    fun markImported(properties: CoSkyProperties, config: String) {
        imported.computeIfAbsent(properties) { ConcurrentHashMap.newKeySet() }.add(config)
    }

    fun importedBy(properties: CoSkyProperties): Set<String> = imported[properties].orEmpty()

    fun close(properties: CoSkyProperties) {
        imported.remove(properties)
        clients.remove(properties)?.close()
    }
}
