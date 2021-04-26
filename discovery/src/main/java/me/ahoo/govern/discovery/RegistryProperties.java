package me.ahoo.govern.discovery;

import lombok.Getter;
import lombok.Setter;
import me.ahoo.govern.core.Consts;
import me.ahoo.govern.core.NamespacedProperties;

import java.util.concurrent.TimeUnit;

/**
 * @author ahoo wang
 */
public class RegistryProperties {
    /**
     * instance time to live
     */
    @Getter
    @Setter
    private int instanceTtl = (int) TimeUnit.MINUTES.toSeconds(1);

}
