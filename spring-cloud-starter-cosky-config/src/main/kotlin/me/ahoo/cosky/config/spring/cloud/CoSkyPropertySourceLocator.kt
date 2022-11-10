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
package me.ahoo.cosky.config.spring.cloud

import com.google.common.io.Files
import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigService
import me.ahoo.cosky.core.CoSky
import me.ahoo.cosky.core.NamespacedContext
import org.slf4j.LoggerFactory
import org.springframework.boot.env.OriginTrackedMapPropertySource
import org.springframework.boot.env.PropertySourceLoader
import org.springframework.cloud.bootstrap.config.PropertySourceLocator
import org.springframework.core.env.Environment
import org.springframework.core.env.PropertySource
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.support.SpringFactoriesLoader
import java.util.*

/**
 * CoSky Property Source Locator.
 *
 * @author ahoo wang
 */
class CoSkyPropertySourceLocator(
    private val configProperties: CoSkyConfigProperties,
    private val configService: ConfigService
) : PropertySourceLocator {

    companion object {
        private val log = LoggerFactory.getLogger(CoSkyPropertySourceLocator::class.java)
        fun getNameOfConfigId(configId: String): String {
            return CoSky.COSKY + ":" + configId
        }
    }

    private val propertySourceLoaders: List<PropertySourceLoader> = SpringFactoriesLoader
        .loadFactories(PropertySourceLoader::class.java, CoSkyPropertySourceLocator::class.java.classLoader)

    override fun locate(environment: Environment): PropertySource<*> {
        val configId = requireNotNull(configProperties.configId) { "configId must not be null." }
        var fileExt = Files.getFileExtension(configId)
        if (fileExt.isBlank()) {
            fileExt = configProperties.fileExtension
        }
        val namespace = NamespacedContext.namespace
        if (log.isInfoEnabled) {
            log.info("Locate - configId:[{}] @ namespace:[{}]", configId, namespace)
        }
        val config = configService.getConfig(configId).block(configProperties.timeout)
        if (config == null) {
            log.warn(
                "locate - can not find configId:[{}] @ namespace:[{}]",
                configId,
                namespace
            )
            return OriginTrackedMapPropertySource(
                getNameOfConfigId(configId),
                emptyMap<Any, Any>()
            )
        }
        val sourceLoader = ensureSourceLoader(fileExt)
        return getCoSkyPropertySourceOfConfig(sourceLoader, config)
    }

    private fun ensureSourceLoader(fileExtension: String): PropertySourceLoader {
        val sourceLoaderOptional = propertySourceLoaders
            .stream()
            .filter { propertySourceLoader: PropertySourceLoader ->
                Arrays.stream(propertySourceLoader.fileExtensions)
                    .anyMatch { fileExt: String -> fileExt == fileExtension }
            }
            .findFirst()
        require(sourceLoaderOptional.isPresent) {
            "can not find fileExtension:[$fileExtension] PropertySourceLoader."
        }
        return sourceLoaderOptional.get()
    }

    private fun getCoSkyPropertySourceOfConfig(
        sourceLoader: PropertySourceLoader,
        config: Config
    ): OriginTrackedMapPropertySource {
        val byteArrayResource = ByteArrayResource(config.data.toByteArray())
        val propertySourceList = sourceLoader.load(config.configId, byteArrayResource)
        val source = getMapSource(config.configId, propertySourceList)
        return OriginTrackedMapPropertySource(getNameOfConfigId(config.configId), source)
    }

    private fun getMapSource(configId: String, propertySourceList: List<PropertySource<*>>): Map<String, Any> {
        if (propertySourceList.isEmpty()) {
            return emptyMap()
        }
        if (propertySourceList.size == 1) {
            val propertySource = propertySourceList[0]
            if (propertySource.source is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                return propertySource.source as Map<String, Any>
            }
        }
        return Collections.singletonMap<String, Any>(
            getNameOfConfigId(configId),
            propertySourceList
        )
    }
}
