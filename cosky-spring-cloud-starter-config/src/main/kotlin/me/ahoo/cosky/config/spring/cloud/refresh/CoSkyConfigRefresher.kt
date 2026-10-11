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

import io.github.oshai.kotlinlogging.KotlinLogging
import me.ahoo.cosky.config.ConfigWatcher
import me.ahoo.cosky.config.spring.cloud.CoSkyConfigProperties
import me.ahoo.cosky.spring.cloud.CoSkyProperties
import org.springframework.beans.BeansException
import org.springframework.beans.factory.DisposableBean
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.cloud.endpoint.event.RefreshEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationListener
import reactor.core.Disposable

/**
 * CoSky Config Refresher.
 *
 * Publishes a [RefreshEvent] for every config change, only after the local config cache holds the new value, so
 * that the refresh can not read the stale one.
 *
 * @author ahoo wang
 */
class CoSkyConfigRefresher(
    private val coSkyProperties: CoSkyProperties,
    private val configProperties: CoSkyConfigProperties,
    private val configWatcher: ConfigWatcher
) : ApplicationListener<ApplicationReadyEvent>, ApplicationContextAware, DisposableBean {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private lateinit var applicationContext: ApplicationContext

    @Volatile
    private var watching: Disposable? = null

    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    @Synchronized
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        if (watching != null) {
            return
        }
        val configId = requireNotNull(configProperties.configId) { "configId must not be null." }
        watching = configWatcher.watchConfig(coSkyProperties.namespace, configId)
            // The first snapshot is the config the application has just started with.
            .skip(1)
            .subscribe {
                log.info {
                    "Refresh - CoSky - configId:[$configId] - version:[${it.value?.version}]."
                }
                applicationContext.publishEvent(RefreshEvent(this, it, "Refresh CoSky config"))
            }
    }

    override fun destroy() {
        watching?.dispose()
    }
}
