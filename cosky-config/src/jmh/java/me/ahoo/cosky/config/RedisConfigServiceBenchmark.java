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

import me.ahoo.cosid.test.MockIdGenerator;
import me.ahoo.cosky.config.redis.RedisConfigService;
import me.ahoo.cosky.test.AbstractReactiveRedisTest;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisConfigServiceBenchmark extends AbstractReactiveRedisTest {
    public RedisConfigService configService;
    private static final String CONFIG_ID = MockIdGenerator.INSTANCE.generateAsString();
    private AtomicInteger atomicInteger;
    
    @Setup
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        configService = new RedisConfigService(redisTemplate);
        configService.setConfig(TestData.NAMESPACE, CONFIG_ID, TestData.DATA).block();
        atomicInteger = new AtomicInteger();
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
    public Boolean setConfig() {
        String randomConfigId = String.valueOf(atomicInteger.incrementAndGet());
        return configService.setConfig(TestData.NAMESPACE, randomConfigId, TestData.DATA).block();
    }
    
    @Benchmark
    public Config getConfig() {
        return configService.getConfig(TestData.NAMESPACE, CONFIG_ID).block();
    }
    
}
