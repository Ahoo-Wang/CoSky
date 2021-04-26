package me.ahoo.govern.disvoery.spring.cloud.registry;

import lombok.var;
import me.ahoo.govern.discovery.InstanceIdGenerator;
import org.springframework.cloud.client.serviceregistry.AbstractAutoServiceRegistration;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationProperties;
import org.springframework.cloud.client.serviceregistry.Registration;
import org.springframework.cloud.client.serviceregistry.ServiceRegistry;

/**
 * @author ahoo wang
 */
public class GovernAutoServiceRegistration extends AbstractAutoServiceRegistration<GovernRegistration> {

    private final GovernRegistration registration;
    private final AutoServiceRegistrationProperties autoServiceRegistrationProperties;

    protected GovernAutoServiceRegistration(GovernServiceRegistry serviceRegistry,
                                            GovernRegistration registration,
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
    protected GovernRegistration getRegistration() {
        return registration;
    }

    @Override
    protected GovernRegistration getManagementRegistration() {
        return null;
    }
}
