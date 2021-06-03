package me.ahoo.cosky.discovery.spring.cloud.registry;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.var;
import me.ahoo.cosky.discovery.*;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import me.ahoo.cosky.discovery.spring.cloud.discovery.ConditionalOnCoskyDiscoveryEnabled;
import me.ahoo.cosky.discovery.spring.cloud.discovery.CoskyDiscoveryAutoConfiguration;
import me.ahoo.cosky.spring.cloud.support.AppSupport;
import me.ahoo.cosky.spring.cloud.support.RedisClientSupport;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationAutoConfiguration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCoskyDiscoveryEnabled
@EnableConfigurationProperties(CoskyRegistryProperties.class)
@ConditionalOnProperty(value = "spring.cloud.service-registry.auto-registration.enabled", matchIfMissing = true)
@AutoConfigureBefore({AutoServiceRegistrationAutoConfiguration.class})
@AutoConfigureAfter({CoskyDiscoveryAutoConfiguration.class})
public class CoskyAutoServiceRegistrationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RegistryProperties registryProperties(
            CoskyRegistryProperties governRegistryProperties) {
        var registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(governRegistryProperties.getTtl());
        return registryProperties;
    }

    @Bean
    @Primary
    public RedisServiceRegistry redisServiceRegistry(RegistryProperties registryProperties,
                                                     AbstractRedisClient redisClient) {
        RedisClusterAsyncCommands<String, String> redisCommands = RedisClientSupport.getRedisCommands(redisClient);
        return new RedisServiceRegistry(registryProperties, redisCommands);
    }

    @Bean
    public RenewInstanceService renewInstanceService(CoskyRegistryProperties governRegistryProperties, RedisServiceRegistry redisServiceRegistry) {
        return new RenewInstanceService(governRegistryProperties.getRenew(), redisServiceRegistry);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(CoskyRegistration.class)
    public CoskyRegistration coskyRegistration(
            ApplicationContext context, CoskyRegistryProperties properties) {
        ServiceInstance serviceInstance = new ServiceInstance();
        serviceInstance.setMetadata(properties.getMetadata());

        if (Strings.isBlank(properties.getServiceId())) {
            String serviceId = AppSupport.getAppName(context.getEnvironment());
            serviceInstance.setServiceId(serviceId);
        } else {
            serviceInstance.setServiceId(properties.getServiceId());
        }

        if (Strings.isNotBlank(properties.getSchema())) {
            serviceInstance.setSchema(properties.getSchema());
        }

        if (Strings.isNotBlank(properties.getHost())) {
            serviceInstance.setHost(properties.getHost());
        }
        serviceInstance.setPort(properties.getPort());
        serviceInstance.setWeight(properties.getWeight());
        serviceInstance.setEphemeral(properties.isEphemeral());
        serviceInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(serviceInstance));
        return new CoskyRegistration(serviceInstance);
    }

    @Bean
    @Primary
    public CoskyServiceRegistry coskyServiceRegistry(ServiceRegistry serviceRegistry, RenewInstanceService renewInstanceService, CoskyRegistryProperties governRegistryProperties) {
        return new CoskyServiceRegistry(serviceRegistry, renewInstanceService, governRegistryProperties);
    }

    @Bean
    @Primary
    public CoskyAutoServiceRegistration governAutoServiceRegistration(
            CoskyServiceRegistry serviceRegistry,
            CoskyRegistration registration,
            AutoServiceRegistrationProperties autoServiceRegistrationProperties
    ) {
        return new CoskyAutoServiceRegistration(serviceRegistry, registration, autoServiceRegistrationProperties);
    }

    @Bean
    public CoskyAutoServiceRegistrationOfNoneWeb governAutoServiceRegistrationOfNoneWeb(
            CoskyServiceRegistry serviceRegistry,
            CoskyRegistration registration
    ) {
        return new CoskyAutoServiceRegistrationOfNoneWeb(serviceRegistry, registration);
    }
}