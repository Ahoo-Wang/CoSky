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

package me.ahoo.cosky.rest.dto.role;

import java.util.Set;

/**
 * @author ahoo wang
 */
public class SaveRoleRequest {

    private String desc;
   private Set<ResourceActionDto> resourceActionBind;

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public Set<ResourceActionDto> getResourceActionBind() {
        return resourceActionBind;
    }

    public void setResourceActionBind(Set<ResourceActionDto> resourceActionBind) {
        this.resourceActionBind = resourceActionBind;
    }
}
