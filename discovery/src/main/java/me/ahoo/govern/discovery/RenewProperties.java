package me.ahoo.govern.discovery;

import lombok.Getter;
import lombok.Setter;

/**
 * @author ahoo wang
 */
public class RenewProperties {
    @Getter
    @Setter
    private int initialDelay = 1;
    @Getter
    @Setter
    private int period = 10;
}
