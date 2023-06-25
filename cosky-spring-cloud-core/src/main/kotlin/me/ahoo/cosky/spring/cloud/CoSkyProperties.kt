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
package me.ahoo.cosky.spring.cloud

import me.ahoo.cosky.core.CoSky
import me.ahoo.cosky.core.Namespaced
import me.ahoo.cosky.core.util.RedisKeys.hasWrap
import me.ahoo.cosky.core.util.RedisKeys.ofKey
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Cosky Properties.
 *
 * @author ahoo wang
 */
@ConfigurationProperties(CoSkyProperties.PREFIX)
data class CoSkyProperties(
    val enabled: Boolean = true,
    override var namespace: String = Namespaced.DEFAULT
) : Namespaced {

    companion object {
        const val PREFIX = "spring.cloud." + CoSky.COSKY
        private val log = LoggerFactory.getLogger(CoSkyProperties::class.java)
    }

    init {
        if (!hasWrap(namespace)) {
            val clusterNamespace = ofKey(true, namespace)
            if (log.isWarnEnabled) {
                log.warn(
                    "When Redis is in cluster mode, namespace:[{}-->{}] must be wrapped by {}(hashtag).",
                    namespace,
                    clusterNamespace,
                    "{}",
                )
            }
            namespace = clusterNamespace
        }
    }
}
