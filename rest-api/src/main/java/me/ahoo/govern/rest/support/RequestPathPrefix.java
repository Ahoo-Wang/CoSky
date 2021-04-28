package me.ahoo.govern.rest.support;

/**
 * @author ahoo wang
 */
public interface RequestPathPrefix {
    String V1 = "/v1/";
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
    //region configs
    /**
     * /v1/namespaces/{namespace}/configs
     */
    String CONFIGS_PREFIX = NAMESPACES_NAMESPACE_PREFIX + "/configs";
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
     * /v1/namespaces/{namespace}/services/{serviceId}/instances
     */
    String SERVICES_INSTANCES = "/{serviceId}/instances";
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
