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
import me.ahoo.cosky.core.test.AbstractReactiveRedisTest;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisConfigServiceBenchmark extends AbstractReactiveRedisTest {
    private final static String namespace = "ben_cfg";
    public RedisConfigService configService;
    private final String configId = MockIdGenerator.INSTANCE.generateAsString();
    private static final String configData = "spring:\n" +
        "  application:\n" +
        "    name: cosky-rest-api\n" +
        "  cloud:\n" +
        "    cosky:\n" +
        "      namespace: dev\n" +
        "      config:\n" +
        "        config-id: ${spring.application.name}.yml\n" +
        "      redis:\n" +
        "        mode: standalone\n" +
        "        url: redis://localhost:6379\n";
    private AtomicInteger atomicInteger;
    
    @Setup
    public void afterPropertiesSet() {
        System.out.println("\n ----- RedisConfigBenchmark setup ----- \n");
        super.afterPropertiesSet();
        configService = new RedisConfigService(redisTemplate);
        configService.setConfig(namespace, configId, configData).block();
        atomicInteger = new AtomicInteger();
    }
    
    @Override
    protected void customizeConnectionFactory(LettuceConnectionFactory connectionFactory) {
    
    }
    
    @TearDown
    public void destroy() {
        System.out.println("\n ----- RedisConfigBenchmark tearDown ----- \n");
        super.destroy();
    }
    
    @Benchmark
    public Boolean setConfig() {
        String randomConfigId = String.valueOf(atomicInteger.incrementAndGet());
        return configService.setConfig(namespace, randomConfigId, configData).block();
    }
    
    @Benchmark
    public Config getConfig() {
        return configService.getConfig(namespace, configId).block();
    }
    
}
