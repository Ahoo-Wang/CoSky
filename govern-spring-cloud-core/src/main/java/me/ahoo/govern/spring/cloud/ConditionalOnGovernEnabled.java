package me.ahoo.govern.spring.cloud;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author ahoo wang
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(value = ConditionalOnGovernEnabled.ENABLED_KEY, matchIfMissing = true)
public @interface ConditionalOnGovernEnabled {
    String ENABLED_KEY = GovernProperties.PREFIX + ".enabled";
}
