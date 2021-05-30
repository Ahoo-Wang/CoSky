package me.ahoo.cosky.core;

/**
 * @author ahoo wang
 */
public class RedisConfig {
    private String url;
    private RedisMode mode;
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public RedisMode getMode() {
        return mode;
    }

    public void setMode(RedisMode mode) {
        this.mode = mode;
    }

    public enum RedisMode {
        STANDALONE,
        CLUSTER
    }
}
