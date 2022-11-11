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

import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import me.ahoo.cosky.test.AbstractReactiveRedisTest;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisServiceRegistryBenchmark extends AbstractReactiveRedisTest {
    public ServiceRegistry serviceRegistry;
    private final static ServiceInstance testInstance = TestServiceInstance.randomInstance();
    
    @Setup
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        RegistryProperties registryProperties = new RegistryProperties();
        serviceRegistry = new RedisServiceRegistry(registryProperties, redisTemplate);
        serviceRegistry.register(TestData.NAMESPACE, testInstance).block();
    }
    
    @Override
    protected boolean getEnableShare() {
        return true;
    }
    
    @TearDown
    public void destroy() {
        super.destroy();
    }
    
    @Benchmark
    public Boolean register() {
        return serviceRegistry
            .register(TestData.NAMESPACE, testInstance)
            .block();
    }
    
    @Benchmark
    public Boolean deregister() {
        return serviceRegistry
            .deregister(TestData.NAMESPACE, testInstance)
            .block();
    }
    
    @Benchmark
    public Boolean renew() {
        return serviceRegistry
            .renew(TestData.NAMESPACE, testInstance)
            .block();
    }
    
}
