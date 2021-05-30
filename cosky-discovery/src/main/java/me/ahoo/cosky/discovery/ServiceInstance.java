package me.ahoo.cosky.discovery;

import lombok.var;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
public class ServiceInstance extends Instance {

    public static final ServiceInstance NOT_FOUND = new ServiceInstance();
    private int weight = 1;
    private boolean ephemeral = true;
    private long ttlAt = -1;
    private Map<String, String> metadata = new LinkedHashMap<>();

    public int getWeight() {
        return this.weight;
    }

    public boolean isEphemeral() {
        return ephemeral;
    }

    public Map<String, String> getMetadata() {
        return this.metadata;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public void setEphemeral(boolean ephemeral) {
        this.ephemeral = ephemeral;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public long getTtlAt() {
        return ttlAt;
    }

    public void setTtlAt(long ttlAt) {
        this.ttlAt = ttlAt;
    }

    public boolean isExpired() {
        if (!ephemeral) {
            return false;
        }
        var nowTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        return ttlAt < nowTimeSeconds;
    }
}
