/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import me.ahoo.cosky.core.listener.MessageListenable;
import me.ahoo.cosky.core.listener.RedisMessageListenable;
import me.ahoo.cosky.discovery.redis.ConsistencyRedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceDiscovery;
import me.ahoo.cosky.discovery.redis.RedisServiceRegistry;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author ahoo wang
 */
@State(Scope.Benchmark)
public class ConsistencyRedisServiceDiscoveryBenchmark {
    private final static String namespace = "benchmark_csy_svc_dvy";
    public ServiceDiscovery serviceDiscovery;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> redisConnection;
    private MessageListenable messageListenable;

    @Setup
    public void setup() {
        System.out.println("\n ----- ConsistencyRedisServiceDiscoveryBenchmark setup ----- \n");
        redisClient = RedisClient.create("redis://localhost:6379");
        redisConnection = redisClient.connect();

        RegistryProperties registryProperties = new RegistryProperties();
        RedisServiceRegistry serviceRegistry = new RedisServiceRegistry(registryProperties, redisConnection.async());
        serviceRegistry.register(TestServiceInstance.TEST_FIXED_INSTANCE);
        RedisServiceDiscovery redisServiceDiscovery = new RedisServiceDiscovery(redisConnection.async());
        messageListenable = new RedisMessageListenable(redisClient.connectPubSub());
        serviceDiscovery = new ConsistencyRedisServiceDiscovery(redisServiceDiscovery, messageListenable);
    }

    @TearDown
    public void tearDown() {
        System.out.println("\n ----- ConsistencyRedisServiceDiscoveryBenchmark tearDown ----- \n");
        if (Objects.nonNull(redisConnection)) {
            redisConnection.close();
        }
        if (Objects.nonNull(redisClient)) {
            redisClient.shutdown();
        }
        if (Objects.nonNull(messageListenable)) {
            try {
                messageListenable.close();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    @Benchmark
    public Set<String> getServices() {
        return serviceDiscovery.getServices(namespace).join();
    }

    @Benchmark
    public List<ServiceInstance> getInstances() {
        return serviceDiscovery.getInstances(namespace, TestServiceInstance.TEST_FIXED_INSTANCE.getServiceId()).join();
    }
}
