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

package me.ahoo.cosky.config;

import me.ahoo.cosky.core.NamespacedContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Config Rollback.
 *
 * @author ahoo wang
 */
public interface ConfigRollback {
    int HISTORY_SIZE = 10;
    
    default Mono<Boolean> rollback(String configId, int targetVersion) {
        return rollback(NamespacedContext.GLOBAL.getRequiredNamespace(), configId, targetVersion);
    }
    
    Mono<Boolean> rollback(String namespace, String configId, int targetVersion);
    
    default Flux<ConfigVersion> getConfigVersions(String configId) {
        return getConfigVersions(NamespacedContext.GLOBAL.getRequiredNamespace(), configId);
    }
    
    Flux<ConfigVersion> getConfigVersions(String namespace, String configId);
    
    default Mono<ConfigHistory> getConfigHistory(String configId, int version) {
        return getConfigHistory(NamespacedContext.GLOBAL.getRequiredNamespace(), configId, version);
    }
    
    Mono<ConfigHistory> getConfigHistory(String namespace, String configId, int version);
}
