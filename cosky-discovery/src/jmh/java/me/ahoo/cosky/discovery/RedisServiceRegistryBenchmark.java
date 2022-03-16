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
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisServiceRegistryBenchmark extends AbstractReactiveRedisTest {
    private final static String namespace = "benchmark_svc";
    public ServiceRegistry serviceRegistry;
    private final static ServiceInstance testInstance = TestServiceInstance.randomInstance();
    
    @Setup
    public void afterPropertiesSet() {
        System.out.println("\n ----- RedisServiceRegistryBenchmark afterPropertiesSet ----- \n");
        super.afterPropertiesSet();
        RegistryProperties registryProperties = new RegistryProperties();
        
        serviceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
        serviceRegistry.register(testInstance).block();
    }
    
    @Override
    protected void customizeConnectionFactory(LettuceConnectionFactory connectionFactory) {
    
    }
    
    @TearDown
    public void destroy() {
        System.out.println("\n ----- RedisServiceRegistryBenchmark destroy ----- \n");
        super.destroy();
    }
    
    @Benchmark
    public Boolean register() {
        return serviceRegistry
            .register(namespace, testInstance)
            .block();
    }
    
    @Benchmark
    public Boolean deregister() {
        return serviceRegistry
            .deregister(namespace, testInstance)
            .block();
    }
    
    @Benchmark
    public Boolean renew() {
        return serviceRegistry
            .renew(namespace, testInstance)
            .block();
    }
    
}
