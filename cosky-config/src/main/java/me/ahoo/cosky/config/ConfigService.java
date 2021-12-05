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

package me.ahoo.cosky.config;

import me.ahoo.cosky.core.NamespacedContext;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * @author ahoo wang
 */
public interface ConfigService extends ConfigRollback {

    default Mono<Set<String>> getConfigs() {
        return getConfigs(NamespacedContext.GLOBAL.getRequiredNamespace());
    }

    Mono<Set<String>> getConfigs(String namespace);

    default Mono<Config> getConfig(String configId) {
        return getConfig(NamespacedContext.GLOBAL.getRequiredNamespace(), configId);
    }

    Mono<Config> getConfig(String namespace, String configId);

    default Mono<Boolean> setConfig(String configId, String data) {
        return setConfig(NamespacedContext.GLOBAL.getRequiredNamespace(), configId, data);
    }

    Mono<Boolean> setConfig(String namespace, String configId, String data);

    default Mono<Boolean> removeConfig(String configId) {
        return removeConfig(NamespacedContext.GLOBAL.getRequiredNamespace(), configId);
    }

    Mono<Boolean> removeConfig(String namespace, String configId);


    Mono<Boolean> containsConfig(String namespace, String configId);
}
