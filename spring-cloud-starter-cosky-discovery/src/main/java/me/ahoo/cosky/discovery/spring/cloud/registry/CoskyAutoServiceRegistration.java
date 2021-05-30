package me.ahoo.cosky.discovery.spring.cloud.registry;

import lombok.var;
import me.ahoo.cosky.discovery.InstanceIdGenerator;
import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;

/**
 * @author ahoo wang
 */
public class CoskyAutoServiceRegistration extends AbstractAutoServiceRegistration<CoskyRegistration> {

    private final CoskyRegistration registration;
    private final AutoServiceRegistrationProperties autoServiceRegistrationProperties;

    protected CoskyAutoServiceRegistration(CoskyServiceRegistry serviceRegistry,
                                           CoskyRegistration registration,
                                           AutoServiceRegistrationProperties autoServiceRegistrationProperties) {
        super(serviceRegistry, autoServiceRegistrationProperties);
        this.registration = registration;
        this.autoServiceRegistrationProperties = autoServiceRegistrationProperties;
    }

    /**
     * @return The object used to configure the registration.
     */
    @Override
    protected Object getConfiguration() {
        return autoServiceRegistrationProperties;
    }

    @Override
    protected void register() {
        if (this.registration.getPort() == 0) {
            this.registration.setPort(getPort().get());
            var serviceInstance = this.registration.of();
            serviceInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(serviceInstance));
        }
        super.register();
    }

    /**
     * @return True, if this is enabled.
     */
    @Override
    protected boolean isEnabled() {
        return autoServiceRegistrationProperties.isEnabled();
    }

    @Override
    protected CoskyRegistration getRegistration() {
        return registration;
    }

    @Override
    protected CoskyRegistration getManagementRegistration() {
        return null;
    }
}
