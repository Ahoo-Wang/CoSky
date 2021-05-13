package me.ahoo.govern.discovery;

/**
 * @author ahoo wang
 */
public class RenewProperties {

    private int initialDelay = 1;
    /**
     * renew period
     * {@link #period} must Less than {@link RegistryProperties#getInstanceTtl()}
     */
    private int period = 10;

    public int getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }
}
