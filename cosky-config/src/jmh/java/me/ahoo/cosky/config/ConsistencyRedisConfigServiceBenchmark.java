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
import me.ahoo.cosky.config.redis.ConsistencyRedisConfigService;
import me.ahoo.cosky.config.redis.RedisConfigService;
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
public class ConsistencyRedisConfigServiceBenchmark extends AbstractReactiveRedisTest {
    public ConfigService configService;
    private static final String CONFIG_ID = MockIdGenerator.INSTANCE.generateAsString();
    
    @Setup
    public void afterPropertiesSet() {
        super.afterPropertiesSet();
        RedisConfigService redisConfigService = new RedisConfigService(redisTemplate);
        redisConfigService.setConfig(TestData.NAMESPACE, CONFIG_ID, TestData.DATA).block();
        configService = new ConsistencyRedisConfigService(redisConfigService, listenerContainer);
    }
    
    @Override
    protected boolean enableShare() {
        return true;
    }
    
    @TearDown
    public void destroy() {
        super.destroy();
    }
    
    @Benchmark
    public Config getConfig() {
        return configService.getConfig(TestData.NAMESPACE, CONFIG_ID).block();
    }
}
