/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.config.spring.cloud;

import me.ahoo.cosky.config.spring.cloud.refresh.CoskyConfigRefresher;
import me.ahoo.cosky.config.ConfigListenable;
import me.ahoo.cosky.spring.cloud.CoskyProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author ahoo wang
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnCoskyConfigEnabled
public class CoskyConfigAutoConfiguration {

    public CoskyConfigAutoConfiguration() {
    }

    @Bean
    @ConditionalOnMissingBean
    public CoskyConfigRefresher coskyConfigRefresher(CoskyProperties coskyProperties,
                                                      CoskyConfigProperties configProperties,
                                                      ConfigListenable configListenable) {
        return new CoskyConfigRefresher(coskyProperties, configProperties, configListenable);
    }
}
