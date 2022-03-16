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

import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.List;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class ConsistencyRedisServiceDiscoveryBenchmark extends AbstractReactiveRedisTest {
    private final static String namespace = "benchmark_csy_svc_dvy";
    public ServiceDiscovery serviceDiscovery;
    private final static ServiceInstance fixedInstance = TestServiceInstance.randomFixedInstance();
    
    @Setup
    public void afterPropertiesSet() {
        System.out.println("\n ----- ConsistencyRedisServiceDiscoveryBenchmark afterPropertiesSet ----- \n");
        super.afterPropertiesSet();
        
        RegistryProperties registryProperties = new RegistryProperties();
        RedisServiceRegistry serviceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
        serviceRegistry.register(fixedInstance).block();
        RedisServiceDiscovery redisServiceDiscovery = new RedisServiceDiscovery(redisTemplate);
        serviceDiscovery = new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, redisTemplate, listenerContainer);
    }
    
    @Override
    protected void customizeConnectionFactory(LettuceConnectionFactory connectionFactory) {
    
    }
    
    @TearDown
    public void destroy() {
        System.out.println("\n ----- ConsistencyRedisServiceDiscoveryBenchmark destroy ----- \n");
        super.destroy();
    }
    
    @Benchmark
    public List<String> getServices() {
        return serviceDiscovery
            .getServices(namespace)
            .collectList()
            .block();
    }
    
    @Benchmark
    public List<ServiceInstance> getInstances() {
        return serviceDiscovery
            .getInstances(namespace, fixedInstance.getServiceId())
            .collectList()
            .block();
    }
}
