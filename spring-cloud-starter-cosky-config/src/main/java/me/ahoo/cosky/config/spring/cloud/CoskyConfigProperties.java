package me.ahoo.cosky.config.spring.cloud;

import me.ahoo.cosky.spring.cloud.CoskyProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

import static me.ahoo.cosky.config.spring.cloud.CoskyConfigProperties.PREFIX;

/**
 * @author ahoo wang
 */
@ConfigurationProperties(PREFIX)
public class CoskyConfigProperties {
    public static final String PREFIX = CoskyProperties.PREFIX + ".config";

    private boolean enabled = true;

    private String configId;

    private String fileExtension = "yaml";
    private Duration timeout = Duration.ofSeconds(2);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
