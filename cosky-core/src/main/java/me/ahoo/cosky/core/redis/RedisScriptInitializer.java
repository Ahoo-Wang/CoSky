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
