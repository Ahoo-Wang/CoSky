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

package me.ahoo.cosky.discovery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Service Instance.
 *
 * @author ahoo wang
 */
public class ServiceInstance extends Instance {
    
    public static final ServiceInstance NOT_FOUND = new ServiceInstance();
    private int weight = 1;
    private boolean ephemeral = true;
    private volatile long ttlAt = -1;
    private Map<String, String> metadata = new LinkedHashMap<>();
    
    public int getWeight() {
        return this.weight;
    }
    
    public boolean isEphemeral() {
        return ephemeral;
    }
    
    public Map<String, String> getMetadata() {
        return this.metadata;
    }
    
    public void setWeight(int weight) {
        this.weight = weight;
    }
    
    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public long getTtlAt() {
        return ttlAt;
    }
    
    public void setTtlAt(long ttlAt) {
        this.ttlAt = ttlAt;
    }
    
    public boolean isExpired() {
        if (!ephemeral) {
            return false;
        }
        long nowTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        return ttlAt < nowTimeSeconds;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceInstance)) {
            return false;
        }
        return super.equals(o);
    }
    
    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
