package me.ahoo.govern.discovery;

/**
 * @author ahoo wang
 */
public final class TestServiceInstance {
    public final static ServiceInstance TEST_INSTANCE = new ServiceInstance();
    public final static ServiceInstance TEST_FIXED_INSTANCE = new ServiceInstance();

    static {
        TEST_INSTANCE.setServiceId("test_service");
        TEST_INSTANCE.setSchema("http");
        TEST_INSTANCE.setIp("127.0.0.1");
        TEST_INSTANCE.setPort(8080);
        TEST_INSTANCE.setInstanceId(InstanceIdGenerator.DEFAULT.generate(TEST_INSTANCE));
        TEST_INSTANCE.getMetadata().put("from", "test");

        TEST_FIXED_INSTANCE.setServiceId("test_fixed_service");
        TEST_FIXED_INSTANCE.setSchema("http");
        TEST_FIXED_INSTANCE.setIp("127.0.0.2");
        TEST_FIXED_INSTANCE.setPort(8080);
        TEST_FIXED_INSTANCE.setInstanceId(InstanceIdGenerator.DEFAULT.generate(TEST_FIXED_INSTANCE));
        TEST_FIXED_INSTANCE.setEphemeral(false);
        TEST_FIXED_INSTANCE.getMetadata().put("from", "test");
    }
}
