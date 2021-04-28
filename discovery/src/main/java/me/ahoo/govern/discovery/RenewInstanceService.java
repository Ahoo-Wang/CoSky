package me.ahoo.govern.discovery;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import java.util.Set;
import java.util.concurrent.*;

/**
 * @author ahoo wang
 */
@Slf4j
public class RenewInstanceService {

    private volatile boolean running;
    private final RenewProperties renewProperties;
    private final ServiceRegistry serviceRegistry;
    private final ScheduledExecutorService scheduledExecutorService;

    public RenewInstanceService(RenewProperties renewProperties, ServiceRegistry serviceRegistry) {
        this(renewProperties,
                serviceRegistry,
                new ScheduledThreadPoolExecutor(1, createThreadFactory()));
    }

    public RenewInstanceService(RenewProperties renewProperties, ServiceRegistry serviceRegistry, ScheduledExecutorService scheduledExecutorService) {
        this.renewProperties = renewProperties;
        this.serviceRegistry = serviceRegistry;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public void start() {
        if (isRunning()) {
            return;
        }
        log.info("start.");
        running = true;

        scheduledExecutorService.scheduleAtFixedRate(this::renew, renewProperties.getInitialDelay(), renewProperties.getPeriod(), TimeUnit.SECONDS);
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        if (!running) {
            return;
        }
        log.info("stop.");
        running = false;
        scheduledExecutorService.shutdown();
    }

    private void renew() {

        final Set<NamespacedServiceInstance> instances = serviceRegistry.getRegisteredEphemeralInstances();
        log.info("renew - instances size:{}.", instances.size());
        if (instances.isEmpty()) {
            return;
        }

        CompletableFuture<Boolean>[] renewFutures = new CompletableFuture[instances.size()];
        var instanceIterator = instances.iterator();
        for (int i = 0; i < renewFutures.length; i++) {
            var namespacedServiceInstance = instanceIterator.next();
            renewFutures[i] = serviceRegistry.renew(namespacedServiceInstance.getNamespace(), namespacedServiceInstance.getServiceInstance());
        }
        CompletableFuture.allOf(renewFutures).join();
    }

    private static ThreadFactory createThreadFactory() {
        return new ThreadFactoryBuilder()
                .setNameFormat(RenewInstanceService.class.getSimpleName() + "-%d")
                .setDaemon(true)
                .build();
    }
}
