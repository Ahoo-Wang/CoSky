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

import me.ahoo.cosky.core.Namespaced;

import com.google.common.base.Objects;

/**
 * Namespaced InstanceId.
 *
 * @author ahoo wang
 */
public class NamespacedInstanceId implements Namespaced {
    private final String namespace;
    private final String instanceId;
    
    public NamespacedInstanceId(String namespace, String instanceId) {
        this.namespace = namespace;
        this.instanceId = instanceId;
    }
    
    public static NamespacedInstanceId of(String namespace, String instanceId) {
        return new NamespacedInstanceId(namespace, instanceId);
    }
    
    @Override
    public String getNamespace() {
        return namespace;
    }
    
    public String getInstanceId() {
        return instanceId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NamespacedInstanceId)) {
            return false;
        }
        NamespacedInstanceId that = (NamespacedInstanceId) o;
        return Objects.equal(namespace, that.namespace) && Objects.equal(instanceId, that.instanceId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, instanceId);
    }
    
    @Override
    public String toString() {
        return "NamespacedInstanceId{"
            + "namespace='" + namespace + '\''
            + ", instanceId='" + instanceId + '\''
            + '}';
    }
}
