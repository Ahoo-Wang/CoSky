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

package me.ahoo.cosky.config.spring.cloud.refresh;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.config.spring.cloud.CoskyConfigProperties;
import me.ahoo.cosky.config.ConfigChangedListener;
import me.ahoo.cosky.config.ConfigListenable;
import me.ahoo.cosky.config.NamespacedConfigId;
import me.ahoo.cosky.spring.cloud.CoskyProperties;
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
public class CoskyConfigRefresher implements ApplicationListener<ApplicationReadyEvent>, ApplicationContextAware {
    private ApplicationContext applicationContext;
    private final ConfigListenable configListenable;
    private final CoskyProperties coskyProperties;
    private final CoskyConfigProperties configProperties;
    private final Listener listener;

    public CoskyConfigRefresher(
            CoskyProperties coskyProperties,
            CoskyConfigProperties configProperties,
            ConfigListenable configListenable) {
        this.configListenable = configListenable;
        this.coskyProperties = coskyProperties;
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
        configListenable.addListener(NamespacedConfigId.of(coskyProperties.getNamespace(), configProperties.getConfigId()), listener);
    }

    class Listener implements ConfigChangedListener {
        @Override
        public void onChange(NamespacedConfigId namespacedConfigId, String op) {
            if (log.isInfoEnabled()) {
                log.info("Refresh - CoSky - configId:[{}] - [{}]", configProperties.getConfigId(), op);
            }
            applicationContext.publishEvent(
                    new RefreshEvent(this, op, "Refresh CoSky config"));
        }
    }
}
