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

import com.alibaba.cloud.nacos.NacosDiscoveryProperties
import com.alibaba.cloud.nacos.NacosServiceManager
import com.alibaba.nacos.api.exception.NacosException
import com.alibaba.nacos.api.naming.NamingService
import com.alibaba.nacos.api.naming.pojo.Instance
import me.ahoo.cosky.core.NamespacedContext
import me.ahoo.cosky.discovery.InstanceChangedEvent
import me.ahoo.cosky.discovery.InstanceEventListenerContainer
import me.ahoo.cosky.discovery.NamespacedServiceId
import me.ahoo.cosky.discovery.ServiceInstance
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import java.util.concurrent.ConcurrentHashMap

/**
 * Cosky To Nacos Mirror.
 *
 * @author ahoo wang
 */
@Service
class CoskyToNacosMirror(
    private val coskyServiceDiscovery: ConsistencyRedisServiceDiscovery,
    private val instanceEventListenerContainer: InstanceEventListenerContainer,
    private val nacosDiscoveryProperties: NacosDiscoveryProperties,
    private val nacosServiceManager: NacosServiceManager,
) : Mirror {
    companion object {
        private val log = LoggerFactory.getLogger(CoskyToNacosMirror::class.java)
    }

    private val serviceMapListener: ConcurrentHashMap<String, CoskyServiceChangedListener> = ConcurrentHashMap()

    fun namingService(): NamingService {
        return nacosServiceManager.getNamingService(nacosDiscoveryProperties.nacosProperties)
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 30000)
    fun mirror() {
        coskyServiceDiscovery.services
            .filter { !serviceMapListener.containsKey(it) }
            .flatMap { serviceId ->
                coskyToNacos(serviceId)
            }
            .doOnError {
                if (log.isErrorEnabled) {
                    log.error(it.message, it)
                }
            }.subscribe()
    }

    fun coskyToNacos(serviceId: String): Flux<Boolean> {
        return coskyServiceDiscovery.getInstances(serviceId)
            .filter {
                shouldRegister(it.metadata)
            }
            .map { coskyToNacos(it) }
            .doOnComplete {
                serviceMapListener.computeIfAbsent(serviceId) {
                    val listener = CoskyServiceChangedListener(serviceId)
                    instanceEventListenerContainer
                        .listen(NamespacedServiceId(NamespacedContext.namespace, serviceId))
                        .doOnNext { listener.onChange(it) }
                        .subscribe()
                    listener
                }
            }
            .doOnError {
                if (log.isErrorEnabled) {
                    log.error(it.message, it)
                }
            }
    }

    /**
     * cosky To Nacos.
     *
     * @param coskyInstance coskyInstance
     * @see ServiceInstance coskyInstance
     */
    fun coskyToNacos(coskyInstance: ServiceInstance): Boolean {
        markRegisterSource(coskyInstance.metadata)
        val nacosInstance = getNacosInstanceFromCosky(coskyInstance)
        markRegisterSource(nacosInstance.metadata)
        val group: String = nacosDiscoveryProperties.group
        namingService().registerInstance(coskyInstance.serviceId, group, nacosInstance)
        return java.lang.Boolean.TRUE
    }

    private fun getNacosInstanceFromCosky(serviceInstance: ServiceInstance): Instance {
        val instance = Instance()
        instance.ip = serviceInstance.host
        instance.port = serviceInstance.port
        instance.weight = serviceInstance.weight.toDouble()
        instance.clusterName = nacosDiscoveryProperties.clusterName
        instance.isEnabled = nacosDiscoveryProperties.isInstanceEnabled
        instance.metadata = serviceInstance.metadata
        instance.isEphemeral = serviceInstance.isEphemeral
        return instance
    }

    override val source: String
        get() = Mirror.MIRROR_SOURCE_COSKY
    override val target: String
        get() = Mirror.MIRROR_SOURCE_NACOS

    private inner class CoskyServiceChangedListener(val serviceId: String) {
        fun onChange(instanceChangedEvent: InstanceChangedEvent) {
            val instance: me.ahoo.cosky.discovery.Instance = instanceChangedEvent.instance
            if (log.isInfoEnabled) {
                log.info(
                    "CoskyServiceChangedListener - onChange - @[{}] op:[{}] instanceId:[{}]",
                    serviceId,
                    instanceChangedEvent.event,
                    instance.instanceId,
                )
            }
            val namespacedServiceId: NamespacedServiceId = instanceChangedEvent.namespacedServiceId
            if (InstanceChangedEvent.Event.REGISTER == instanceChangedEvent.event) {
                coskyServiceDiscovery.getInstance(
                    namespacedServiceId.namespace,
                    namespacedServiceId.serviceId,
                    instance.instanceId,
                )
                    .doOnNext { coskyInstance: ServiceInstance ->
                        if (target == coskyInstance.metadata[Mirror.MIRROR_SOURCE]) {
                            if (log.isInfoEnabled) {
                                log.info(
                                    "CoskyServiceChangedListener - Ignore [cosky.mirror.source is target] - @[{}] op:[{}] instanceId:[{}]",
                                    serviceId,
                                    instanceChangedEvent.event,
                                    instance.instanceId,
                                )
                            }
                            return@doOnNext
                        }
                        coskyToNacos(coskyInstance)
                    }
                    .subscribe()
                return
            }
            if (InstanceChangedEvent.Event.DEREGISTER == instanceChangedEvent.event || InstanceChangedEvent.Event.EXPIRED == instanceChangedEvent.event) {
                // TODO
//                instanceChangedEvent.instance.metadata[Mirror.MIRROR_SOURCE] = target
//                if (target == metadata[Mirror.MIRROR_SOURCE]) {
//                    if (log.isInfoEnabled) {
//                        log.info(
//                            "CoskyServiceChangedListener - Ignore [cosky.mirror.source is target] - @[{}] op:[{}] instanceId:[{}]",
//                            serviceId,
//                            instanceChangedEvent.event,
//                            instance.instanceId
//                        )
//                    }
//                    return
//                }
                val group: String = nacosDiscoveryProperties.group
                try {
                    namingService().deregisterInstance(instance.serviceId, group, instance.host, instance.port)
                } catch (e: NacosException) {
                    log.error("NacosServiceChangedListener - onChange - deregisterInstance error.", e)
                }
            }
        }
    }
}
