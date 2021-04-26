package me.ahoo.govern.discovery;

import lombok.var;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class DiscoveryKeyGeneratorTest {
    private DiscoveryKeyGenerator keyGenerator;

    @BeforeEach
    public void init() {
        this.keyGenerator = new DiscoveryKeyGenerator("govern");
    }

    @Test
    public void getServiceIdxKey() {
        var serviceIdxKey = keyGenerator.getServiceIdxKey();
        Assertions.assertEquals("govern:svc_idx", serviceIdxKey);
    }

    @Test
    public void getServiceInstanceIdxKey() {
        var serviceIdxKey = keyGenerator.getInstanceIdxKey("order_service");
        Assertions.assertEquals("govern:svc_itc_idx:order_service", serviceIdxKey);
    }

    @Test
    public void getInstanceKey() {
        var instanceKey = keyGenerator.getInstanceKey("http#127.0.0.1#8080@order_service");
        Assertions.assertEquals("govern:svc_itc:http#127.0.0.1#8080@order_service", instanceKey);
    }
}
