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
package me.ahoo.cosky.rest.security.rbac

/**
 * Role 1:n ResourceAction.
 *
 * @author ahoo wang
 */
data class Role(
    var roleName: String,
    var desc: String,
    var resourceActionBind: Map<String, ResourceAction> = emptyMap()
) {
    fun check(requestAction: ResourceAction): Boolean {
        val resourceAction = resourceActionBind[requestAction.namespace] ?: return false
        return resourceAction.check(requestAction)
    }

    companion object {
        const val ADMIN_ROLE = "admin"
        const val ADMIN_ROLE_DESC = "System reserved role,Have the highest level of authority!"
        val ADMIN: Role = Role(ADMIN_ROLE, ADMIN_ROLE_DESC)
    }
}
