package me.ahoo.govern.config;

import com.google.common.base.Objects;

/**
 * @author ahoo wang
 */
public class Config extends ConfigVersion {

    private String data;
    /**
     * data hash
     */
    private String hash;
    private long createTime;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Config)) return false;
        if (!super.equals(o)) return false;
        Config config = (Config) o;
        return createTime == config.createTime && Objects.equal(data, config.data) && Objects.equal(hash, config.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), data, hash, createTime);
    }
}
