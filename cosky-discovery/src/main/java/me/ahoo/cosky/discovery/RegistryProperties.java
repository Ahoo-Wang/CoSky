package me.ahoo.cosky.discovery;

import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
public class RegistryProperties {
    /**
     * instance time to live
     */
    private int instanceTtl = (int) TimeUnit.MINUTES.toSeconds(1);

    public int getInstanceTtl() {
        return instanceTtl;
    }

    public void setInstanceTtl(int instanceTtl) {
        this.instanceTtl = instanceTtl;
    }
}
