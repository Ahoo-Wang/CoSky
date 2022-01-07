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

package me.ahoo.cosky.discovery;

import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author ahoo wang
 */
public interface ServiceStatistic {

    Mono<Void> statService(String namespace);

    Mono<Void> statService(String namespace, @Nullable String serviceId);

    Mono<List<ServiceStat>> getServiceStats(String namespace);

    Mono<Long> getInstanceCount(String namespace);

    Mono<Map<String, Set<String>>> getTopology(String namespace);
}
