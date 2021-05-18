package me.ahoo.govern.discovery;

import com.google.common.base.Strings;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * @author ahoo wang
 */
public class Instance {
    private String instanceId;
    private String serviceId;
    private String schema;
    private String host;
    private int port;

    /**
     * Creates a URI from the given ServiceInstance's host:port.
     *
     * @param instance the ServiceInstance.
     * @return URI of the form {{@link #schema}}://{@link #host}:{@link #port}".
     */
    public static URI getUri(Instance instance) {
        String uri = Strings.lenientFormat("%s://%s:%s", instance.getSchema(), instance.getHost(), instance.getPort());
        return URI.create(uri);
    }

    public URI parseUri() {
        return getUri(this);
    }

    public String getInstanceId() {
        return this.instanceId;
    }

    public String getServiceId() {
        return this.serviceId;
    }

    public String getSchema() {
        return this.schema;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public void setSchema(String schema) {
        this.schema = schema.toLowerCase(Locale.ROOT);
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    private final static Set<String> secureSchemas;

    static {
        secureSchemas = new HashSet<>();
        secureSchemas.add("https");
        secureSchemas.add("wss");
    }

    public boolean isSecure() {
        return secureSchemas.contains(schema);
    }
}
