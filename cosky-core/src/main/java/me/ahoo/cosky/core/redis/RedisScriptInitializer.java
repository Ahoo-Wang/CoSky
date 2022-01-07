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

package me.ahoo.cosky.core.redis;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisScriptInitializer implements AutoCloseable {

    private final AbstractRedisClient redisClient;
    private final RedisStateListener redisStateListener;

    public RedisScriptInitializer(RedisConnectionFactory redisConnectionFactory) {
        this.redisClient = redisConnectionFactory.getClient();
        this.redisStateListener = new RedisStateListener();
        redisClient.addListener(redisStateListener);
    }

    @Override
    public void close() throws IOException {
        if (log.isInfoEnabled()){
            log.info("close - removeListener.");
        }
        this.redisClient.removeListener(redisStateListener);
    }

    private static class RedisStateListener implements RedisConnectionStateListener {

        /**
         * Event handler for disconnection event.
         *
         * @param connection Source connection.
         */
        @Override
        public void onRedisDisconnected(RedisChannelHandler<?, ?> connection) {

            if (connection instanceof StatefulRedisPubSubConnection) {
                return;
            }

            if (log.isWarnEnabled()) {
                log.warn("onRedisDisconnected - [{}] - RedisScripts.clearScript.", connection);
            }
            RedisScripts.clearScript();
        }

        /**
         * Event handler for exceptions.
         *
         * @param connection Source connection.
         * @param cause      Caught exception.
         */
        @Override
        public void onRedisExceptionCaught(RedisChannelHandler<?, ?> connection, Throwable cause) {
            log.error("onRedisExceptionCaught.", cause);
        }
    }
}
