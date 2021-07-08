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

import com.google.common.base.Objects;
import me.ahoo.cosky.core.Namespaced;

/**
 * @author ahoo wang
 */
public class NamespacedServiceId implements Namespaced {
    private final String namespace;
    private final String serviceId;

    public NamespacedServiceId(String namespace, String serviceId) {
        this.namespace = namespace;
        this.serviceId = serviceId;
    }

    public static NamespacedServiceId of(String namespace, String serviceId) {
        return new NamespacedServiceId(namespace, serviceId);
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public String getServiceId() {
        return serviceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NamespacedServiceId)) return false;
        NamespacedServiceId that = (NamespacedServiceId) o;
        return Objects.equal(namespace, that.namespace) && Objects.equal(serviceId, that.serviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, serviceId);
    }
}
