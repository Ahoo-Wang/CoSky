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
package me.ahoo.cosky.mirror

import com.alibaba.nacos.api.naming.listener.Event
import com.alibaba.nacos.api.naming.listener.EventListener
import com.alibaba.nacos.api.naming.listener.NamingEvent
import com.alibaba.nacos.api.naming.pojo.Instance
import com.alibaba.nacos.api.naming.pojo.ListView
import com.google.common.base.Strings
import lombok.SneakyThrows
import me.ahoo.cosky.discovery.ServiceInstance
import org.reactivestreams.Publisher
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * Nacos To Cosky Mirror.
 *
 * @author ahoo wang
 * @see com.alibaba.cloud.nacos.discovery.NacosServiceDiscovery
 */
@Service
class NacosToCoskyMirror(
    coskyServiceRegistry: RedisServiceRegistry,
    nacosDiscoveryProperties: NacosDiscoveryProperties,
    nacosServiceManager: NacosServiceManager
) : Mirror {
    private val coskyServiceRegistry: RedisServiceRegistry
    private val nacosDiscoveryProperties: NacosDiscoveryProperties
    private val nacosServiceManager: NacosServiceManager
    private val serviceMapListener: ConcurrentHashMap<String, NacosServiceChangedListener>

    init {
        this.coskyServiceRegistry = coskyServiceRegistry
        this.nacosDiscoveryProperties = nacosDiscoveryProperties
        this.nacosServiceManager = nacosServiceManager
        serviceMapListener = ConcurrentHashMap()
    }

    fun namingService(): NamingService {
        return nacosServiceManager.getNamingService(nacosDiscoveryProperties.getNacosProperties())
    }

    /**
     * getNacosServices.
     *
     * @see com.alibaba.cloud.nacos.discovery.NacosServiceDiscovery
     */
    @get:SneakyThrows
    val nacosServices: List<String>
        get() {
            val group: String = nacosDiscoveryProperties.getGroup()
            val services: ListView<String> = namingService().getServicesOfServer(1, Int.MAX_VALUE, group)
            return services.data
        }

    @Scheduled(initialDelay = 15000, fixedDelay = 30000)
    fun mirror() {
        val nacosServices = nacosServices
        Flux.fromIterable<String>(nacosServices)
            .filter(Predicate { serviceId: String -> !serviceMapListener.containsKey(serviceId) })
            .flatMap<Boolean>(
                Function<String, Publisher<out Boolean>> { serviceId: String ->
                    this.nacosToCosky(
                        serviceId,
                    )
                },
            )
            .subscribe()
    }

    @SneakyThrows
    fun nacosToCosky(serviceId: String): Flux<Boolean> {
        val group: String = nacosDiscoveryProperties.getGroup()
        val nacosInstances: List<Instance> = namingService().selectInstances(serviceId, group, true)
        serviceMapListener.computeIfAbsent(serviceId) { key: String? ->
            val listener = NacosServiceChangedListener(serviceId, nacosInstances)
            try {
                namingService().subscribe(serviceId, group, listener)
            } catch (e: NacosException) {
                log.error("nacosToCosky subscribe error.", e)
                throw CoSkyException(e)
            }
            listener
        }
        return Flux.fromIterable<Instance>(nacosInstances)
            .filter(Predicate { serviceInstance: Instance -> shouldRegister(serviceInstance.metadata) })
            .flatMap<Boolean>(
                Function<Instance, Publisher<out Boolean?>> { serviceInstance: Instance ->
                    nacosToCosky(
                        serviceId,
                        serviceInstance,
                    )
                },
            )
    }

    fun nacosToCosky(serviceId: String, instance: Instance): Mono<Boolean?> {
        val coskyInstance = getCoskyInstanceFromNacos(serviceId, instance)
        return coskyServiceRegistry.register(coskyInstance)
            .doOnError(
                Consumer { throwable: Throwable ->
                    if (log.isErrorEnabled()) {
                        log.error(throwable.message, throwable)
                    }
                },
            )
            .retry(1)
    }

    private fun getCoskyInstanceFromNacos(serviceId: String, instance: Instance): ServiceInstance {
        val coskyInstance = ServiceInstance()
        coskyInstance.setServiceId(serviceId)
        val secureStr = instance.metadata["secure"]
        if (!Strings.isNullOrEmpty(secureStr) && java.lang.Boolean.parseBoolean(secureStr)) {
            coskyInstance.setSchema("https")
        } else {
            coskyInstance.setSchema("http")
        }
        coskyInstance.setHost(instance.ip)
        coskyInstance.setPort(instance.port)
        coskyInstance.setWeight(instance.weight.toInt())
        coskyInstance.setMetadata(instance.metadata)
        coskyInstance.setEphemeral(instance.isEphemeral)
        /**
         * mark register source [.getSource]
         */
        markRegisterSource(coskyInstance.metadata)
        return coskyInstance
    }

    override val source: String
        get() = Mirror.Companion.MIRROR_SOURCE_NACOS
    override val target: String
        get() = Mirror.Companion.MIRROR_SOURCE_COSKY

    private inner class NacosServiceChangedListener(
        private val serviceId: String,
        @field:Volatile private var lastInstances: List<Instance>
    ) : EventListener {
        /**
         * callback event.
         *
         * @param event event
         * @see NamingEvent
         */
        override fun onEvent(event: Event) {
            if (log.isInfoEnabled()) {
                log.info("NacosServiceChangedListener - onEvent @[{}]", serviceId)
            }
            val namingEvent = event as NamingEvent
            val currentInstances = namingEvent.instances
            val addedInstances = currentInstances.stream().filter { current: Instance ->
                lastInstances.stream().noneMatch { last: Instance -> last.instanceId == current.instanceId }
            }
                .collect(Collectors.toList())
            val removedInstances = lastInstances.stream().filter { last: Instance ->
                currentInstances.stream().noneMatch { current: Instance -> last.instanceId == current.instanceId }
            }
                .collect(Collectors.toList())
            addedInstances.forEach(
                Consumer { addedInstance: Instance ->
                    if (log.isInfoEnabled()) {
                        log.info("NacosServiceChangedListener - onEvent - add {}", addedInstance)
                    }
                    if (target == addedInstance.metadata[Mirror.Companion.MIRROR_SOURCE]) {
                        if (log.isInfoEnabled()) {
                            log.info(
                                "NacosServiceChangedListener - Ignore [cosky.mirror.source is target] - @[{}] instanceId:[{}]",
                                serviceId,
                                addedInstance.instanceId,
                            )
                        }
                        return@forEach
                    }
                    nacosToCosky(serviceId, addedInstance).subscribe()
                },
            )
            removedInstances.forEach(
                Consumer { removedInstance: Instance ->
                    if (log.isInfoEnabled()) {
                        log.info("NacosServiceChangedListener - onEvent - remove {}", removedInstance)
                    }
                    val coskyInstance = getCoskyInstanceFromNacos(serviceId, removedInstance)
                    coskyServiceRegistry.deregister(coskyInstance).subscribe()
                },
            )
            lastInstances = currentInstances
        }
    }
}
