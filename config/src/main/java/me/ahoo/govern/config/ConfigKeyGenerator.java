package me.ahoo.govern.config;

import com.google.common.base.Strings;
import lombok.var;
import me.ahoo.govern.core.Namespaced;

/**
 * @author ahoo wang
 */
public class ConfigKeyGenerator implements Namespaced {
    private final static String CONFIG_IDX = "cfg_idx";
    private final static String CONFIG_HISTORY_IDX = "cfg_htr_idx";
    private final static String CONFIG_HISTORY = "cfg_htr";
    private final static String CONFIG = "cfg";

    private final String namespace;
    /**
     * {namespace}:{@link #CONFIG_IDX}
     */
    private final String configIdxKey;
    /**
     * {namespace}:{@link #CONFIG_HISTORY_IDX}:{configId}
     */
    private final String configHistoryIdxKeyFormat;
    /**
     * {namespace}:{@link #CONFIG_HISTORY}:
     */
    private final String configHistoryKeyPrefix;
    /**
     * {namespace}:{@link #CONFIG_HISTORY}:{configId}:{version}
     */
    private final String configHistoryKeyFormat;
    /**
     * {namespace}:{@link #CONFIG}:
     */
    private final String configKeyPrefix;
    /**
     * {namespace}:{@link #CONFIG}:{configId}
     */
    private final String configKeyFormat;

    public ConfigKeyGenerator(String namespace) {
        this.namespace = namespace;
        this.configIdxKey = namespace + ":" + CONFIG_IDX;
        this.configHistoryIdxKeyFormat = namespace + ":" + CONFIG_HISTORY_IDX + ":%s";
        this.configHistoryKeyPrefix = namespace + ":" + CONFIG_HISTORY + ":";
        this.configHistoryKeyFormat = configHistoryKeyPrefix + "%s:%s";
        this.configKeyPrefix = namespace + ":" + CONFIG + ":";
        this.configKeyFormat = configKeyPrefix + "%s";
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    public String getConfigIdxKey() {
        return this.configIdxKey;
    }

    public String getConfigHistoryIdxKey(String configId) {
        return Strings.lenientFormat(configHistoryIdxKeyFormat, configId);
    }

    public String getConfigHistoryKey(String configId, Integer version) {
        return Strings.lenientFormat(configHistoryKeyFormat, configId, version);
    }

    public String getConfigKey(String configId) {
        return Strings.lenientFormat(configKeyFormat, configId);
    }

    public String getConfigIdOfKey(String configKey) {
        return configKey.substring(configKeyPrefix.length());
    }

    public ConfigVersion getConfigVersionOfHistoryKey(String configHistoryKey) {
        var configIdWithVersion = configHistoryKey.substring(configHistoryKeyPrefix.length());
        var configIdWithVersionSplit = configIdWithVersion.split(":");
        if (configIdWithVersionSplit.length != 2) {
            throw new IllegalArgumentException(Strings.lenientFormat("configHistoryKey:[%s] format error.", configHistoryKey));
        }
        var configVersion = new ConfigVersion();
        configVersion.setConfigId(configIdWithVersionSplit[0]);
        configVersion.setVersion(Integer.parseInt(configIdWithVersionSplit[1]));
        return configVersion;
    }
}
