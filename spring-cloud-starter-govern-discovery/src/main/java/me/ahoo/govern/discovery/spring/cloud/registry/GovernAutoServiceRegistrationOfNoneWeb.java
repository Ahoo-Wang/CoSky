package me.ahoo.govern.discovery.spring.cloud.registry;

import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.util.Systems;
import me.ahoo.govern.discovery.InstanceIdGenerator;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;

import javax.annotation.PreDestroy;

/**
 * @author ahoo wang
 */
@Slf4j
public class GovernAutoServiceRegistrationOfNoneWeb implements ApplicationListener<ApplicationStartedEvent>, ApplicationContextAware {

    private final GovernServiceRegistry serviceRegistry;
    private final GovernRegistration registration;
    private boolean isWebApp;

    public GovernAutoServiceRegistrationOfNoneWeb(GovernServiceRegistry serviceRegistry,
                                                  GovernRegistration registration) {
        this.serviceRegistry = serviceRegistry;
        this.registration = registration;
    }

    /**
     * Handle an application event.
     *
     * @param event the event to respond to
     */
    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (isWebApp) {
            if (log.isInfoEnabled()) {
                log.info("onApplicationEvent - Ignoring registration service of WebServerApplicationContext");
            }
            return;
        }
        var serviceInstance = this.registration.of();
        if (serviceInstance.getPort() == 0) {
            /**
             * use PID as port
             */
            serviceInstance.setSchema("__");
            serviceInstance.setPort((int) Systems.getCurrentProcessId());
            serviceInstance.setInstanceId(InstanceIdGenerator.DEFAULT.generate(serviceInstance));
        }
        this.serviceRegistry.register(registration);
    }

    @PreDestroy
    public void destroy() {
        if (isWebApp) {
            return;
        }
        this.serviceRegistry.deregister(registration);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        isWebApp = applicationContext instanceof WebServerApplicationContext;
    }
}
