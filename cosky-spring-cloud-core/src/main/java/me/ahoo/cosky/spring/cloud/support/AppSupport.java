package me.ahoo.cosky.spring.cloud.support;

import org.springframework.core.env.Environment;

/**
 * @author ahoo wang
 */
public final class AppSupport {

    public final static String SPRING_APPLICATION_NAME = "spring.application.name";

    private AppSupport() {
    }

    public static String getAppName(Environment environment) {
        return environment.getProperty(SPRING_APPLICATION_NAME);
    }
}
