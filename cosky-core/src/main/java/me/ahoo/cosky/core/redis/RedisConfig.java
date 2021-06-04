package me.ahoo.cosky.core.redis;

/**
 * @author ahoo wang
 */
public class RedisConfig {
    private String url;
    private RedisMode mode;
    private ReadFrom readFrom;

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

    public ReadFrom getReadFrom() {
        return readFrom;
    }

    public void setReadFrom(ReadFrom readFrom) {
        this.readFrom = readFrom;
    }

    public enum RedisMode {
        STANDALONE,
        CLUSTER
    }

    public enum ReadFrom {
        MASTER,
        MASTER_PREFERRED,
        UPSTREAM,
        UPSTREAM_PREFERRED,
        REPLICA_PREFERRED,
        REPLICA,
        NEAREST,
        ANY,
        ANY_REPLICA
    }
}
