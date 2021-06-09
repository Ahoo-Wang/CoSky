package me.ahoo.cosky.discovery.spring.cloud.discovery.ribbon;

import com.netflix.loadbalancer.Server;
import org.springframework.cloud.netflix.ribbon.DefaultServerIntrospector;

import java.util.Map;

/**
 * @author ahoo wang
 */
public class CoskyServerIntrospector extends DefaultServerIntrospector {

    @Override
    public boolean isSecure(Server server) {
        if (server instanceof CoskyServer) {
            return ((CoskyServer) server).getInstance().isSecure();
        }
        return super.isSecure(server);
    }

    @Override
    public Map<String, String> getMetadata(Server server) {
        if (server instanceof CoskyServer) {
            return ((CoskyServer) server).getInstance().getMetadata();
        }
        return super.getMetadata(server);
    }
}
