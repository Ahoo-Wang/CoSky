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

import java.util.concurrent.CompletableFuture;

/**
 * 实例负载上报器.
 * TODO 实时上报服务实例负载情况，用于动态负载均衡。{@link me.ahoo.cosky.discovery.loadbalancer.LoadBalancer}
 *
 * @author ahoo wang
 */
public interface LoadReporter {
    /**
     * 上报实例负载报告.
     *
     * @param report report
     * @return CompletableFuture
     */
    CompletableFuture<Void> report(Report report);
    
    public class Report {
    
    }
}
