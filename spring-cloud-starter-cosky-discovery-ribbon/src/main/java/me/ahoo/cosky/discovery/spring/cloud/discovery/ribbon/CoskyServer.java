package me.ahoo.cosky.discovery.spring.cloud.discovery.ribbon;

import com.netflix.loadbalancer.Server;
import me.ahoo.cosky.discovery.ServiceInstance;

/**
 * @author ahoo wang
 */
public class CoskyServer extends Server {
    private final ServiceInstance instance;
    private final MetaInfo metaInfo;

    public CoskyServer(final ServiceInstance instance) {
        super(instance.getSchema(), instance.getHost(), instance.getPort());
        this.instance = instance;
        this.metaInfo = new MetaInfo() {
            @Override
            public String getAppName() {
                return instance.getServiceId();
            }

            @Override
            public String getServerGroup() {
                return null;
            }

            @Override
            public String getServiceIdForDiscovery() {
                return null;
            }

            @Override
            public String getInstanceId() {
                return instance.getInstanceId();
            }
        };
    }

    @Override
    public MetaInfo getMetaInfo() {
        return metaInfo;
    }

    public ServiceInstance getInstance() {
        return instance;
    }
}
