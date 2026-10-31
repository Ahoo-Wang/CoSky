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

package me.ahoo.cosky.spring.config

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.spring.CoSkyClients
import me.ahoo.cosky.spring.CoSkyProperties
import org.springframework.boot.context.config.ConfigData
import org.springframework.boot.context.config.ConfigDataLoader
import org.springframework.boot.context.config.ConfigDataLoaderContext
import org.springframework.boot.context.config.ConfigDataLocation
import org.springframework.boot.context.config.ConfigDataLocationResolver
import org.springframework.boot.context.config.ConfigDataLocationResolverContext
import org.springframework.boot.context.config.ConfigDataResource
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.env.PropertiesPropertySourceLoader
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ByteArrayResource

private const val PREFIX = "cosky:"

/**
 * One Config named by `spring.config.import=cosky:<name>` (design §7.7).
 */
data class CoSkyConfigDataResource(val config: String, val properties: CoSkyProperties) : ConfigDataResource()

class CoSkyConfigDataLocationResolver : ConfigDataLocationResolver<CoSkyConfigDataResource> {
    override fun isResolvable(context: ConfigDataLocationResolverContext, location: ConfigDataLocation): Boolean =
        location.hasPrefix(PREFIX)

    override fun resolve(
        context: ConfigDataLocationResolverContext,
        location: ConfigDataLocation,
    ): List<CoSkyConfigDataResource> {
        val properties = context.binder.bindOrCreate(CoSkyProperties.PREFIX, Bindable.of(CoSkyProperties::class.java))
        return listOf(CoSkyConfigDataResource(location.getNonPrefixedValue(PREFIX), properties))
    }
}

/**
 * Loading blocks startup, so it runs on a virtual thread and gives up after a timeout. A Config that is not there, or
 * cannot be known without a server and a snapshot, is an error unless the location is `optional:` (design §7.5).
 */
class CoSkyConfigDataLoader : ConfigDataLoader<CoSkyConfigDataResource> {
    override fun load(context: ConfigDataLoaderContext, resource: CoSkyConfigDataResource): ConfigData {
        val properties = resource.properties
        val namespace = CoSkyClients.shared(properties).namespace(properties.namespace)
        val config = runBlocking(VirtualThreads.dispatcher) {
            withTimeoutOrNull(properties.configLoadTimeout) { namespace.configs.load(resource.config) }
        } ?: throw ConfigDataResourceNotFoundException(resource)
        CoSkyClients.markImported(properties, resource.config)
        return ConfigData(parse(resource.config, config))
    }

    private fun parse(name: String, config: Resource): List<PropertySource<*>> {
        val content = ByteArrayResource(config.config.contentBytes.toByteArray())
        val sourceName = "$PREFIX$name"
        return when (config.config.format) {
            ConfigFormat.CONFIG_FORMAT_PROPERTIES -> PropertiesPropertySourceLoader().load(sourceName, content)
            // JSON is a subset of YAML, so one parser serves both.
            ConfigFormat.CONFIG_FORMAT_YAML, ConfigFormat.CONFIG_FORMAT_JSON ->
                YamlPropertySourceLoader().load(sourceName, content)

            else -> listOf(MapPropertySource(sourceName, mapOf(name to config.config.content)))
        }
    }
}
