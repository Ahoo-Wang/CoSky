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

package me.ahoo.cosky.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Renew Instance Service.
 *
 * @author ahoo wang
 */
@Slf4j
public class RenewInstanceService {
    
    private volatile boolean running;
    private final RenewProperties renewProperties;
    private final ServiceRegistry serviceRegistry;
    private final AtomicInteger renewCounter = new AtomicInteger();
    private final Scheduler scheduler;
    private Disposable scheduleDisposable;
    
    @VisibleForTesting
    @Nullable
    private final Consumer<ServiceInstance> hookOnRenew;
    
    public RenewInstanceService(RenewProperties renewProperties, ServiceRegistry serviceRegistry) {
        this(renewProperties,
            serviceRegistry,
            Schedulers.newSingle("CoSky-Renew", true), null);
    }
    
    public RenewInstanceService(RenewProperties renewProperties, ServiceRegistry serviceRegistry, Consumer<ServiceInstance> hookOnRenew) {
        this(renewProperties,
            serviceRegistry,
            Schedulers.newSingle("CoSky-Renew", true), hookOnRenew);
    }
    
    public RenewInstanceService(RenewProperties renewProperties, ServiceRegistry serviceRegistry, Scheduler scheduler, Consumer<ServiceInstance> hookOnRenew) {
        this.renewProperties = renewProperties;
        this.serviceRegistry = serviceRegistry;
        this.scheduler = scheduler;
        this.hookOnRenew = hookOnRenew;
    }
    
    @Synchronized
    public void start() {
        if (isRunning()) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info("start.");
        }
        running = true;
        
        scheduleDisposable = scheduler.schedulePeriodically(this::renew, renewProperties.getInitialDelay().getSeconds(), renewProperties.getPeriod().getSeconds(), TimeUnit.SECONDS);
    }
    
    public boolean isRunning() {
        return running;
    }
    
    @Synchronized
    public void stop() {
        if (!running) {
            return;
        }
        if (log.isInfoEnabled()) {
            log.info("stop.");
        }
        running = false;
        if (null != scheduleDisposable && !scheduleDisposable.isDisposed()) {
            scheduleDisposable.dispose();
        }
        scheduler.dispose();
    }
    
    private void renew() {
        final int times = renewCounter.incrementAndGet();
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final Map<NamespacedInstanceId, ServiceInstance> instances = serviceRegistry.getRegisteredEphemeralInstances();
        if (log.isDebugEnabled()) {
            log.debug("renew - instances size:{} start - times@[{}] .", instances.size(), times);
        }
        
        if (instances.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("renew - instances size:{} end - times@[{}] .", instances.size(), times);
            }
            return;
        }
        
        Flux.fromIterable(instances.entrySet())
            .flatMap(namespacedServiceInstance -> serviceRegistry.renew(namespacedServiceInstance.getKey().getNamespace(), namespacedServiceInstance.getValue())
                .doOnSuccess(nil -> {
                    if (null != hookOnRenew) {
                        hookOnRenew.accept(namespacedServiceInstance.getValue());
                    }
                })
            )
            .doOnError(throwable -> {
                if (log.isWarnEnabled()) {
                    log.warn("renew - failed.", throwable);
                }
            }).doOnComplete(() -> {
                if (log.isDebugEnabled()) {
                    log.debug("renew - instances size:{} end - times@[{}] taken:[{}ms].", instances.size(), times, stopwatch.elapsed(TimeUnit.MILLISECONDS));
                }
                
            }).subscribe();
    }
}
