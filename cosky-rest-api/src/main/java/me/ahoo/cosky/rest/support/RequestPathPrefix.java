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

package me.ahoo.cosky.rest.support;


/**
 * @author ahoo wang
 */
public interface RequestPathPrefix {
    String V1 = "/v1/";

    //region Authenticate
    /**
     * /v1/auth
     */
    String AUTHENTICATE_PREFIX = V1 + "authenticate";
    //endregion
    //region user
    /**
     * /v1/users
     */
    String USERS_PREFIX = V1 + "users";

    /**
     * /v1/users/{username}
     */
    String USERS_USER = "/{username}";

    String USERS_USER_PASSWORD = USERS_USER + "/password";
    String USERS_USER_ROLE = USERS_USER + "/role";
    String USERS_USER_UNLOCK = USERS_USER + "/unlock";

    //endregion
    //region role
    /**
     * /v1/roles
     */
    String ROLES_PREFIX = V1 + "roles";
    String ROLES_ROLE = "/{roleName}";
    String ROLES_ROLE_BIND = ROLES_ROLE + "/bind";
    //endregion


    //region audit
    /**
     * /v1/audit
     */
    String AUDIT_LOG_PREFIX = V1 + "audit-log";
    //endregion

    //region namespaces
    /**
     * /v1/namespaces
     */
    String NAMESPACES_PREFIX = V1 + "namespaces";
    /**
     * /v1/namespaces/{namespace}
     */
    String NAMESPACES_NAMESPACE = "/{namespace}";

    /**
     * /v1/namespaces/{namespace}
     */
    String NAMESPACES_NAMESPACE_PREFIX = NAMESPACES_PREFIX + NAMESPACES_NAMESPACE;
    /**
     * /v1/namespaces/current
     */
    String NAMESPACES_CURRENT = "/current";
    /**
     * /v1/namespaces/current/{namespace}
     */
    String NAMESPACES_CURRENT_NAMESPACE = NAMESPACES_CURRENT + "/{namespace}";
    //endregion

    /**
     * /v1/namespaces/{namespace}
     */
    String STAT_PREFIX = NAMESPACES_NAMESPACE_PREFIX + "/stat";

    //region configs
    /**
     * /v1/namespaces/{namespace}/configs
     */
    String CONFIGS_PREFIX = NAMESPACES_NAMESPACE_PREFIX + "/configs";
    /**
     * /v1/namespaces/{namespace}/configs/{configId}/export
     */
    String CONFIGS_CONFIG_EXPORT = "/export";
    /**
     * /v1/namespaces/{namespace}/configs/{configId}
     */
    String CONFIGS_CONFIG = "{configId}";
    /**
     * /v1/namespaces/{namespace}/configs/{configId}/versions
     */
    String CONFIGS_CONFIG_VERSIONS = CONFIGS_CONFIG + "/versions";
    /**
     * /v1/namespaces/{namespace}/configs/{configId}/versions/{version}
     */
    String CONFIGS_CONFIG_VERSIONS_VERSION = CONFIGS_CONFIG_VERSIONS + "/{version}";
    /**
     * /v1/namespaces/{namespace}/configs/{configId}/to/{targetVersion}
     */
    String CONFIGS_CONFIG_TO = CONFIGS_CONFIG + "/to/{targetVersion}";

    //endregion
    //region services
    /**
     * /v1/namespaces/{namespace}/services/
     */
    String SERVICES_PREFIX = NAMESPACES_NAMESPACE_PREFIX + "/services";

    /**
     * /v1/namespaces/{namespace}/services/stats
     */
    String SERVICES_STATS = "/stats";

    /**
     * /v1/namespaces/{namespace}/services/{serviceId}
     */
    String SERVICES_SERVICE = "/{serviceId}";

    /**
     * /v1/namespaces/{namespace}/services/{serviceId}/lb
     */
    String SERVICES_LB = SERVICES_SERVICE + "/lb";
    /**
     * /v1/namespaces/{namespace}/services/{serviceId}/instances
     */
    String SERVICES_INSTANCES = SERVICES_SERVICE + "/instances";
    /**
     * /v1/namespaces/{namespace}/services/{serviceId}/instances/{instanceId}
     */
    String SERVICES_INSTANCES_INSTANCE = SERVICES_INSTANCES + "/{instanceId}";
    /**
     * /v1/namespaces/{namespace}/services/{serviceId}/instances/{instanceId}/metadata
     */
    String SERVICES_INSTANCES_INSTANCE_METADATA = SERVICES_INSTANCES_INSTANCE + "/metadata";
    //endregion
}
