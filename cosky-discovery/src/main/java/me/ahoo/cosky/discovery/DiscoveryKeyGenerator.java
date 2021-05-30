package me.ahoo.cosky.discovery;

import com.google.common.base.Strings;
import lombok.var;
import me.ahoo.cosky.core.Consts;

/**
 * @author ahoo wang
 */
public final class DiscoveryKeyGenerator {

    private DiscoveryKeyGenerator() {
    }

    private final static String SERVICE_IDX = "svc_idx";
    private final static String SERVICE_STAT = "svc_stat";
    private final static String SERVICE_INSTANCE_IDX = "svc_itc_idx";
    private final static String SERVICE_INSTANCE = "svc_itc";

    /**
     * {namespace}:{@link #SERVICE_IDX}
     */
    private static final String serviceIdxKeyFormat = "%s:" + SERVICE_IDX;
    /**
     * {namespace}:{@link #SERVICE_STAT}
     */
    private static final String serviceStatKeyFormat = "%s:" + SERVICE_STAT;
    /**
     * {namespace}:{@link #SERVICE_INSTANCE_IDX}:{serviceId}
     */
    private static final String instanceIdxKeyFormat = "%s:" + SERVICE_INSTANCE_IDX + ":%s";
    ;

    /**
     * {namespace}:{@link #SERVICE_INSTANCE}:{instanceId}
     */
    private static final String instanceKeyFormat = "%s:" + SERVICE_INSTANCE + ":%s";
    /**
     * {namespace}:{@link #SERVICE_INSTANCE}:
     */
    private static final String instanceKeyPrefixFormat = "%s:svc_itc:";

    private static final String instanceKeyPatternOfNamespaceFormat = instanceKeyPrefixFormat + "*";

    private static final String instanceKeyPatternOfServiceFormat = instanceKeyPrefixFormat + "%s@*";

    public static String getServiceIdxKey(String namespace) {
        return Strings.lenientFormat(serviceIdxKeyFormat, namespace);
    }

    public static String getServiceStatKey(String namespace) {
        return Strings.lenientFormat(serviceStatKeyFormat, namespace);
    }

    public static String getNamespaceOfKey(String key) {
        var firstSplitIdx = key.indexOf(Consts.KEY_SEPARATOR);
        return key.substring(0, firstSplitIdx);
    }

    public static String getInstanceIdxKey(String namespace, String serviceId) {
        return Strings.lenientFormat(instanceIdxKeyFormat, namespace, serviceId);
    }

    public static String getInstanceKey(String namespace, String instanceId) {
        return Strings.lenientFormat(instanceKeyFormat, namespace, instanceId);
    }

    public static String getInstanceKeyPatternOfNamespace(String namespace) {
        return Strings.lenientFormat(instanceKeyPatternOfNamespaceFormat, namespace);
    }

    public static String getInstanceKeyPatternOfService(String namespace, String serviceId) {
        return Strings.lenientFormat(instanceKeyPatternOfServiceFormat, namespace, serviceId);
    }

    public static String getInstanceIdOfKey(String namespace, String instanceKey) {
        var instanceKeyPrefix = Strings.lenientFormat(instanceKeyPrefixFormat, namespace);
        return instanceKey.substring(instanceKeyPrefix.length());
    }

}
