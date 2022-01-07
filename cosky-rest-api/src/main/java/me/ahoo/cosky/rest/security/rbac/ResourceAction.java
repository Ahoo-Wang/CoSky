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

package me.ahoo.cosky.rest.security.rbac;

import com.google.common.base.Objects;
import me.ahoo.cosky.core.Namespaced;

/**
 * @author ahoo wang
 */
public class ResourceAction implements Namespaced {
    private final String namespace;
    private final Action action;

    public ResourceAction(String namespace, Action action) {
        this.namespace = namespace;
        this.action = action;
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    public Action getAction() {
        return action;
    }

    public boolean check(ResourceAction requestAction) {
        if (!namespace.equals(requestAction.getNamespace())) {
            return false;
        }
        return action.check(requestAction.getAction());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceAction)) return false;
        ResourceAction that = (ResourceAction) o;
        return Objects.equal(namespace, that.namespace) && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(namespace, action);
    }

    @Override
    public String toString() {
        return "ResourceAction{" +
                "namespace='" + namespace + '\'' +
                ", action=" + action +
                '}';
    }
}
