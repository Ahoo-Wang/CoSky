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

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.core.CoSky
import me.ahoo.cosky.core.Namespaced
import me.ahoo.cosky.core.util.RedisKeys.hasWrap
import me.ahoo.cosky.core.util.RedisKeys.ofKey
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Cosky Properties.
 *
 * @author ahoo wang
 */
@ConfigurationProperties(CoSkyProperties.PREFIX)
class CoSkyProperties(
    var enabled: Boolean = true,
    override var namespace: String = Namespaced.DEFAULT
) : Namespaced {

    companion object {
        private val log = KotlinLogging.logger {}
        const val PREFIX = "spring.cloud." + CoSky.COSKY
    }

    init {
        if (!hasWrap(namespace)) {
            val clusterNamespace = ofKey(true, namespace)
            log.warn {
                "When Redis is in cluster mode, namespace:[$namespace-->$clusterNamespace]" +
                    " must be wrapped by {}(hashtag)."
            }
            namespace = clusterNamespace
        }
    }
}
