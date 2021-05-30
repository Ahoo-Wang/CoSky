package me.ahoo.cosky.discovery;

import lombok.var;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author ahoo wang
 */
public class InstanceIdGeneratorTest {
    @Test
    public void generate() {
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setServiceId("order_service");
        serviceInstance.setSchema("http");
        serviceInstance.setHost("127.0.0.1");
        serviceInstance.setPort(8080);

        String expected = "order_service@http#127.0.0.1#8080";
        var actual = InstanceIdGenerator.DEFAULT.generate(serviceInstance);

        Assertions.assertEquals(expected, actual);
    }

    @Test
    public void of() {

        String instanceId = "order_service@http#127.0.0.1#8080";
        var instance = InstanceIdGenerator.DEFAULT.of(instanceId);

        Assertions.assertEquals(instanceId, instance.getInstanceId());
        Assertions.assertEquals("order_service", instance.getServiceId());
        Assertions.assertEquals("http", instance.getSchema());
        Assertions.assertEquals("127.0.0.1", instance.getHost());
        Assertions.assertEquals(8080, instance.getPort());

    }


}
