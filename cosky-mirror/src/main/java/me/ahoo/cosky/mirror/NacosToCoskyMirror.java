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

package me.ahoo.cosky.mirror;

import me.ahoo.cosky.core.CoSkyException;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.Event;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.google.common.base.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Nacos To Cosky Mirror.
 *
 * @author ahoo wang
 * @see com.alibaba.cloud.nacos.discovery.NacosServiceDiscovery
 */
@Slf4j
@Service
public class NacosToCoskyMirror implements Mirror {
    private final RedisServiceRegistry coskyServiceRegistry;
    private final NacosDiscoveryProperties nacosDiscoveryProperties;
    private final NacosServiceManager nacosServiceManager;
    private final ConcurrentHashMap<String, NacosServiceChangedListener> serviceMapListener;
    
    public NacosToCoskyMirror(RedisServiceRegistry coskyServiceRegistry,
                              NacosDiscoveryProperties nacosDiscoveryProperties,
                              NacosServiceManager nacosServiceManager) {
        this.coskyServiceRegistry = coskyServiceRegistry;
        this.nacosDiscoveryProperties = nacosDiscoveryProperties;
        this.nacosServiceManager = nacosServiceManager;
        this.serviceMapListener = new ConcurrentHashMap<>();
    }
    
    public NamingService namingService() {
        return nacosServiceManager.getNamingService(this.nacosDiscoveryProperties.getNacosProperties());
    }
    
    /**
     * getNacosServices.
     *
     * @see com.alibaba.cloud.nacos.discovery.NacosServiceDiscovery
     */
    @SneakyThrows
    public List<String> getNacosServices() {
        String group = nacosDiscoveryProperties.getGroup();
        ListView<String> services = namingService().getServicesOfServer(1, Integer.MAX_VALUE, group);
        return services.getData();
    }
    
    @Scheduled(initialDelay = 15_000, fixedDelay = 30_000)
    public void mirror() {
        List<String> nacosServices = getNacosServices();
        Flux.fromIterable(nacosServices)
            .filter(serviceId -> !serviceMapListener.containsKey(serviceId))
            .flatMap(this::nacosToCosky)
            .subscribe();
    }
    
    @SneakyThrows
    public Flux<Boolean> nacosToCosky(String serviceId) {
        String group = nacosDiscoveryProperties.getGroup();
        List<Instance> nacosInstances = namingService().selectInstances(serviceId, group, true);
        
        serviceMapListener.computeIfAbsent(serviceId, (key) -> {
            NacosServiceChangedListener listener = new NacosServiceChangedListener(serviceId, nacosInstances);
            try {
                namingService().subscribe(serviceId, group, listener);
            } catch (NacosException e) {
                log.error("nacosToCosky subscribe error.", e);
                throw new CoSkyException(e);
            }
            return listener;
        });
        return Flux.fromIterable(nacosInstances)
            .filter(serviceInstance -> shouldRegister(serviceInstance.getMetadata()))
            .flatMap((serviceInstance -> nacosToCosky(serviceId, serviceInstance)));
    }
    
    public Mono<Boolean> nacosToCosky(String serviceId, Instance instance) {
        ServiceInstance coskyInstance = getCoskyInstanceFromNacos(serviceId, instance);
        return coskyServiceRegistry.register(coskyInstance)
            .doOnError(throwable -> {
                if (log.isErrorEnabled()) {
                    log.error(throwable.getMessage(), throwable);
                }
            })
            .retry(1);
    }
    
    private ServiceInstance getCoskyInstanceFromNacos(String serviceId, Instance instance) {
        ServiceInstance coskyInstance = new ServiceInstance();
        coskyInstance.setServiceId(serviceId);
        String secureStr = instance.getMetadata().get("secure");
        
        if (!Strings.isNullOrEmpty(secureStr) && Boolean.parseBoolean(secureStr)) {
            coskyInstance.setSchema("https");
        } else {
            coskyInstance.setSchema("http");
        }
        
        coskyInstance.setHost(instance.getIp());
        coskyInstance.setPort(instance.getPort());
        coskyInstance.setWeight((int) instance.getWeight());
        coskyInstance.setMetadata(instance.getMetadata());
        coskyInstance.setEphemeral(instance.isEphemeral());
        /**
         * mark register source {@link #getSource()}
         */
        markRegisterSource(coskyInstance.getMetadata());
        return coskyInstance;
    }
    
    
    @Override
    public String getSource() {
        return MIRROR_SOURCE_NACOS;
    }
    
    @Override
    public String getTarget() {
        return MIRROR_SOURCE_COSKY;
    }
    
    
    private class NacosServiceChangedListener implements EventListener {
        
        private final String serviceId;
        private volatile List<Instance> lastInstances;
        
        private NacosServiceChangedListener(String serviceId, List<Instance> lastInstances) {
            this.serviceId = serviceId;
            this.lastInstances = lastInstances;
        }
        
        /**
         * callback event.
         *
         * @param event event
         * @see NamingEvent
         */
        @Override
        public void onEvent(Event event) {
            if (log.isInfoEnabled()) {
                log.info("NacosServiceChangedListener - onEvent @[{}]", serviceId);
            }
            NamingEvent namingEvent = (NamingEvent) event;
            List<Instance> currentInstances = namingEvent.getInstances();
            List<Instance> addedInstances = currentInstances.stream().filter(current ->
                lastInstances.stream().noneMatch(last -> last.getInstanceId().equals(current.getInstanceId()))
            ).collect(Collectors.toList());
            
            List<Instance> removedInstances = lastInstances.stream().filter(last ->
                currentInstances.stream().noneMatch(current -> last.getInstanceId().equals(current.getInstanceId()))
            ).collect(Collectors.toList());
            addedInstances.forEach(addedInstance -> {
                if (log.isInfoEnabled()) {
                    log.info("NacosServiceChangedListener - onEvent - add {}", addedInstance);
                }
                if (getTarget().equals(addedInstance.getMetadata().get(MIRROR_SOURCE))) {
                    if (log.isInfoEnabled()) {
                        log.info("NacosServiceChangedListener - Ignore [cosky.mirror.source is target] - @[{}] instanceId:[{}]", serviceId, addedInstance.getInstanceId());
                    }
                    return;
                }
                nacosToCosky(serviceId, addedInstance).subscribe();
            });
            removedInstances.forEach(removedInstance -> {
                if (log.isInfoEnabled()) {
                    log.info("NacosServiceChangedListener - onEvent - remove {}", removedInstance);
                }
                ServiceInstance coskyInstance = getCoskyInstanceFromNacos(serviceId, removedInstance);
                coskyServiceRegistry.deregister(coskyInstance).subscribe();
            });
            
            this.lastInstances = currentInstances;
        }
    }
    
}
