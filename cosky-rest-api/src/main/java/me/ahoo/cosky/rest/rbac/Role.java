/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosky.rest.rbac;

import com.google.common.base.Objects;

import java.util.Set;

/**
 * @author ahoo wang
 */
public class Role {

    private String roleName;

    private Set<ResourceAction> resourceActionBind;

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Set<ResourceAction> getResourceActionBind() {
        return resourceActionBind;
    }

    public void setResourceActionBind(Set<ResourceAction> resourceActionBind) {
        this.resourceActionBind = resourceActionBind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role)) return false;
        Role role = (Role) o;
        return Objects.equal(roleName, role.roleName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(roleName);
    }

    public static class ResourceAction {
        private String namespace;
        private Action action;

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public Action getAction() {
            return action;
        }

        public void setAction(Action action) {
            this.action = action;
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

    public enum Action {
        READ("r"),
        WRITE("w"),
        READ_WRITE("rw");

        private final String value;

        Action(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        Action of(String value) {
            switch (value) {
                case "r": {
                    return READ;
                }
                case "w": {
                    return WRITE;
                }
                case "rw": {
                    return READ_WRITE;
                }
                default:
                    throw new IllegalStateException("Unexpected value: " + value);
            }
        }
    }

}
