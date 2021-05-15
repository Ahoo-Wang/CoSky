package me.ahoo.govern.rest.job;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.NamespaceService;
import me.ahoo.govern.core.NamespacedContext;
import me.ahoo.govern.discovery.ServiceStatistic;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * @author ahoo wang
 */
@Slf4j
@Service
public class StatServiceJob {
    private final NamespaceService namespaceService;
    private final ServiceStatistic serviceStatistic;

    public StatServiceJob(NamespaceService namespaceService, ServiceStatistic serviceStatistic) {
        this.namespaceService = namespaceService;
        this.serviceStatistic = serviceStatistic;
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 60_000)
    public void doStatService() {
        if (log.isInfoEnabled()) {
            log.info("doStatService - start.");
        }
        var currentNamespace = NamespacedContext.GLOBAL.getNamespace();
        var namespaces = namespaceService.getNamespaces().join();
        if (!namespaces.contains(currentNamespace)) {
            namespaceService.setNamespace(currentNamespace).join();
        }

        if (!namespaces.isEmpty()) {
            namespaces.forEach(serviceStatistic::statService);
        }

        if (log.isInfoEnabled()) {
            log.info("doStatService - end.");
        }
    }
}
