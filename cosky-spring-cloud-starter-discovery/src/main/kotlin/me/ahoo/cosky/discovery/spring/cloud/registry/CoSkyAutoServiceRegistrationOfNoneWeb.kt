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
package me.ahoo.cosky.discovery.spring.cloud.registry

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import me.ahoo.cosky.core.util.ProcessId
import me.ahoo.cosky.discovery.ServiceInstanceContext
import org.springframework.beans.BeansException
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationListener

/**
 * CoSky Auto Service Registration Of None Web.
 *
 * @author ahoo wang
 */
class CoSkyAutoServiceRegistrationOfNoneWeb(
    private val serviceRegistry: CoSkyServiceRegistry,
    private val registration: CoSkyRegistration
) : ApplicationListener<ApplicationStartedEvent>, ApplicationContextAware {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private var isWebApp = false
    override fun onApplicationEvent(event: ApplicationStartedEvent) {
        if (isWebApp) {
            log.debug {
                "OnApplicationEvent - Ignoring registration service of WebServerApplicationContext"
            }
            return
        }
        if (registration.port == 0) {
            /**
             * use PID as port
             */
            registration.setSchema("__")
            registration.port = ProcessId.currentProcessId.toInt()
        }
        ServiceInstanceContext.serviceInstance = registration.asServiceInstance()
        serviceRegistry.register(registration)
    }

    @PreDestroy
    fun destroy() {
        if (isWebApp) {
            return
        }
        serviceRegistry.deregister(registration)
    }

    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        isWebApp = applicationContext is WebServerApplicationContext
    }
}
