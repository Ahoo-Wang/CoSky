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

package me.ahoo.cosky.config.spring.cloud;

import me.ahoo.cosky.config.Config;
import me.ahoo.cosky.config.ConfigService;
import me.ahoo.cosky.core.CoSky;
import me.ahoo.cosky.core.NamespacedContext;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cosky Property Source Locator.
 *
 * @author ahoo wang
 */
@Slf4j
public class CoskyPropertySourceLocator implements PropertySourceLocator {
    private final List<PropertySourceLoader> propertySourceLoaders;
    private final ConfigService configService;
    private final CoskyConfigProperties configProperties;
    
    public CoskyPropertySourceLocator(CoskyConfigProperties configProperties, ConfigService configService) {
        this.configService = configService;
        this.configProperties = configProperties;
        propertySourceLoaders = SpringFactoriesLoader
            .loadFactories(PropertySourceLoader.class, CoskyPropertySourceLocator.class.getClassLoader());
    }
    
    @Override
    public PropertySource<?> locate(Environment environment) {
        String configId = configProperties.getConfigId();
        
        String fileExt = Files.getFileExtension(configId);
        if (Strings.isBlank(fileExt)) {
            fileExt = configProperties.getFileExtension();
        }
        String namespace = NamespacedContext.GLOBAL.getNamespace();
        
        log.info("locate - configId:[{}] @ namespace:[{}]", configId, namespace);
        
        Config config = configService.getConfig(configId).block(configProperties.getTimeout());
        
        if (Objects.isNull(config)) {
            log.warn("locate - can not find configId:[{}] @ namespace:[{}]", configId, namespace);
            return new OriginTrackedMapPropertySource(getNameOfConfigId(configId), Collections.emptyMap());
        }
        
        PropertySourceLoader sourceLoader = ensureSourceLoader(fileExt);
        OriginTrackedMapPropertySource coskyPropertySource = getCoSkyPropertySourceOfConfig(sourceLoader, config);
        return coskyPropertySource;
    }
    
    public PropertySourceLoader ensureSourceLoader(String fileExtension) {
        Optional<PropertySourceLoader> sourceLoaderOptional = propertySourceLoaders
            .stream()
            .filter(propertySourceLoader ->
                Arrays.stream(propertySourceLoader.getFileExtensions())
                    .anyMatch(fileExt -> fileExt.equals(fileExtension)))
            .findFirst();
        if (!sourceLoaderOptional.isPresent()) {
            throw new IllegalArgumentException(String.format("can not find fileExtension:[%s] PropertySourceLoader.", fileExtension));
        }
        return sourceLoaderOptional.get();
    }
    
    @SneakyThrows
    public OriginTrackedMapPropertySource getCoSkyPropertySourceOfConfig(PropertySourceLoader sourceLoader, Config config) {
        ByteArrayResource byteArrayResource = new ByteArrayResource(config.getData().getBytes(Charsets.UTF_8));
        List<PropertySource<?>> propertySourceList = sourceLoader.load(config.getConfigId(), byteArrayResource);
        Map<String, Object> source = getMapSource(config.getConfigId(), propertySourceList);
        return new OriginTrackedMapPropertySource(getNameOfConfigId(config.getConfigId()), source);
    }
    
    private Map<String, Object> getMapSource(String configId, List<PropertySource<?>> propertySourceList) {
        if (CollectionUtils.isEmpty(propertySourceList)) {
            return Collections.emptyMap();
        }
        
        if (propertySourceList.size() == 1) {
            PropertySource propertySource = propertySourceList.get(0);
            if (propertySource != null && propertySource.getSource() instanceof Map) {
                return (Map<String, Object>) propertySource.getSource();
            }
        }
        return Collections.singletonMap(
            getNameOfConfigId(configId),
            propertySourceList);
    }
    
    
    public static String getNameOfConfigId(String configId) {
        return CoSky.COSKY + ":" + configId;
    }
}
