package me.ahoo.cosky.discovery.spring.cloud.discovery.ribbon;

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
@ConditionalOnProperty(value = "ribbon.cosky.enabled", matchIfMissing = true)
public @interface ConditionalOnCoskyRibbon {
}
