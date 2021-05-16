package me.ahoo.govern.discovery.spring.cloud.discovery;

import me.ahoo.govern.spring.cloud.GovernProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(GovernDiscoveryProperties.PREFIX)
public class GovernDiscoveryProperties {
    public static final String PREFIX = GovernProperties.PREFIX + ".discovery";
    private boolean enabled = true;
    private int order = 0;
    private Duration timeout = Duration.ofSeconds(2);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
