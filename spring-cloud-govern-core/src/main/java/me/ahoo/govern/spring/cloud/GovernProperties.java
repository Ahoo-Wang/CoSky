package me.ahoo.govern.spring.cloud;

import me.ahoo.govern.core.Consts;
import me.ahoo.govern.core.NamespacedProperties;
import me.ahoo.govern.core.RedisConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(GovernProperties.PREFIX)
public class GovernProperties extends NamespacedProperties {
    public static final String PREFIX = "spring.cloud." + Consts.GOVERN;

    private boolean enabled = true;

    @NestedConfigurationProperty
    private RedisConfig redis = new RedisConfig();

    public RedisConfig getRedis() {
        return redis;
    }

    public void setRedis(RedisConfig redis) {
        this.redis = redis;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
