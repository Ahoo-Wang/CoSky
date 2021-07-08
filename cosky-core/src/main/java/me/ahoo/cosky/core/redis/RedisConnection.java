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

package me.ahoo.cosky.core.redis;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;


/**
 * @author ahoo wang
 */
public class RedisConnection implements AutoCloseable {
    private StatefulConnection<String, String> connection;
    private RedisClusterAsyncCommands<String, String> asyncCommands;

    public RedisConnection(StatefulConnection<String, String> connection, RedisClusterAsyncCommands<String, String> asyncCommands) {
        this.connection = connection;
        this.asyncCommands = asyncCommands;
    }

    public StatefulConnection<String, String> getConnection() {
        return connection;
    }

    public RedisClusterAsyncCommands<String, String> getAsyncCommands() {
        return asyncCommands;
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
