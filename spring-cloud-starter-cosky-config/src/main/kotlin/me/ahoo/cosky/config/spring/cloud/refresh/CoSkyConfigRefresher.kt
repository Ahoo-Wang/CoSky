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
package me.ahoo.cosky.config.spring.cloud.refresh

import me.ahoo.cosky.config.ListenableConfigService
import me.ahoo.cosky.config.NamespacedConfigId
import me.ahoo.cosky.config.spring.cloud.CoSkyConfigProperties
import me.ahoo.cosky.spring.cloud.CoSkyProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cloud.endpoint.event.RefreshEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationListener

/**
 * CoSky Config Refresher.
 *
 * @author ahoo wang
 */
class CoSkyConfigRefresher(
    private val coSkyProperties: CoSkyProperties,
    private val configProperties: CoSkyConfigProperties,
    private val listenableConfigService: ListenableConfigService
) : ApplicationListener<ApplicationReadyEvent>, ApplicationContextAware {
    private lateinit var applicationContext: ApplicationContext

    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        listenableConfigService.listen(
            NamespacedConfigId(
                coSkyProperties.namespace,
                requireNotNull(configProperties.configId) { "configId must not be null." }
            )
        ).doOnNext {
            if (log.isInfoEnabled) {
                log.info("Refresh - CoSky - configId:[{}] - [{}]", configProperties.configId, it.event)
            }
            applicationContext.publishEvent(
                RefreshEvent(this, it.event, "Refresh CoSky config")
            )
        }.subscribe()
    }

    companion object {
        private val log = LoggerFactory.getLogger(CoSkyConfigRefresher::class.java)
    }
}
