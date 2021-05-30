package me.ahoo.cosky.config;

import com.google.common.base.Objects;

/**
 * @author ahoo wang
 */
public class ConfigVersion {
    private String configId;
    private int version;

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigVersion that = (ConfigVersion) o;
        return version == that.version && Objects.equal(configId, that.configId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(configId, version);
    }
}
