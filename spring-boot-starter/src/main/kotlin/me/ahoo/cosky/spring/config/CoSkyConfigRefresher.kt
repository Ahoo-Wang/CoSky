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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.spring.CoSkyClients
import me.ahoo.cosky.spring.CoSkyProperties
import me.ahoo.cosky.spring.revisionOrZero
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.cloud.endpoint.event.RefreshEvent
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.Executors

/**
 * Publishes a Spring Cloud [RefreshEvent] when a Config this application imported changes. The refresh then runs the
 * imports again, which reads the new content from the same client.
 */
class CoSkyConfigRefresher(
    private val client: CoSkyClient,
    private val properties: CoSkyProperties,
    private val publisher: ApplicationEventPublisher,
) : SmartInitializingSingleton, DisposableBean {
    private val scope = CoroutineScope(SupervisorJob() + VirtualThreads.dispatcher)

    /**
     * A platform thread, unlike everything else here: the imports block inside the synchronized
     * `ContextRefresher.refresh`, which on JDK 21-23 would pin a carrier the client itself needs.
     */
    private val refreshing = Executors.newSingleThreadExecutor {
        Thread(it, "cosky-config-refresh").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    override fun afterSingletonsInstantiated() {
        val configs = client.namespace(properties.namespace).configs
        CoSkyClients.importedBy(properties).forEach { (config, loaded) ->
            scope.launch {
                // By revision, so that a change between the load and this point counts too.
                configs.watch(config).dropWhile { it.revisionOrZero() == loaded }.collect { refresh(config) }
            }
        }
    }

    private suspend fun refresh(config: String) = withContext(refreshing) {
        try {
            publisher.publishEvent(RefreshEvent(this@CoSkyConfigRefresher, config, "CoSky Config $config"))
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Content that does not parse, say. The next change may fix it, so this keeps watching.
            log.warn("Refreshing after a change to Config {} failed.", config, e)
        }
    }

    override fun destroy() {
        scope.cancel()
        refreshing.close()
    }

    private companion object {
        val log = LoggerFactory.getLogger(CoSkyConfigRefresher::class.java)
    }
}
