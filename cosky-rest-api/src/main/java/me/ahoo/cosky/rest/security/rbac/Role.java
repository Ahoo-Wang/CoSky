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

package me.ahoo.cosky.rest.security.rbac;

import com.google.common.base.Objects;

import java.util.HashMap;
import java.util.Map;

/**
 * Role 1:n ResourceAction
 *
 * @author ahoo wang
 */
public class Role {
    public static final String ADMIN_ROLE = "admin";

    public static final Role ADMIN;

    static {
        ADMIN = new Role();
        ADMIN.setRoleName(ADMIN_ROLE);
    }

    private String roleName;
    private String desc;
    private Map<String, ResourceAction> resourceActionBind = new HashMap<>();

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public Map<String, ResourceAction> getResourceActionBind() {
        return resourceActionBind;
    }

    public void setResourceActionBind(Map<String, ResourceAction> resourceActionBind) {
        this.resourceActionBind = resourceActionBind;
    }

    public boolean check(ResourceAction requestAction) {
        ResourceAction resourceAction = resourceActionBind.get(requestAction.getNamespace());
        if (resourceAction == null) {
            return false;
        }
        return resourceAction.check(requestAction);
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
}
