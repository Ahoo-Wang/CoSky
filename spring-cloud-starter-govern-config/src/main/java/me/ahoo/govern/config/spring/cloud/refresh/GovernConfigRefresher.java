package me.ahoo.govern.config.spring.cloud.refresh;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.govern.config.ConfigChangedListener;
import me.ahoo.govern.config.ConfigListenable;
import me.ahoo.govern.config.NamespacedConfigId;
import me.ahoo.govern.config.spring.cloud.GovernConfigProperties;
import me.ahoo.govern.spring.cloud.GovernProperties;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.endpoint.event.RefreshEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;

/**
 * @author ahoo wang
 */
@Slf4j
public class GovernConfigRefresher implements ApplicationListener<ApplicationReadyEvent>, ApplicationContextAware {
    private ApplicationContext applicationContext;
    private final ConfigListenable configListenable;
    private final GovernProperties governProperties;
    private final GovernConfigProperties configProperties;
    private final Listener listener;

    public GovernConfigRefresher(
            GovernProperties governProperties,
            GovernConfigProperties configProperties,
            ConfigListenable configListenable) {
        this.configListenable = configListenable;
        this.governProperties = governProperties;
        this.configProperties = configProperties;
        this.listener = new Listener();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Handle an application event.
     *
     * @param event the event to respond to
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        configListenable.addListener(NamespacedConfigId.of(governProperties.getNamespace(), configProperties.getConfigId()), listener);
    }

    class Listener implements ConfigChangedListener {
        @Override
        public void onChange(NamespacedConfigId namespacedConfigId, String op) {
            if (log.isInfoEnabled()) {
                log.info("Refresh - Govern-Service - configId:[{}] - [{}]", configProperties.getConfigId(), op);
            }
            applicationContext.publishEvent(
                    new RefreshEvent(this, op, "Refresh Govern-Service config"));
        }
    }
}
