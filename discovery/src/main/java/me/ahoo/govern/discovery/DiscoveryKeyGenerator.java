package me.ahoo.govern.discovery;

import com.google.common.base.Strings;
import me.ahoo.govern.core.Namespaced;

/**
 * @author ahoo wang
 */
public class DiscoveryKeyGenerator implements Namespaced {
    private final static String SERVICE_IDX = "svc_idx";
    private final static String SERVICE_INSTANCE_IDX = "svc_itc_idx";
    private final static String SERVICE_INSTANCE = "svc_itc";

    private final String namespace;
    /**
     * {namespace}:{@link #SERVICE_IDX}
     */
    private final String serviceIdxKey;
    /**
     * {namespace}:{@link #SERVICE_INSTANCE_IDX}:{serviceId}
     */
    private final String instanceIdxKeyFormat;

    /**
     * {namespace}:{@link #SERVICE_INSTANCE}:{instanceId}
     */
    private final String instanceKeyFormat;
    /**
     * {namespace}:{@link #SERVICE_INSTANCE}:
     */
    private final String instanceKeyPrefix;
    private final String instanceKeyPatternOfServiceFormat;

    public DiscoveryKeyGenerator(String namespace) {
        this.namespace = namespace;
        this.serviceIdxKey = namespace + ":" + SERVICE_IDX;
        this.instanceIdxKeyFormat = namespace + ":" + SERVICE_INSTANCE_IDX + ":%s";
        this.instanceKeyFormat = namespace + ":" + SERVICE_INSTANCE + ":%s";
        this.instanceKeyPrefix = namespace + ":svc_itc:";
        this.instanceKeyPatternOfServiceFormat = instanceKeyPrefix + "%s@*";
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    public String getServiceIdxKey() {
        return this.serviceIdxKey;
    }

    public String getInstanceIdxKey(String serviceId) {
        return Strings.lenientFormat(instanceIdxKeyFormat, serviceId);
    }

    public String getInstanceKey(String instanceId) {
        return Strings.lenientFormat(instanceKeyFormat, instanceId);
    }

    public String getInstanceKeyPatternOfService(String serviceId) {
        return Strings.lenientFormat(instanceKeyPatternOfServiceFormat, serviceId);
    }

    public String getInstanceIdOfKey(String instanceKey) {
        return instanceKey.substring(instanceKeyPrefix.length());
    }

}
