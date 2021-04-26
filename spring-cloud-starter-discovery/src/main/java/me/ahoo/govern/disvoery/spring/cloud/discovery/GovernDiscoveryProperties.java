package me.ahoo.govern.disvoery.spring.cloud.discovery;

import me.ahoo.govern.spring.cloud.GovernProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(GovernDiscoveryProperties.PREFIX)
public class GovernDiscoveryProperties  {
    public static final String PREFIX =  GovernProperties.PREFIX + ".discovery";
    private boolean enabled = true;
    private int order = 0;

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

}
