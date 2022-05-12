package me.ahoo.cosky.config;

/**
 * ConfigData .
 *
 * @author ahoo wang
 */
public class TestData {
    private TestData() {
    }
    
    public static final String NAMESPACE = "ben_cfg";
    
    public static final String DATA = "spring:\n" +
        "  application:\n" +
        "    name: ${service.name:cosky-rest-api}\n" +
        "  cloud:\n" +
        "    cosky:\n" +
        "      namespace: ${cosky.namespace:cosky-{system}}\n" +
        "      config:\n" +
        "        config-id: ${spring.application.name}.yaml\n" +
        "    service-registry:\n" +
        "      auto-registration:\n" +
        "        enabled: ${cosky.auto-registry:true}\n" +
        "logging:\n" +
        "  file:\n" +
        "    name: logs/${spring.application.name}.log\n" +
        "\n";
}
