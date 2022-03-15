/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.discovery.spring.cloud.registry;

import me.ahoo.cosky.discovery.spring.cloud.support.StatusConstants;
import me.ahoo.cosky.discovery.RenewProperties;
import me.ahoo.cosky.discovery.spring.cloud.discovery.CoskyDiscoveryProperties;

import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.commons.util.InetUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cosky Registry Properties.
 *
 * @author ahoo wang
 */
@ConfigurationProperties(CoskyRegistryProperties.PREFIX)
public class CoskyRegistryProperties {
    public static final String PREFIX = CoskyDiscoveryProperties.PREFIX + ".registry";
    private final InetUtils.HostInfo hostInfo;
    private String serviceId;
    private String schema = "http";
    private String host;
    private int port;
    
    private int weight = 1;
    private boolean ephemeral = true;
    private String initialStatus = StatusConstants.STATUS_UP;
    private Map<String, String> metadata = new HashMap<>();
    
    private int ttl = 60;
    
    private Boolean secure;
    
    private RenewProperties renew = new RenewProperties();
    
    private Duration timeout = Duration.ofSeconds(2);
    
    public CoskyRegistryProperties(InetUtils inetUtils) {
        this.hostInfo = inetUtils.findFirstNonLoopbackHostInfo();
        this.host = this.hostInfo.getIpAddress();
        metadata.put(StatusConstants.INSTANCE_STATUS_KEY, initialStatus);
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
    
    public String getSchema() {
        return schema;
    }
    
    public void setSchema(String schema) {
        this.schema = schema;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    
    public int getWeight() {
        return weight;
    }
    
    public void setWeight(int weight) {
        this.weight = weight;
    }
    
    
    public boolean isEphemeral() {
        return ephemeral;
    }
    
    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }
    
    public String getInitialStatus() {
        return initialStatus;
    }
    
    public void setInitialStatus(String initialStatus) {
        this.initialStatus = initialStatus;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public Boolean getSecure() {
        return secure;
    }
    
    public void setSecure(Boolean secure) {
        this.secure = secure;
        if (Strings.isBlank(schema)) {
            schema = secure ? "http" : "https";
        }
    }
    
    public int getTtl() {
        return ttl;
    }
    
    public void setTtl(int ttl) {
        this.ttl = ttl;
    }
    
    public RenewProperties getRenew() {
        return renew;
    }
    
    public void setRenew(RenewProperties renew) {
        this.renew = renew;
    }
    
    public Duration getTimeout() {
        return timeout;
    }
    
    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
    
}
