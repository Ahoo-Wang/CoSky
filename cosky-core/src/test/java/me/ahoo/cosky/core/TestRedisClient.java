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

package me.ahoo.cosky.core;

import io.lettuce.core.RedisClient;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;

/**
 * @author ahoo wang
 */
public final class TestRedisClient {

    public static RedisClient createClient() {
        return RedisClient.create("redis://localhost:6379");
    }

    public static RedisClusterCommands<String, String> createRedisClusterCommands(RedisClient redisClient) {
        return redisClient.connect().sync();
    }
}
