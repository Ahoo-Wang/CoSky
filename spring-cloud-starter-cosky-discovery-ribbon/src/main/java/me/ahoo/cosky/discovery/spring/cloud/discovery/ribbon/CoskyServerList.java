package me.ahoo.cosky.discovery.spring.cloud.discovery.ribbon;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import lombok.var;
import me.ahoo.cosky.core.util.Futures;
import me.ahoo.cosky.discovery.ServiceDiscovery;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
public class CoskyServerList extends AbstractServerList<CoskyServer> {
    private String serviceId;
    private final ServiceDiscovery serviceDiscovery;

    public CoskyServerList(ServiceDiscovery serviceDiscovery) {
        this.serviceDiscovery = serviceDiscovery;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig iClientConfig) {
        this.serviceId = iClientConfig.getClientName();
    }

    @Override
    public List<CoskyServer> getInitialListOfServers() {
        return getCoskyServers();
    }

    private List<CoskyServer> getCoskyServers() {
        var getInstancesFuture = serviceDiscovery.getInstances(this.serviceId);
        var instances = Futures.getUnChecked(getInstancesFuture, Duration.ofSeconds(2));
        if (instances.isEmpty()) {
            Collections.emptyList();
        }
        return instances.stream().map(CoskyServer::new).collect(Collectors.toList());
    }

    /**
     * Return updated list of servers. This is called say every 30 secs
     * (configurable) by the Loadbalancer's Ping cycle
     */
    @Override
    public List<CoskyServer> getUpdatedListOfServers() {
        return getCoskyServers();
    }
}
