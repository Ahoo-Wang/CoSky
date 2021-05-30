package me.ahoo.cosky.discovery;

import lombok.var;
import me.ahoo.cosky.core.Consts;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class DiscoveryKeyGeneratorTest {

    @Test
    public void getServiceIdxKey() {
        var serviceIdxKey = DiscoveryKeyGenerator.getServiceIdxKey(Consts.COSKY);
        Assertions.assertEquals("cosky:svc_idx", serviceIdxKey);
    }

    @Test
    public void getServiceInstanceIdxKey() {
        var serviceIdxKey = DiscoveryKeyGenerator.getInstanceIdxKey(Consts.COSKY, "order_service");
        Assertions.assertEquals("cosky:svc_itc_idx:order_service", serviceIdxKey);
    }

    @Test
    public void getInstanceKey() {
        var instanceKey = DiscoveryKeyGenerator.getInstanceKey(Consts.COSKY, "http#127.0.0.1#8080@order_service");
        Assertions.assertEquals("cosky:svc_itc:http#127.0.0.1#8080@order_service", instanceKey);
    }
}
