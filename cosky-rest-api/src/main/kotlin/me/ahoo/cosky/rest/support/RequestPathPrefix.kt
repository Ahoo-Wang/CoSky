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
package me.ahoo.cosky.rest.support

/**
 * Request Path Prefix.
 *
 * @author ahoo wang
 */
object RequestPathPrefix {
    /**
     * /dashboard .
     */
    const val DASHBOARD = "/dashboard/"
    const val SWAGGER_UI = "/swagger"
    const val SWAGGER_UI_RESOURCE = "/webjars/swagger"
    const val V1 = "/v1/"
    //region Authenticate
    /**
     * /v1/auth .
     */
    const val AUTHENTICATE_PREFIX = V1 + "authenticate"
    //endregion
    //region user
    /**
     * /v1/users .
     */
    const val USERS_PREFIX = V1 + "users"

    /**
     * /v1/users/{username} .
     */
    const val USERS_USER = "/{username}"
    const val USERS_USER_PASSWORD = USERS_USER + "/password"
    const val USERS_USER_ROLE = USERS_USER + "/role"
    const val USERS_USER_UNLOCK = USERS_USER + "/unlock"
    //endregion
    //region role
    /**
     * /v1/roles .
     */
    const val ROLES_PREFIX = V1 + "roles"
    const val ROLES_ROLE = "/{roleName}"
    const val ROLES_ROLE_BIND = ROLES_ROLE + "/bind"
    //endregion
    //region audit
    /**
     * /v1/audit .
     */
    const val AUDIT_LOG_PREFIX = V1 + "audit-log"
    //endregion
    //region namespaces
    /**
     * /v1/namespaces .
     */
    const val NAMESPACES_PREFIX = V1 + "namespaces"

    /**
     * /v1/namespaces/{namespace} .
     */
    const val NAMESPACES_NAMESPACE = "/{namespace}"

    /**
     * /v1/namespaces/{namespace} .
     */
    const val NAMESPACES_NAMESPACE_PREFIX = NAMESPACES_PREFIX + NAMESPACES_NAMESPACE

    /**
     * /v1/namespaces/current .
     */
    const val NAMESPACES_CURRENT = "/current"

    /**
     * /v1/namespaces/current/{namespace} .
     */
    const val NAMESPACES_CURRENT_NAMESPACE = NAMESPACES_CURRENT + "/{namespace}"
    //endregion
    /**
     * /v1/namespaces/{namespace} .
     */
    const val STAT_PREFIX = "$NAMESPACES_NAMESPACE_PREFIX/stat"
    //region configs
    /**
     * /v1/namespaces/{namespace}/configs .
     */
    const val CONFIGS_PREFIX = "$NAMESPACES_NAMESPACE_PREFIX/configs"

    /**
     * /v1/namespaces/{namespace}/configs/{configId}/export .
     */
    const val CONFIGS_CONFIG_EXPORT = "/export"

    /**
     * /v1/namespaces/{namespace}/configs/{configId} .
     */
    const val CONFIGS_CONFIG = "{configId}"

    /**
     * /v1/namespaces/{namespace}/configs/{configId}/versions .
     */
    const val CONFIGS_CONFIG_VERSIONS = "$CONFIGS_CONFIG/versions"

    /**
     * /v1/namespaces/{namespace}/configs/{configId}/versions/{version} .
     */
    const val CONFIGS_CONFIG_VERSIONS_VERSION = "$CONFIGS_CONFIG_VERSIONS/{version}"

    /**
     * /v1/namespaces/{namespace}/configs/{configId}/to/{targetVersion} .
     */
    const val CONFIGS_CONFIG_TO = "$CONFIGS_CONFIG/to/{targetVersion}"
    //endregion
    //region services
    /**
     * /v1/namespaces/{namespace}/services/ .
     */
    const val SERVICES_PREFIX = "$NAMESPACES_NAMESPACE_PREFIX/services"

    /**
     * /v1/namespaces/{namespace}/services/stats .
     */
    const val SERVICES_STATS = "/stats"

    /**
     * /v1/namespaces/{namespace}/services/{serviceId} .
     */
    const val SERVICES_SERVICE = "/{serviceId}"

    /**
     * /v1/namespaces/{namespace}/services/{serviceId}/lb .
     */
    const val SERVICES_LB = "$SERVICES_SERVICE/lb"

    /**
     * /v1/namespaces/{namespace}/services/{serviceId}/instances .
     */
    const val SERVICES_INSTANCES = "$SERVICES_SERVICE/instances"

    /**
     * /v1/namespaces/{namespace}/services/{serviceId}/instances/{instanceId} .
     */
    const val SERVICES_INSTANCES_INSTANCE = "$SERVICES_INSTANCES/{instanceId}"

    /**
     * /v1/namespaces/{namespace}/services/{serviceId}/instances/{instanceId}/metadata .
     */
    const val SERVICES_INSTANCES_INSTANCE_METADATA = "$SERVICES_INSTANCES_INSTANCE/metadata"
//endregion
}
