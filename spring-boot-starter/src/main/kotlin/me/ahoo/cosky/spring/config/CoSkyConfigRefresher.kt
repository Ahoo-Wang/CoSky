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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.ahoo.cosky.api.VirtualThreads
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.spring.CoSkyClients
import me.ahoo.cosky.spring.CoSkyProperties
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.cloud.endpoint.event.RefreshEvent
import org.springframework.context.ApplicationEventPublisher

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

    override fun afterSingletonsInstantiated() {
        val configs = client.namespace(properties.namespace).configs
        CoSkyClients.importedBy(properties).forEach { config ->
            scope.launch {
                configs.watch(config).drop(1).collect {
                    publisher.publishEvent(RefreshEvent(this@CoSkyConfigRefresher, config, "CoSky Config $config"))
                }
            }
        }
    }

    override fun destroy() {
        scope.cancel()
    }
}
