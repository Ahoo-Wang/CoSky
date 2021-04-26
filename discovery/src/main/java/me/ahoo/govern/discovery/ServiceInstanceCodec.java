package me.ahoo.govern.discovery;


import lombok.var;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ahoo wang
 */
public final class ServiceInstanceCodec {
    private static final String METADATA_PREFIX = "_";
    private static final int METADATA_PREFIX_LENGTH = METADATA_PREFIX.length();
    private static final String[] EMPTY_STRING_ARRAY = {};
    private static final String INSTANCE_ID = "instanceId";
    private static final String SERVICE_ID = "serviceId";
    private static final String SCHEMA = "schema";
    private static final String IP = "ip";
    private static final String PORT = "port";
    private static final String WEIGHT = "weight";
    private static final String EPHEMERAL = "ephemeral";

    @Deprecated
    public static Map<String, String> encode(ServiceInstance serviceInstance) {
        var serviceInstanceData = new HashMap<String, String>();
        serviceInstanceData.put(INSTANCE_ID, serviceInstance.getInstanceId());
        serviceInstanceData.put(SERVICE_ID, serviceInstance.getServiceId());
        serviceInstanceData.put(SCHEMA, serviceInstance.getSchema());
        serviceInstanceData.put(IP, serviceInstance.getIp());
        serviceInstanceData.put(PORT, String.valueOf(serviceInstance.getPort()));
        serviceInstanceData.put(WEIGHT, String.valueOf(serviceInstance.getWeight()));
        serviceInstanceData.put(EPHEMERAL, String.valueOf(serviceInstance.isEphemeral()));
        serviceInstance.getMetadata().forEach((key, value) -> {
            var metadataKey = METADATA_PREFIX + key;
            serviceInstanceData.put(metadataKey, value);
        });
        return serviceInstanceData;
    }

    public static String[] encodeMetadata(Map<String, String> instanceMetadata) {
        if (instanceMetadata.isEmpty()) {
            return EMPTY_STRING_ARRAY;
        }
        String[] values = new String[instanceMetadata.size() * 2];
        var valueIdx = -1;
        for (Map.Entry<String, String> metadataKV : instanceMetadata.entrySet()) {
            values[++valueIdx] = METADATA_PREFIX + metadataKV.getKey();
            values[++valueIdx] = metadataKV.getValue();
        }
        return values;
    }

    public static ServiceInstance decode(List<String> instanceData) {
        ServiceInstance serviceInstance = new ServiceInstance();
        for (int i = 0; i < instanceData.size(); i = i + 2) {
            var key = instanceData.get(i);
            var value = instanceData.get(i + 1);
            switch (key) {
                case INSTANCE_ID: {
                    serviceInstance.setInstanceId(value);
                    break;
                }
                case SERVICE_ID: {
                    serviceInstance.setServiceId(value);
                    break;
                }
                case SCHEMA: {
                    serviceInstance.setSchema(value);
                    break;
                }
                case IP: {
                    serviceInstance.setIp(value);
                    break;
                }
                case PORT: {
                    serviceInstance.setPort(Integer.parseInt(value));
                    break;
                }
                case WEIGHT: {
                    serviceInstance.setWeight(Integer.parseInt(value));
                    break;
                }
                case EPHEMERAL: {
                    serviceInstance.setEphemeral(Boolean.parseBoolean(value));
                    break;
                }
                default: {
                    if (key.startsWith(METADATA_PREFIX)) {
                        var metadataKey = key.substring(METADATA_PREFIX_LENGTH);
                        serviceInstance.getMetadata().put(metadataKey, value);
                    }
                    break;
                }
            }
        }
        return serviceInstance;
    }

    @Deprecated
    public static ServiceInstance decode(Map<String, String> instanceData) {
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setInstanceId(instanceData.get(INSTANCE_ID));
        serviceInstance.setServiceId(instanceData.get(SERVICE_ID));
        serviceInstance.setSchema(instanceData.get(SCHEMA));
        serviceInstance.setIp(instanceData.get(IP));
        serviceInstance.setPort(Integer.parseInt(instanceData.get(PORT)));
        serviceInstance.setWeight(Integer.parseInt(instanceData.get(WEIGHT)));
        serviceInstance.setEphemeral(Boolean.parseBoolean(instanceData.get(EPHEMERAL)));
        instanceData.forEach((key, value) -> {
            if (key.startsWith(METADATA_PREFIX)) {
                var metadataKey = key.substring(METADATA_PREFIX_LENGTH);
                serviceInstance.getMetadata().put(metadataKey, value);
            }
        });
        return serviceInstance;
    }

}
