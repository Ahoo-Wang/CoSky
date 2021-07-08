/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosky.mirror;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.cloud.nacos.registry.NacosServiceRegistry;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.cosky.core.NamespacedContext;
import me.ahoo.cosky.discovery.NamespacedServiceId;
import me.ahoo.cosky.discovery.ServiceChangedEvent;
import me.ahoo.cosky.discovery.ServiceChangedListener;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author ahoo wang
 */
@Slf4j
@Service
public class CoskyToNacosMirror implements Mirror {

    private final ConsistencyRedisServiceDiscovery coskyServiceDiscovery;
    private final NacosDiscoveryProperties nacosDiscoveryProperties;
    private final NacosServiceManager nacosServiceManager;
    private final ConcurrentHashMap<String, CoskyServiceChangedListener> serviceMapListener;

    public CoskyToNacosMirror(ConsistencyRedisServiceDiscovery coskyServiceDiscovery,
                              NacosDiscoveryProperties nacosDiscoveryProperties, NacosServiceManager nacosServiceManager) {
        this.coskyServiceDiscovery = coskyServiceDiscovery;
        this.nacosDiscoveryProperties = nacosDiscoveryProperties;
        this.nacosServiceManager = nacosServiceManager;
        this.serviceMapListener = new ConcurrentHashMap<>();
    }

    public NamingService namingService() {
        return nacosServiceManager.getNamingService(this.nacosDiscoveryProperties.getNacosProperties());
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 30_000)
    public void mirror() {
        coskyServiceDiscovery.getServices().thenAccept(coskyServices -> {
            coskyServices.stream().filter(serviceId -> !serviceMapListener.containsKey(serviceId))
                    .forEach(serviceId -> coskyToNacos(serviceId));
        }).exceptionally(throwable -> {
            log.error(throwable.getMessage(), throwable);
            return null;
        });
    }

    public void coskyToNacos(String serviceId) {
        coskyServiceDiscovery.getInstances(serviceId).thenAccept(coskyInstances -> {

            coskyInstances.stream().filter(serviceInstance -> shouldRegister(serviceInstance.getMetadata()))
                    .forEach((serviceInstance -> coskyToNacos(serviceInstance)));

            serviceMapListener.computeIfAbsent(serviceId, (key) -> {
                var listener = new CoskyServiceChangedListener(serviceId);
                coskyServiceDiscovery.addListener(NamespacedServiceId.of(NamespacedContext.GLOBAL.getNamespace(), serviceId), listener);
                return listener;
            });
        }).exceptionally(throwable -> {
            log.error(throwable.getMessage(), throwable);
            return null;
        });

    }

    /**
     * @param coskyInstance
     * @see NacosServiceRegistry
     */
    @SneakyThrows
    public void coskyToNacos(ServiceInstance coskyInstance) {
        markRegisterSource(coskyInstance.getMetadata());
        var nacosInstance = getNacosInstanceFromCosky(coskyInstance);
        markRegisterSource(nacosInstance.getMetadata());
        String group = nacosDiscoveryProperties.getGroup();
        namingService().registerInstance(coskyInstance.getServiceId(), group, nacosInstance);
    }

    private Instance getNacosInstanceFromCosky(me.ahoo.cosky.discovery.ServiceInstance serviceInstance) {
        Instance instance = new Instance();
        instance.setIp(serviceInstance.getHost());
        instance.setPort(serviceInstance.getPort());
        instance.setWeight(serviceInstance.getWeight());
        instance.setClusterName(nacosDiscoveryProperties.getClusterName());
        instance.setEnabled(nacosDiscoveryProperties.isInstanceEnabled());
        instance.setMetadata(serviceInstance.getMetadata());
        instance.setEphemeral(serviceInstance.isEphemeral());
        return instance;
    }

    @Override
    public String getSource() {
        return MIRROR_SOURCE_COSKY;
    }

    @Override
    public String getTarget() {
        return MIRROR_SOURCE_NACOS;
    }

    private class CoskyServiceChangedListener implements ServiceChangedListener {

        public final String serviceId;

        public CoskyServiceChangedListener(String serviceId) {
            this.serviceId = serviceId;
        }

        @Override
        public void onChange(ServiceChangedEvent serviceChangedEvent) {
            var instance = serviceChangedEvent.getInstance();
            if (log.isInfoEnabled()) {
                log.info("CoskyServiceChangedListener - onChange - @[{}] op:[{}] instanceId:[{}]", serviceId, serviceChangedEvent.getOp(), instance.getInstanceId());
            }
            var namespacedServiceId = serviceChangedEvent.getNamespacedServiceId();
            if (ServiceChangedEvent.REGISTER.equals(serviceChangedEvent.getOp())) {
                coskyServiceDiscovery.getInstance(namespacedServiceId.getNamespace(), namespacedServiceId.getServiceId(), instance.getInstanceId()).thenAccept(coskyInstance -> {
                    if (getTarget().equals(coskyInstance.getMetadata().get(MIRROR_SOURCE))) {
                        if (log.isInfoEnabled()) {
                            log.info("CoskyServiceChangedListener - Ignore [cosky.mirror.source is target] - @[{}] op:[{}] instanceId:[{}]", serviceId, serviceChangedEvent.getOp(), instance.getInstanceId());
                        }
                        return;
                    }
                    coskyToNacos(coskyInstance);
                });
                return;
            }

            if (ServiceChangedEvent.DEREGISTER.equals(serviceChangedEvent.getOp()) || ServiceChangedEvent.EXPIRED.equals(serviceChangedEvent.getOp())) {
                var coskyInstance = (ServiceInstance) serviceChangedEvent.getInstance();
                if (getTarget().equals(coskyInstance.getMetadata().get(MIRROR_SOURCE))) {
                    if (log.isInfoEnabled()) {
                        log.info("CoskyServiceChangedListener - Ignore [cosky.mirror.source is target] - @[{}] op:[{}] instanceId:[{}]", serviceId, serviceChangedEvent.getOp(), instance.getInstanceId());
                    }
                    return;
                }
                String group = nacosDiscoveryProperties.getGroup();
                try {
                    namingService().deregisterInstance(instance.getServiceId(), group, instance.getHost(), instance.getPort());
                } catch (NacosException e) {
                    log.error("NacosServiceChangedListener - onChange - deregisterInstance error.", e);
                }
            }
        }
    }
}
