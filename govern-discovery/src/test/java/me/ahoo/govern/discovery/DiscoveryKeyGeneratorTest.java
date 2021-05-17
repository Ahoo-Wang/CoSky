package me.ahoo.govern.discovery;

import lombok.var;
import me.ahoo.govern.core.Consts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class DiscoveryKeyGeneratorTest {

    @Test
    public void getServiceIdxKey() {
        var serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(Consts.GOVERN);
        Assertions.assertEquals("govern:svc_idx", serviceIdxKey);
    }

    @Test
    public void getServiceInstanceIdxKey() {
        var serviceIdxKey = DiscoveryKeyGenerator.getInstanceIdxKey(Consts.GOVERN, "order_service");
        Assertions.assertEquals("govern:svc_itc_idx:order_service", serviceIdxKey);
    }

    @Test
    public void getInstanceKey() {
        var instanceKey = DiscoveryKeyGenerator.getInstanceKey(Consts.GOVERN, "http#127.0.0.1#8080@order_service");
        Assertions.assertEquals("govern:svc_itc:http#127.0.0.1#8080@order_service", instanceKey);
    }
}
