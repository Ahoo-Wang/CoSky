/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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


import lombok.var;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author ahoo wang
 */
public final class ServiceInstanceCodec {
    private static final String SYSTEM_METADATA_PREFIX = "__";
    private static final String METADATA_PREFIX = "_";
    private static final int METADATA_PREFIX_LENGTH = METADATA_PREFIX.length();
    private static final String[] EMPTY_STRING_ARRAY = {};
    private static final String INSTANCE_ID = "instanceId";
    private static final String SERVICE_ID = "serviceId";
    private static final String SCHEMA = "schema";
    private static final String HOST = "host";
    private static final String PORT = "port";
    private static final String WEIGHT = "weight";
    private static final String EPHEMERAL = "ephemeral";
    private static final String TTL_AT = "ttl_at";

    @Deprecated
    public static Map<String, String> encode(ServiceInstance serviceInstance) {
        var serviceInstanceData = new HashMap<String, String>();
        serviceInstanceData.put(INSTANCE_ID, serviceInstance.getInstanceId());
        serviceInstanceData.put(SERVICE_ID, serviceInstance.getServiceId());
        serviceInstanceData.put(SCHEMA, serviceInstance.getSchema());
        serviceInstanceData.put(HOST, serviceInstance.getHost());
        serviceInstanceData.put(PORT, String.valueOf(serviceInstance.getPort()));
        serviceInstanceData.put(WEIGHT, String.valueOf(serviceInstance.getWeight()));
        serviceInstanceData.put(EPHEMERAL, String.valueOf(serviceInstance.isEphemeral()));
        serviceInstance.getMetadata().forEach((key, value) -> {
            var metadataKey = METADATA_PREFIX + key;
            serviceInstanceData.put(metadataKey, value);
        });
        return serviceInstanceData;
    }

    public static String[] encodeMetadata(String[] preArgs, Map<String, String> instanceMetadata) {
        if (instanceMetadata.isEmpty()) {
            return preArgs;
        }
        String[] values = new String[preArgs.length + instanceMetadata.size() * 2];
        System.arraycopy(preArgs, 0, values, 0, preArgs.length);
        var valueIdx = preArgs.length - 1;
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
                case HOST: {
                    serviceInstance.setHost(value);
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
                case TTL_AT: {
                    serviceInstance.setTtlAt(Integer.parseInt(value));
                    break;
                }
                default: {
                    if (key.startsWith(SYSTEM_METADATA_PREFIX)) {
                        break;
                    }
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
        serviceInstance.setHost(instanceData.get(HOST));
        serviceInstance.setPort(Integer.parseInt(instanceData.get(PORT)));
        serviceInstance.setWeight(Integer.parseInt(instanceData.get(WEIGHT)));
        serviceInstance.setEphemeral(Boolean.parseBoolean(instanceData.get(EPHEMERAL)));
        if (instanceData.containsKey(TTL_AT)) {
            serviceInstance.setTtlAt(Integer.parseInt(instanceData.get(TTL_AT)));
        }
        instanceData.forEach((key, value) -> {
            if (key.startsWith(METADATA_PREFIX)) {
                var metadataKey = key.substring(METADATA_PREFIX_LENGTH);
                serviceInstance.getMetadata().put(metadataKey, value);
            }
        });
        return serviceInstance;
    }

}
