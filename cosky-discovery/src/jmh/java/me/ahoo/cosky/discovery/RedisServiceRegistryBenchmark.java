/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package me.ahoo.cosky.discovery;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import org.openjdk.jmh.annotations.*;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class RedisServiceRegistryBenchmark {
    private final static String namespace = "benchmark_svc";
    public ServiceRegistry serviceRegistry;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;

    @Setup
    public void setup() {
        System.out.println("\n ----- RedisServiceRegistryBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();
        RegistryProperties registryProperties = new RegistryProperties();

        serviceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        serviceRegistry.register(TestServiceInstance.TEST_FIXED_INSTANCE);
    }

    @TearDown
    public void tearDown() {
        System.out.println("\n ----- RedisServiceRegistryBenchmark tearDown ----- \n");
        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
    }

    @Benchmark
    public Boolean register() {
        return serviceRegistry.register(namespace, TestServiceInstance.TEST_INSTANCE).join();
    }

    @Benchmark
    public Boolean deregister() {
        return serviceRegistry.deregister(namespace, TestServiceInstance.TEST_INSTANCE).join();
    }

    @Benchmark
    public Boolean renew() {
        return serviceRegistry.renew(namespace, TestServiceInstance.TEST_INSTANCE).join();
    }

}
