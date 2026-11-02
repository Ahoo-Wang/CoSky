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

import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.client.CoSkyClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Configuration loads before the context exists and runs again on every refresh, while the context needs the same
 * client afterwards. So the client is shared here by what connects it, and closed once with the context. Other settings
 * may well differ between the two: an imported Config can hold them too.
 */
internal object CoSkyClients {
    private data class Connection(
        val endpoints: List<String>,
        val token: String?,
        val tls: CoSkyProperties.Tls,
        val snapshot: CoSkyProperties.Snapshot,
    )

    private data class Imports(val connection: Connection, val namespace: String)

    private val CoSkyProperties.connection: Connection
        get() = Connection(endpoints, token, tls, snapshot)

    private val CoSkyProperties.imports: Imports
        get() = Imports(connection, namespace)

    private val clients = ConcurrentHashMap<Connection, CoSkyClient>()
    private val imported = ConcurrentHashMap<Imports, MutableMap<String, Resource>>()
    private val sources = ConcurrentHashMap<Pair<Imports, String>, Set<String>>()

    /**
     * @param applicationName `spring.application.name`, which names this client in the server's topology view.
     */
    fun shared(properties: CoSkyProperties, applicationName: String?): CoSkyClient =
        clients.computeIfAbsent(properties.connection) { CoSkyClient(properties.toOptions(applicationName)) }

    /**
     * Remembers a Config this application loaded and what it saw, so that a change to it can trigger a refresh once the
     * context exists.
     */
    fun markImported(properties: CoSkyProperties, config: String, seen: Resource?) {
        imported.computeIfAbsent(properties.imports) { ConcurrentHashMap() }[config] = seen.orNone()
    }

    fun importedBy(properties: CoSkyProperties): Map<String, Resource> = imported[properties.imports].orEmpty()

    /**
     * Remembers the property sources a Config makes up now.
     *
     * @return those it made up before.
     */
    fun replaceSources(properties: CoSkyProperties, config: String, names: Set<String>): Set<String> =
        sources.put(properties.imports to config, names).orEmpty()

    fun close(properties: CoSkyProperties) {
        imported.keys.removeIf { it.connection == properties.connection }
        sources.keys.removeIf { (imports, _) -> imports.connection == properties.connection }
        clients.remove(properties.connection)?.close()
    }
}

/**
 * Something to compare a Config with, whether or not it exists.
 */
internal fun Resource?.orNone(): Resource = this ?: Resource.getDefaultInstance()
