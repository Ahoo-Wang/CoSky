package me.ahoo.cosky.core.redis;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.core.util.RedisScripts;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisScriptInitializer implements Closeable {

    private final AbstractRedisClient redisClient;
    private final RedisStateListener redisStateListener;

    public RedisScriptInitializer(AbstractRedisClient redisClient) {
        this.redisClient = redisClient;
        this.redisStateListener = new RedisStateListener();
        redisClient.addListener(redisStateListener);
    }

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
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
