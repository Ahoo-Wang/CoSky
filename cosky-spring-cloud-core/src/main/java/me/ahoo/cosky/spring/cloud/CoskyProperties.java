package me.ahoo.cosky.spring.cloud;

import me.ahoo.cosky.core.Consts;
import me.ahoo.cosky.core.NamespacedProperties;
import me.ahoo.cosky.core.redis.RedisConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(CoskyProperties.PREFIX)
public class CoskyProperties extends NamespacedProperties {
    public static final String PREFIX = "spring.cloud." + Consts.COSKY;

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
