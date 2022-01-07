/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosky.discovery.spring.cloud.registry;

import me.ahoo.cosky.discovery.InstanceIdGenerator;
import me.ahoo.cosky.discovery.ServiceInstance;
import me.ahoo.cosky.discovery.ServiceInstanceContext;
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
        ServiceInstance serviceInstance = this.registration.of();
        if (this.registration.getPort() == 0) {
            this.registration.setPort(getPort().get());
            serviceInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(serviceInstance));
        }
        ServiceInstanceContext.CURRENT.setServiceInstance(serviceInstance);
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
