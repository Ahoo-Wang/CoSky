package me.ahoo.govern.disvoery.spring.cloud.registry;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import lombok.var;
import me.ahoo.govern.discovery.*;
import me.ahoo.govern.discovery.redis.RedisServiceRegistry;
import me.ahoo.govern.disvoery.spring.cloud.discovery.ConditionalOnGovernDiscoveryEnabled;
import me.ahoo.govern.disvoery.spring.cloud.discovery.GovernDiscoveryAutoConfiguration;
import me.ahoo.govern.spring.cloud.support.AppSupport;
import me.ahoo.govern.spring.cloud.support.RedisClientSupport;
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

/**
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnGovernDiscoveryEnabled
@EnableConfigurationProperties(GovernRegistryProperties.class)
@ConditionalOnProperty(value = "spring.cloud.service-registry.auto-registration.enabled", matchIfMissing = true)
@AutoConfigureBefore({AutoServiceRegistrationAutoConfiguration.class})
@AutoConfigureAfter({GovernDiscoveryAutoConfiguration.class})
public class GovernAutoServiceRegistrationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RegistryProperties registryProperties(
            GovernRegistryProperties governRegistryProperties) {
        var registryProperties = new RegistryProperties();
        registryProperties.setInstanceTtl(governRegistryProperties.getTtl());
        return registryProperties;
    }

    @Bean
    public RedisServiceRegistry redisServiceRegistry(RegistryProperties registryProperties,

                                                     AbstractRedisClient redisClient) {
        RedisClusterAsyncCommands<String, String> redisCommands = RedisClientSupport.getRedisCommands(redisClient);
        return new RedisServiceRegistry(registryProperties,  redisCommands);
    }

    @Bean
    public RenewInstanceService renewInstanceService(GovernRegistryProperties governRegistryProperties, RedisServiceRegistry redisServiceRegistry) {
        return new RenewInstanceService(governRegistryProperties.getRenew(), redisServiceRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(GovernRegistration.class)
    public GovernRegistration governRegistration(
            ApplicationContext context, GovernRegistryProperties properties) {
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

        if (Strings.isNotBlank(properties.getIp())) {
            serviceInstance.setIp(properties.getIp());
        }
        serviceInstance.setPort(properties.getPort());
        serviceInstance.setWeight(properties.getWeight());
        serviceInstance.setEphemeral(properties.isEphemeral());
        serviceInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(serviceInstance));
        return new GovernRegistration(serviceInstance);
    }

    @Bean
    public GovernServiceRegistry governServiceRegistry(ServiceRegistry serviceRegistry, RenewInstanceService renewInstanceService) {
        return new GovernServiceRegistry(serviceRegistry, renewInstanceService);
    }

    @Bean
    public GovernAutoServiceRegistration governAutoServiceRegistration(
            GovernServiceRegistry serviceRegistry,
            GovernRegistration registration,
            AutoServiceRegistrationProperties autoServiceRegistrationProperties
    ) {
        return new GovernAutoServiceRegistration(serviceRegistry, registration, autoServiceRegistrationProperties);
    }
}
